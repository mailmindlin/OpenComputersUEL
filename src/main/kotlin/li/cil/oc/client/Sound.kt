package li.cil.oc.client

import java.net.MalformedURLException
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import java.util.PriorityQueue

import com.google.common.base.Charsets
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import net.minecraft.client.Minecraft
import net.minecraft.client.audio.SoundManager
import net.minecraft.server.integrated.IntegratedServer
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.ResourceLocation
import net.minecraft.util.SoundCategory
import net.minecraftforge.client.event.sound.SoundLoadEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.client.FMLClientHandler
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import paulscode.sound.SoundSystemConfig

object Sound {
  private val sources = mutableMapOf<TileEntity, PseudoLoopingStream>()

  private val commandQueue = PriorityQueue<Command>()

  private var lastVolume = FMLClientHandler.instance().client.gameSettings.getSoundLevel(SoundCategory.BLOCKS)

  private val updateTimer = Timer("OpenComputers-SoundUpdater", true)

  init {
    if (Settings.get.soundVolume > 0) {
      updateTimer.scheduleAtFixedRate(object : TimerTask() {
        override fun run() {
          synchronized(sources) {
            updateCallable = {
              updateVolume()
              processQueue()
            }
          }
        }
      }, 500, 50)
    }
  }

  private var updateCallable: (() -> Unit)? = null

  // Set in init event.
  var manager: SoundManager? = null

  val soundSystem get() = manager?.sndSystem

  private fun updateVolume() {
    val volume =
      if (isGamePaused) 0f
      else FMLClientHandler.instance().client.gameSettings.getSoundLevel(SoundCategory.BLOCKS)
    if (volume != lastVolume) {
      lastVolume = volume
      synchronized(sources) {
        for (sound in sources.values) {
          sound.updateVolume()
        }
      }
    }
  }

  private val isGamePaused: Boolean
    get() {
      val server = FMLCommonHandler.instance().minecraftServerInstance
      // Check outside of match to avoid client side class access.
      return server != null && !server.isDedicatedServer && (server is IntegratedServer && Minecraft.getMinecraft().isGamePaused)
    }

  private fun processQueue() {
    if (commandQueue.isNotEmpty()) {
      synchronized(commandQueue) {
        while (commandQueue.isNotEmpty() && commandQueue.peek().`when` < System.currentTimeMillis()) {
          try {
            commandQueue.poll()()
          } catch (t: Throwable) {
            OpenComputers.log.warn("Error processing sound command.", t)
          }
        }
      }
    }
  }

  fun startLoop(tileEntity: TileEntity, name: String, volume: Float = 1f, delay: Long = 0) {
    if (Settings.get.soundVolume > 0) {
      synchronized(commandQueue) {
        commandQueue += StartCommand(System.currentTimeMillis() + delay, tileEntity, name, volume)
      }
    }
  }

  fun stopLoop(tileEntity: TileEntity) {
    if (Settings.get.soundVolume > 0) {
      synchronized(commandQueue) {
        commandQueue += StopCommand(tileEntity)
      }
    }
  }

  fun updatePosition(tileEntity: TileEntity) {
    if (Settings.get.soundVolume > 0) {
      synchronized(commandQueue) {
        commandQueue += UpdatePositionCommand(tileEntity)
      }
    }
  }

  @SubscribeEvent
  @Suppress("unused")
  fun onSoundLoad(event: SoundLoadEvent) {
    manager = event.manager
  }

  private var hasPreloaded = Settings.get.soundVolume <= 0

