package li.cil.oc.server.machine.luac

import com.google.common.base.Strings
import com.google.common.io.PatternFilenameFilter
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.api.driver.item.MutableProcessor
import li.cil.oc.api.machine.Machine
import li.cil.repack.com.naef.jnlua.*
import lua
import net.minecraft.item.ItemStack
import org.apache.commons.lang3.SystemUtils
import scala.util.Random
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.Channels
import java.util.regex.Pattern
import kotlin.math.floor


/**
 * Factory singleton used to spawn new LuaState instances.
 *
 * This is realized as a singleton so that we only have to resolve shared
 * library references once during initialization and can then re-use the
 * already loaded ones.
 */
abstract class LuaStateFactory {
  companion object {
    val isAvailable: Boolean
      get() {
        // Force initialization of all.
        val lua52 = Lua52.isAvailable
        val lua53 = Lua53.isAvailable
        val lua54 = Lua54.isAvailable
        return lua52 || lua53 || lua54
      }
    val luajRequested: Boolean get() = Settings.get.forceLuaJ || Settings.get.registerLuaJArchitecture
    /** Register LuaJ */
    val includeLuaJ: Boolean get() = !isAvailable || luajRequested
    val include52: Boolean get() = Lua52.isAvailable && !Settings.get.forceLuaJ
    val include53: Boolean get() = Lua53.isAvailable && Settings.get.enableLua53 && !Settings.get.forceLuaJ
    val include54: Boolean get() = Lua54.isAvailable && Settings.get.enableLua54 && !Settings.get.forceLuaJ
    val default53: Boolean get() = include53 && Settings.get.defaultLua53
    fun setDefaultArch(stack: ItemStack): ItemStack {
      if (!default53) return stack
      val driver = Driver.driverFor(stack) as? MutableProcessor ?: return stack
      val lua53 = NativeLua53Architecture::class.java
      driver.setArchitecture(stack, lua53)
      return stack
    }
  }

  abstract val version: String

  // ----------------------------------------------------------------------- //
  // Initialization
  // ----------------------------------------------------------------------- //

  /** Set to true in initialization code below if available. */
  private var haveNativeLibrary = false

  private var currentLib = ""

  private val libraryName = run {
    val libExtension = when {
      SystemUtils.IS_OS_MAC -> ".dylib"
      SystemUtils.IS_OS_WINDOWS -> ".dll"
      else -> ".so"
    }

    val platformName = run {
      if (!Strings.isNullOrEmpty(Settings.get.forceNativeLibPlatform)) Settings.get.forceNativeLibPlatform
      else {
        val systemName =
          if (SystemUtils.IS_OS_FREE_BSD) "freebsd"
          else if (SystemUtils.IS_OS_NET_BSD) "netbsd"
          else if (SystemUtils.IS_OS_OPEN_BSD) "openbsd"
          else if (SystemUtils.IS_OS_SOLARIS) "solaris"
          else if (SystemUtils.IS_OS_LINUX) "linux"
          else if (SystemUtils.IS_OS_MAC) "darwin"
          else if (SystemUtils.IS_OS_WINDOWS) "windows"
          else "unknown"

        val archName =
          if (Architecture.IS_OS_ARM64) "aarch64"
          else if (Architecture.IS_OS_ARM) "arm"
          else if (Architecture.IS_OS_X64) "x86_64"
          else if (Architecture.IS_OS_X86) "x86"
          else "unknown"

        "$systemName-$archName"
      }
    }

    "libjnlua$version-$platformName$libExtension"
  }

  protected abstract fun create(maxMemory: Int? = null): LuaState

  protected abstract fun openLibs(state: LuaState): Unit

  // ----------------------------------------------------------------------- //

  val isAvailable: Boolean = haveNativeLibrary