  @SubscribeEvent
  @Suppress("unused")
  fun onTick(e: ClientTickEvent) {
    if (soundSystem != null) {
      if (!hasPreloaded) {
        hasPreloaded = true
        Thread(Runnable {
          val preloadConfigLocation = ResourceLocation(Settings.resourceDomain, "sounds/preload.cfg")
          val preloadConfigResource = Minecraft.getMinecraft().resourceManager.getResource(preloadConfigLocation)
          preloadConfigResource.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.forEachLine { location ->
              val url = javaClass.classLoader.getResource(location)
              if (url != null) {
                try {
                  val sourceName = "preload_$location"
                  soundSystem!!.newSource(false, sourceName, url, location, true, 0f, 0f, 0f, SoundSystemConfig.ATTENUATION_NONE, 16f)
                  soundSystem!!.activate(sourceName)
                  soundSystem!!.removeSource(sourceName)
                } catch (_: Throwable) {
                  // Meh.
                }
              } else {
                OpenComputers.log.warn("Couldn't preload sound $location!")
              }
            }
          }
        }).start()
      }

      synchronized(sources) {
        updateCallable?.invoke()
        updateCallable = null
      }
    }
  }

  @SubscribeEvent
  @Suppress("unused")
  fun onWorldUnload(event: WorldEvent.Unload) {
    synchronized(commandQueue) { commandQueue.clear() }
    synchronized(sources) {
      try {
        sources.forEach { it.value.stop() }
      } catch (_: Throwable) {
        // Ignore.
      }
    }
    sources.clear()
  }

  private abstract class Command(val `when`: Long, val tileEntity: TileEntity) : Comparable<Command> {
    abstract operator fun invoke()

    override fun compareTo(other: Command): Int = (other.`when` - `when`).toInt()
  }

  private class StartCommand(
    `when`: Long,
    tileEntity: TileEntity,
    val name: String,
    val volume: Float
  ) : Command(`when`, tileEntity) {
    override fun invoke() {
      synchronized(sources) {
        sources.getOrPut(tileEntity) { PseudoLoopingStream(tileEntity, volume) }.play(name)
      }
    }
  }

  private class StopCommand(tileEntity: TileEntity) : Command(System.currentTimeMillis() + 1, tileEntity) {
    override fun invoke() {
      synchronized(sources) {
        sources.remove(tileEntity)?.stop()
      }
      synchronized(commandQueue) {
        // Remove all other commands for this tile entity from the queue. This
        // is inefficient, but we generally don't expect the command queue to
        // be very long, so this should be OK.
        val filtered = commandQueue.filter { it.tileEntity != tileEntity }
        commandQueue.clear()
        commandQueue.addAll(filtered)
      }
    }
  }

  private class UpdatePositionCommand(tileEntity: TileEntity) : Command(System.currentTimeMillis(), tileEntity) {
    override fun invoke() {
      synchronized(sources) {
        sources[tileEntity]?.updatePosition()
      }
    }
  }

  private class PseudoLoopingStream(
    val tileEntity: TileEntity,
    val volume: Float,
    val source: String = UUID.randomUUID().toString()
  ) {
    var initialized = false

    fun updateVolume() {
      soundSystem!!.setVolume(source, lastVolume * volume * Settings.get.soundVolume)
    }

    fun updatePosition() {
      if (tileEntity != null) soundSystem!!.setPosition(source, tileEntity.pos.x.toFloat(), tileEntity.pos.y.toFloat(), tileEntity.pos.z.toFloat())
      else soundSystem!!.setPosition(source, 0f, 0f, 0f)
    }

    fun play(name: String) {
      val resourceName = "${Settings.resourceDomain}:$name"
      val sound = manager!!.sndHandler.getAccessor(ResourceLocation(resourceName))
      // Specified return type because apparently this is ambiguous according to Jenkins. I don't even.
      val resource: net.minecraft.client.audio.Sound = sound.cloneEntry()
      val soundLocation = resource.soundAsOggLocation
      if (!initialized) {
        initialized = true
        if (tileEntity != null) soundSystem!!.newSource(false, source, toUrl(soundLocation), soundLocation.toString(), true, tileEntity.pos.x.toFloat(), tileEntity.pos.y.toFloat(), tileEntity.pos.z.toFloat(), SoundSystemConfig.ATTENUATION_LINEAR, 16f)
        else soundSystem!!.newSource(false, source, toUrl(soundLocation), soundLocation.toString(), false, 0f, 0f, 0f, SoundSystemConfig.ATTENUATION_NONE, 0f)
        updateVolume()
        soundSystem!!.activate(source)
      }
      soundSystem!!.play(source)
    }

    fun stop() {
      if (soundSystem != null) {
        try {
          soundSystem!!.stop(source)
          soundSystem!!.removeSource(source)
        } catch (_: Throwable) {
        }
      }
    }
  }

  // This is copied from SoundManager.getURLForSoundResource, which is private.
  private fun toUrl(resource: ResourceLocation): URL? {
    val name = "mcsounddomain:${resource.namespace}:${resource.path}"
    return try {
      URL(null, name, object : URLStreamHandler() {
        override fun openConnection(url: URL): URLConnection = object : URLConnection(url) {
          override fun connect() {
          }

          override fun getInputStream() = try {
            Minecraft.getMinecraft().resourceManager.getResource(resource).inputStream
          } catch (t: Throwable) {
            OpenComputers.log.warn(t)
            null
          }
        }
      })
    } catch (_: MalformedURLException) {
      null
    }
  }
}