  // Since we use native libraries we have to do some work. This includes
  // figuring out what we're running on, so that we can load the proper shared
  // libraries compiled for that system. It also means we have to unpack the
  // shared libraries somewhere so that we can load them, because we cannot
  // load them directly from a JAR. Lastly, we need to handle library overrides in
  // case the user wants to use custom libraries, or are not on a supported platform.
  fun init() {
    if (libraryName == null)
      return

    if (SystemUtils.IS_OS_WINDOWS && !Settings.get.alwaysTryNative) {
      if (SystemUtils.IS_OS_WINDOWS_XP) {
        OpenComputers.log.warn("Sorry, but Windows XP isn't supported. I'm afraid you'll have to use a newer Windows. I very much recommend upgrading your Windows, anyway, since Microsoft has stopped supporting Windows XP in April 2014.")
        return
      }

      if (SystemUtils.IS_OS_WINDOWS_2003) {
        OpenComputers.log.warn("Sorry, but Windows Server 2003 isn't supported. I'm afraid you'll have to use a newer Windows.")
        return
      }
    }

    var tmpLibFile: File? = null
    if (!Strings.isNullOrEmpty(Settings.get.forceNativeLibPathFirst)) {
      val libraryTest = File(Settings.get.forceNativeLibPathFirst, libraryName);
      if (libraryTest.canRead()) {
        tmpLibFile = libraryTest
        currentLib = libraryTest.absolutePath
        OpenComputers.log.info("Found forced-path filesystem library $currentLib.")
      }
      else
        OpenComputers.log.warn("forceNativeLibPathFirst is set, but $currentLib was not found there. Falling back to checking the built-in libraries.")
    }

    if (currentLib.isEmpty()) {
      val libraryUrl = Machine::class.java.getResource("/assets/${Settings.resourceDomain}/lib/$libraryName")
      if (libraryUrl == null) {
        OpenComputers.log.warn("Native library with name '$libraryName' not found.")
        return
      }

      val tmpLibName = "OpenComputersMod-${OpenComputers.Version}-$version-$libraryName"
      val tmpBasePath = if (Settings.get.nativeInTmpDir) {
        val path = System.getProperty("java.io.tmpdir")
        if (path == null) ""
        else if (path.endsWith("/") || path.endsWith("\\")) path
        else "$path/"
      }
      else "./"
      tmpLibFile = File(tmpBasePath + tmpLibName)

      // Clean up old library files when not in tmp dir.
      if (!Settings.get.nativeInTmpDir) {
        val libDir = File(tmpBasePath)
        if (libDir.isDirectory) {
          for (file in libDir.listFiles(PatternFilenameFilter("^" + Pattern.quote("OpenComputersMod-") + ".*" + Pattern.quote("-" + libraryName) + "$"))) {
            if (file.compareTo(tmpLibFile) != 0) {
              file.delete()
            }
          }
        }
      }

      // If the file, already exists, make sure it's the same we need, if it's
      // not disable use of the natives.
      if (tmpLibFile.exists()) {
        var matching = true
        try {
          val inCurrent = BufferedInputStream(libraryUrl.openStream())
          val inExisting = BufferedInputStream(FileInputStream(tmpLibFile))
          var inCurrentByte = 0
          var inExistingByte = 0
          do {
            inCurrentByte = inCurrent.read()
            inExistingByte = inExisting.read()
            if (inCurrentByte != inExistingByte) {
              matching = false
              inCurrentByte = -1
              inExistingByte = -1
            }
          }
          while (inCurrentByte != -1 && inExistingByte != -1)
          inCurrent.close()
          inExisting.close()
        } catch (e: Exception) {
          matching = false
        }

        if (!matching) {
          // Try to delete an old instance of the library, in case we have an update
          // and deleteOnExit fails (which it regularly does on Windows it seems).
          // Note that this should only ever be necessary for dev-builds, where the
          // version number didn't change (since the version number is part of the name).
          try {
            tmpLibFile.delete()
          } catch (e: Exception) {
            // Ignore.
          }
          if (tmpLibFile.exists()) {
            OpenComputers.log.warn("Could not update native library '${tmpLibFile.name}'!")
          }
        }
      }

      // Copy the file contents to the temporary file.
      try {
        Channels.newChannel(libraryUrl.openStream()).use { chin ->
          FileOutputStream(tmpLibFile).channel.use { out ->
            out.transferFrom(chin, 0, Long.MAX_VALUE)
            tmpLibFile.deleteOnExit()
            // Set file permissions more liberally for multi-user+instance servers.
            tmpLibFile.setReadable(true, false)
            tmpLibFile.setWritable(true, false)
          }
        }
      // Java (or Windows?) locks the library file when opening it, so any
      // further tries to update it while another instance is still running
      // will fail. We still want to try each time, since the files may have
      // been updated.
      // Alternatively, the file could not be opened for reading/writing.
      } catch (e: Exception) {
        // Nothing.
      }
      // Try to load the lib.
      currentLib = tmpLibFile.absolutePath
    }

    try {
      synchronized(LuaStateFactory) {
        System.load(currentLib)
        create().close()
      }
      OpenComputers.log.info("Found a compatible native library: '${tmpLibFile?.name}'.")
      haveNativeLibrary = true
    } catch (t: Exception) {
      if (Settings.get.logFullLibLoadErrors) {
        OpenComputers.log.warn("Could not load native library '${tmpLibFile?.name}'.", t)
      } else {
        OpenComputers.log.trace("Could not load native library '${tmpLibFile?.name}'.")
      }
      tmpLibFile?.delete()
    }
  }

  init {
    init()

    if (!haveNativeLibrary) {
      OpenComputers.log.warn("Unsupported platform, you won't be able to host games with persistent computers.")
    }
  }


  // ----------------------------------------------------------------------- //
  // Factory
  // ----------------------------------------------------------------------- //

  fun createState(): LuaState? {
    if (!haveNativeLibrary) return null

    try {
      val state = synchronized(LuaStateFactory) {
        System.load(currentLib)
        if (Settings.get.limitMemory) create(Int.MAX_VALUE)
        else create()
      }
      try {
        // Load all libraries.
        openLibs(state)

        if (!Settings.get.disableLocaleChanging) {
          state.openLib(LuaState.Library.OS)
          state.getField(-1, "setlocale")
          state.pushString("C")
          state.call(1, 0)
          state.pop(1)
        }

        // Prepare table for os stuff.
        state.newTable()
        state.setGlobal("os")

        // Kill compat entries.
        state.pushNil()
        state.setGlobal("unpack")
        state.pushNil()
        state.setGlobal("loadstring")
        state.getGlobal("math")
        state.pushNil()
        state.setField(-2, "log10")
        state.pop(1)
        state.getGlobal("table")
        state.pushNil()
        state.setField(-2, "maxn")
        state.pop(1)

        // Remove some other functions we don't need and are dangerous.
        state.pushNil()
        state.setGlobal("dofile")
        state.pushNil()
        state.setGlobal("loadfile")

        state.getGlobal("math")

        // We give each Lua state it's own randomizer, since otherwise they'd
        // use the good old rand() from C. Which can be terrible, and isn't
        // necessarily thread-safe.
        val random = Random()
        state.pushJavaFunction { lua ->
          val r = random.nextDouble()
          when (lua.top) {
            0 -> lua.pushNumber(r)
            1 -> {
              val u = lua.checkNumber(1)
              lua.checkArg(1, 1 <= u, "interval is empty")
              lua.pushNumber(floor(r * u) + 1)
            }
            2 -> {
              val l = lua.checkNumber(1)
              val u = lua.checkNumber(2)
              lua.checkArg(2, l <= u, "interval is empty")
              lua.pushNumber(floor(r * (u - l + 1)) + l)
            }
            else -> throw IllegalArgumentException("wrong number of arguments")
          }
          1
        }
        state.setField(-2, "random")

        state.pushJavaFunction { lua ->
          random.setSeed(lua.checkInteger(1))
          0
        }
        state.setField(-2, "randomseed")

        // Pop the math table.
        state.pop(1)

        return state
      } catch (e: Exception) {
        OpenComputers.log.warn("Failed creating Lua state.", e)
        state.close()
      }
    } catch (e: UnsatisfiedLinkError) {
      OpenComputers.log.error("Failed loading the native libraries.")
    } catch (e: Exception) {
      OpenComputers.log.warn("Failed creating Lua state.", e)
    }
    return null
  }

  // Inspired by org.apache.commons.lang3.SystemUtils
  object Architecture {
    val OS_ARCH = try { System.getProperty("os.arch") } catch (ex: SecurityException) { null }

    val IS_OS_ARM = isOSArchMatch("arm")

    val IS_OS_ARM64 = isOSArchMatch("aarch64")

    val IS_OS_X86 = isOSArchMatch("x86") || isOSArchMatch("i386")

    val IS_OS_X64 = isOSArchMatch("x86_64") || isOSArchMatch("amd64")

    private fun isOSArchMatch(archPrefix: String): Boolean = OS_ARCH != null && OS_ARCH.startsWith(archPrefix)
  }


  object Lua52: LuaStateFactory() {
    override val version: String get() = "52"

    override fun create(maxMemory: Int?) = maxMemory?.let { LuaState(it) } ?: LuaState()

    override fun openLibs(state: LuaState) {
      state.openLib(LuaState.Library.BASE)
      state.openLib(LuaState.Library.BIT32)
      state.openLib(LuaState.Library.COROUTINE)
      state.openLib(LuaState.Library.DEBUG)
      state.openLib(LuaState.Library.ERIS)
      state.openLib(LuaState.Library.MATH)
      state.openLib(LuaState.Library.STRING)
      state.openLib(LuaState.Library.TABLE)
      state.pop(8)
    }
  }

  object Lua53: LuaStateFactory() {
    override val version: String get() = "53"

    override fun create(maxMemory: Int?) = maxMemory?.let { LuaStateFiveThree(it) } ?: LuaStateFiveThree()

    override fun openLibs(state: LuaState) {
      state.openLib(LuaState.Library.BASE)
      state.openLib(LuaState.Library.COROUTINE)
      state.openLib(LuaState.Library.DEBUG)
      state.openLib(LuaState.Library.ERIS)
      state.openLib(LuaState.Library.MATH)
      state.openLib(LuaState.Library.STRING)
      state.openLib(LuaState.Library.TABLE)
      state.openLib(LuaState.Library.UTF8)
      state.pop(8)
    }
  }

  object Lua54: LuaStateFactory() {
    override val version: String get() = "54"

    override fun create(maxMemory: Int?) = maxMemory?.let { LuaStateFiveFour(it) } ?: LuaStateFiveFour()

    override fun openLibs(state: LuaState) {
      state.openLib(LuaState.Library.BASE)
      state.openLib(LuaState.Library.COROUTINE)
      state.openLib(LuaState.Library.DEBUG)
      state.openLib(LuaState.Library.ERIS)
      state.openLib(LuaState.Library.MATH)
      state.openLib(LuaState.Library.STRING)
      state.openLib(LuaState.Library.TABLE)
      state.openLib(LuaState.Library.UTF8)
      state.pop(8)
    }
  }
}