package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.internal.TextBuffer
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.machine.LimitReachedException
import li.cil.oc.api.network.*
import li.cil.oc.common.component.GpuTextBuffer
import li.cil.oc.common.component.traits.VideoRamDevice
import li.cil.oc.common.component.traits.VideoRamDevice.Companion.RESERVED_SCREEN_INDEX
import li.cil.oc.common.component.traits.VideoRamRasterizer
import li.cil.oc.server.machine.Machine
import li.cil.oc.util.PackedColor
import li.cil.oc.util.Result
import li.cil.oc.util.result
import li.cil.oc.util.unicodeLength
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// IMPORTANT: usually methods with side effects should *not* be direct
// callbacks to avoid the massive headache synchronizing them ensues, in
// particular when it comes to world saving. I'm making an exception for
// screens, though since they'd be painfully sluggish otherwise. This also
// means we have to use a somewhat nasty trick in common.component.Buffer's
// save function: we wait for all computers in the same network to finish
// their current execution and then pause them, to ensure the state of the
// buffer is "clean", meaning the computer has the correct state when it is
// saved in turn. If we didn't, a computer might change a screen after it was
// saved, but before the computer was saved, leading to mismatching states in
// the save file - a Bad Thing (TM).

open class GraphicsCard(val tier: Int): ManagedEnvironmentKt(), DeviceInfo {
  override val node = nodeFactory(Visibility.Neighbors, component = "gpu")
    .withConnector()
    .create()

  private val maxResolution = Settings.screenResolutionsByTier[tier]
  private val maxDepth = Settings.screenDepthsByTier[tier]

  private val device: GraphicsCardDevice = GraphicsCardDevice()

  inner class GraphicsCardDevice: VideoRamDevice() {
    private inline fun <R> withScreen(index: Int, f: (TextBuffer) -> R): R? {
      if (index == RESERVED_SCREEN_INDEX) {
        val screen = screenInstance ?: return null
        return synchronized(screen) { f(screen) }
      } else {
        val buffer = getBuffer(index) ?: return null
        return f(buffer)
      }
    }
    // this event occurs when the gpu is told a page was removed - we need to notify the screen of this
    // we do this because the VideoRamDevice trait only notifies itself, it doesn't assume there is a screen
    override fun onBufferRamDestroy(id: Int) {
      // first protect our buffer index - it needs to fall back to the screen if its buffer was removed
      if (id != RESERVED_SCREEN_INDEX) {
        withScreen(RESERVED_SCREEN_INDEX) { s ->
          // addon mod screen type that is not video ram aware
          if (s !is VideoRamRasterizer) return@withScreen true
          return@withScreen s.removeBuffer(node!!.address()!!, id)
        }
      }
      if (id == bufferIndex)
        bufferIndex = RESERVED_SCREEN_INDEX
    }
  }

  private var screenAddress: String? = null

  private var screenInstance: TextBuffer? = null

  private var bufferIndex: Int = RESERVED_SCREEN_INDEX // screen is index zero

  private inline fun screen(index: Int = bufferIndex, f: (TextBuffer) -> Result): Result {
    if (index == RESERVED_SCREEN_INDEX) {
      val screen = screenInstance ?: return result(Unit, "no screen")
      return synchronized(screen) { f(screen) }
    } else {
      val buffer = device.getBuffer(index) ?: return result(Unit, "invalid buffer index")
      return f(buffer)
    }
  }

  data class OperationCosts(
    val setBackground: Double,
    val setForeground: Double,
    val setPaletteColor: Double,
    val set: Double,
    val copy: Double,
    val fill: Double,
  )
  val costsByTier = arrayOf(
    OperationCosts(
      setBackground = 1.0 / 32.0,
      setForeground = 1.0 / 32.0,
      setPaletteColor = 1.0 / 2.0,
      set = 1.0 / 64.0,
      copy = 1.0 / 16.0,
      fill = 1.0 / 32.0,
    ),
    OperationCosts(
      setBackground = 1.0 / 64.0,
      setForeground = 1.0 / 64.0,
      setPaletteColor = 1.0 / 8.0,
      set = 1.0 / 128.0,
      copy = 1.0 / 32.0,
      fill = 1.0 / 64.0,
    ),
    OperationCosts(
      setBackground = 1.0 / 128.0,
      setForeground = 1.0 / 128.0,
      setPaletteColor = 1.0 / 16.0,
      set = 1.0 / 256.0,
      copy = 1.0 / 64.0,
      fill = 1.0 / 128.0,
    )
  )

  private val costs: OperationCosts = costsByTier[tier]
//  final val setBackgroundCosts = Array(1.0 / 32, 1.0 / 64, 1.0 / 128)
//  final val setForegroundCosts = Array(1.0 / 32, 1.0 / 64, 1.0 / 128)
//  final val setPaletteColorCosts = Array(1.0 / 2, 1.0 / 8, 1.0 / 16)
//  final val setCosts = Array(1.0 / 64, 1.0 / 128, 1.0 / 256)
//  final val copyCosts = Array(1.0 / 16, 1.0 / 32, 1.0 / 64)
//  final val fillCosts = Array(1.0 / 32, 1.0 / 64, 1.0 / 128)

  // These are dirty page bitblt budget costs
  // a single bitblt can send a screen of data, which is n*set calls where set is writing an entire line
  // So for each tier, we multiple the set cost with the number of lines the screen may have
  final val bitbltCost: Double = Settings.get.bitbltCost * 2.0.pow(tier)
  final val totalVRAM: Double = maxResolution.pixels * Settings.get.vramSizes[tier.coerceIn(0..2)]

  var budgetExhausted: Boolean = false // for especially expensive calls, bitblt

  // ----------------------------------------------------------------------- //

  private val deviceInfo_ by lazy {
    mapOf(
      DeviceAttribute.Class to DeviceClass.Display,
      DeviceAttribute.Description to "Graphics controller",
      DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
      DeviceAttribute.Product to ("MPG" + ((tier + 1) * 1000).toString() + " GTZ"),
      DeviceAttribute.Capacity to capacityInfo,
      DeviceAttribute.Width to widthInfo,
      DeviceAttribute.Clock to clockInfo
    )
  }

  override fun getDeviceInfo() = deviceInfo_

  protected val capacityInfo: String
    get() = maxResolution.pixels.toString()

  protected val widthInfo: String
    get() = arrayOf("1", "4", "8")[maxDepth.ordinal]

  protected val clockInfo: String
    get() = ((2000 / costs.setBackground).toInt() / 100).toString() + "/" + ((2000 / costs.setForeground).toInt() / 100).toString() + "/" + ((2000 / costs.setPaletteColor).toInt() / 100).toString() + "/" + ((2000 / costs.set).toInt() / 100).toString() + "/" + ((2000 / costs.copy).toInt() / 100).toString() + "/" + ((2000 / costs.fill).toInt() / 100).toString()

  // ----------------------------------------------------------------------- //

  private fun resolveInvokeCosts(idx: Int, context: Context, budgetCost: Double, units: Int, factor: Double): Boolean {
    if (idx != RESERVED_SCREEN_INDEX)
      return true

    context.consumeCallBudget(budgetCost)
    return consumePower(units.toDouble(), factor)
  }

  @Callback(direct = true, doc = """function(): number -- returns the index of the currently selected buffer. 0 is reserved for the screen. Can return 0 even when there is no screen""")
  fun getActiveBuffer(context: Context, args: Arguments): Result = result(bufferIndex)

  @Callback(direct = true, doc = """function(index: number): number -- Sets the active buffer to `index`. 1 is the first vram buffer and 0 is reserved for the screen. returns nil for invalid index (0 is always valid)""")
  fun setActiveBuffer(context: Context, args: Arguments): Result {
    val previousIndex: Int = bufferIndex
    val newIndex: Int = args.checkInteger(0)
    if (newIndex != RESERVED_SCREEN_INDEX && device.getBuffer(newIndex) == null)
      return result(Unit, "invalid buffer index")

    bufferIndex = newIndex
    if (bufferIndex == RESERVED_SCREEN_INDEX) {
      screen { return result(true) }
    }
    return result(previousIndex)
  }

  @Callback(direct = true, doc = """function(): number -- Returns an array of indexes of the allocated buffers""")
  fun buffers(context: Context, args: Arguments): Result = result(device.bufferIndexes())

  @Callback(direct = true, doc = """function([width: number, height: number]): number -- allocates a new buffer with dimensions width*height (defaults to max resolution) and appends it to the buffer list. Returns the index of the new buffer and returns nil with an error message on failure. A buffer can be allocated even when there is no screen bound to this gpu. Index 0 is always reserved for the screen and thus the lowest index of an allocated buffer is always 1.""")
  fun allocateBuffer(context: Context, args: Arguments): Result {
    val width: Int = args.optInteger(0, maxResolution.width)
    val height: Int = args.optInteger(1, maxResolution.height)
    val size: Int = width * height
    if (width <= 0 || height <= 0)
      return result(Unit, "invalid page dimensions: must be greater than zero")
    if (size > (totalVRAM - device.calculateUsedMemory()))
      return result(Unit, "not enough video memory")
    val node = node ?: return result(Unit, "graphics card appears disconnected")
    val format: PackedColor.ColorFormat = PackedColor.Depth.format(Settings.screenDepthsByTier[tier])
    val buffer = li.cil.oc.util.TextBufferData(width, height, format)
    val page = GpuTextBuffer.wrap(node.address()!!, device.nextAvailableBufferIndex(), buffer)
    device.addBuffer(page)
    return result(page.id)
  }

  @Callback(direct = true, doc = """function(index: number): boolean -- Closes buffer at `index`. Returns true if a buffer closed. If the current buffer is closed, index moves to 0""")
  fun freeBuffer(context: Context, args: Arguments): Result {
    val index: Int = args.optInteger(0, bufferIndex)
    if (device.removeBuffers(intArrayOf(index)) != 1)
      return result(Unit, "no buffer at index")
    return result(true)
  }

  @Callback(direct = true, doc = """function(): number -- Closes all buffers and returns the count. If the active buffer is closed, index moves to 0""")
  fun freeAllBuffers(context: Context, args: Arguments): Result = result(device.removeAllBuffers())

  @Callback(direct = true, doc = """function(): number -- returns the total memory size of the gpu vram. This does not include the screen.""")
  fun totalMemory(context: Context, args: Arguments): Result = result(totalVRAM)

  @Callback(direct = true, doc = """function(): number -- returns the total free memory not allocated to buffers. This does not include the screen.""")
  fun freeMemory(context: Context, args: Arguments): Result = result(totalVRAM - device.calculateUsedMemory())

  @Callback(direct = true, doc = """function(index: number): number, number -- returns the buffer size at index. Returns the screen resolution for index 0. returns nil for invalid indexes""")
  fun getBufferSize(context: Context, args: Arguments): Result {
    val idx = args.optInteger(0, bufferIndex)
    return screen(idx) { s -> result(s.width, s.height) }
  }

  private fun determineBitbltBudgetCost(dst: TextBuffer, src: TextBuffer): Double {
    // large dirty buffers need throttling so their budget cost is more
    // clean buffers have no budget cost.
    val page = src as? GpuTextBuffer ?: return 0.0
    // from screen is free

    // no cost to write to ram
    if (dst is GpuTextBuffer)
      return 0.0

    // bitblt a clean page to screen has a minimal cost
    if (!page.dirty)
      return 0.001
    // screen target will need the new buffer
    // small buffers are cheap, so increase with size of buffer source
    return bitbltCost * (src.width * src.height) / maxResolution.pixels
  }

  private fun determineBitbltEnergyCost(dst: TextBuffer): Double {
    // memory to memory copies are extremely energy efficient
    // rasterizing to the screen has the same cost as copy (in fact, screen-to-screen blt _is_ a copy
    return when (dst) {
      is GpuTextBuffer -> 0.0
      else -> Settings.get.gpuCopyCost / 15
    }
  }

  @Callback(direct = true, doc = """function([dst: number, col: number, row: number, width: number, height: number, src: number, fromCol: number, fromRow: number]):boolean -- bitblt from buffer to screen. All parameters are optional. Writes to `dst` page in rectangle `x, y, width, height`, defaults to the bound screen and its viewport. Reads data from `src` page at `fx, fy`, default is the active page from position 1, 1""")
  fun bitblt(context: Context, args: Arguments): Result {
    val dstIdx = args.optInteger(0, RESERVED_SCREEN_INDEX)
    return screen(dstIdx) { dst ->
      val col = args.optInteger(1, 1)
      val row = args.optInteger(2, 1)
      val w = args.optInteger(3, dst.width)
      val h = args.optInteger(4, dst.height)
      val srcIdx = args.optInteger(5, bufferIndex)
      screen(srcIdx) { src ->
        val fromCol = args.optInteger(6, 1)
        val fromRow = args.optInteger(7, 1)

        var budgetCost: Double = determineBitbltBudgetCost(dst, src)
        val energyCost: Double = determineBitbltEnergyCost(dst)
        val tierCredit: Double = ((tier + 1) * .5)
        val overBudget: Double = budgetCost - tierCredit

        if (overBudget > 0) {
          if (!budgetExhausted) { // we've thrown once before
            budgetExhausted = true
            throw LimitReachedException()
          }
          if (overBudget > tierCredit) { // we need even more pause than just a single tierCredit
            val pauseNeeded = overBudget - tierCredit
            val seconds: Double = (pauseNeeded / tierCredit) / 20
            context.pause(seconds)
          }
          budgetCost = 0.0 // remove the rest of the budget cost at this point
        }
        budgetExhausted = false

        if (!resolveInvokeCosts(dstIdx, context, budgetCost, w * h, energyCost))
          return result(Unit, "not enough energy")

        if (dstIdx == srcIdx) {
          val tx = col - fromCol
          val ty = row - fromRow
          dst.copy(fromCol - 1, fromRow - 1, w, h, tx, ty)
          result(true)
        } else {
          // at least one of the two buffers is a gpu buffer
          GpuTextBuffer.bitblt(dst, col, row, w, h, src, fromCol, fromRow)
          result(true)
        }
      }
    }
  }

  @Callback(doc = """function(address:string[, reset:boolean=true]):boolean -- Binds the GPU to the screen with the specified address and resets screen settings if `reset` is true.""")
  fun bind(context: Context, args: Arguments): Result {
    val address = args.checkString(0)
    val reset = args.optBoolean(1, true)
    val host = (node!!.network().node(address)
      ?: return result(Unit, "invalid address"))
      .host() as? TextBuffer
      ?: return result(Unit, "not a screen");

    screenAddress = address
    screenInstance = host
    return screen { s ->
      if (reset) {
        val (gmw, gmh) = maxResolution
        val smw = s.maximumWidth
        val smh = s.maximumHeight
        s.setResolution(min(gmw, smw), min(gmh, smh))
        s.setColorDepth(TextBuffer.ColorDepth.values()[min(maxDepth.ordinal, s.maximumColorDepth.ordinal)])
        s.setForegroundColor(0xFFFFFF)
        s.setBackgroundColor(0x000000)
        if (s is VideoRamRasterizer)
          s.removeAllBuffers()
      } else {
        // To discourage outputting "in realtime" to multiple screens using one GPU.
        context.pause(0.0)
      }
      result(true)
    }
  }

  @Callback(direct = true, doc = """function():string -- Get the address of the screen the GPU is currently bound to.""")
  fun getScreen(context: Context, args: Arguments): Result = screen(RESERVED_SCREEN_INDEX) { s -> result(s.node()!!.address()) }

  @Callback(direct = true, doc = """function():number, boolean -- Get the current background color and whether it's from the palette or not.""")
  fun getBackground(context: Context, args: Arguments): Result =
    screen { result(it.backgroundColor, it.isBackgroundFromPalette) }

  @Callback(direct = true, doc = """function(value:number[, palette:boolean]):number, number or nil -- Sets the background color to the specified value. Optionally takes an explicit palette index. Returns the old value and if it was from the palette its palette index.""")
  fun setBackground(context: Context, args: Arguments): Result {
    val color = args.checkInteger(0)
    if (bufferIndex == RESERVED_SCREEN_INDEX) {
      context.consumeCallBudget(costs.setBackground)
    }
    return screen { s ->
      val oldValue = s.backgroundColor
      val (oldColor, oldIndex) =
        if (s.isBackgroundFromPalette) {
          Pair(s.getPaletteColor(oldValue), oldValue)
        } else {
          Pair(oldValue, Unit)
        }
      s.setBackgroundColor(color, args.optBoolean(1, false))
      return result(oldColor, oldIndex)
    }
  }

  @Callback(direct = true, doc = """function():number, boolean -- Get the current foreground color and whether it's from the palette or not.""")
  fun getForeground(context: Context, args: Arguments): Result =
    screen { s -> result(s.foregroundColor, s.isForegroundFromPalette) }

  @Callback(direct = true, doc = """function(value:number[, palette:boolean]):number, number or nil -- Sets the foreground color to the specified value. Optionally takes an explicit palette index. Returns the old value and if it was from the palette its palette index.""")
  fun setForeground(context: Context, args: Arguments): Result {
    val color = args.checkInteger(0)
    if (bufferIndex == RESERVED_SCREEN_INDEX) {
      context.consumeCallBudget(costs.setForeground)
    }

    return screen { s ->
      val oldValue = s.foregroundColor
      val (oldColor, oldIndex) =
        if (s.isForegroundFromPalette) {
          Pair(s.getPaletteColor(oldValue), oldValue)
        } else {
          Pair(oldValue, Unit)
        }
      s.setForegroundColor(color, args.optBoolean(1, false))
      return result(oldColor, oldIndex)
    }
  }

  @Callback(direct = true, doc = """function(index:number):number -- Get the palette color at the specified palette index.""")
  fun getPaletteColor(context: Context, args: Arguments): Result {
    val index = args.checkInteger(0)
    return screen { s ->
      try {
        result(s.getPaletteColor(index))
      } catch (e: ArrayIndexOutOfBoundsException) {
        throw IllegalArgumentException("invalid palette index", e)
      }
    }
  }

  @Callback(direct = true, doc = """function(index:number, color:number):number -- Set the palette color at the specified palette index. Returns the previous value.""")
  fun setPaletteColor(context: Context, args: Arguments): Result {
    val index = args.checkInteger(0)
    val color = args.checkInteger(1)
    if (bufferIndex == RESERVED_SCREEN_INDEX) {
      context.consumeCallBudget(costs.setPaletteColor)
      context.pause(0.1)
    }

    return screen { s ->
      try {
        val oldColor = s.getPaletteColor(index)
        s.setPaletteColor(index, color)
        result(oldColor)
      } catch (e: ArrayIndexOutOfBoundsException) {
        throw IllegalArgumentException("invalid palette index", e)
      }
    }
  }

  @Callback(direct = true, doc = """function():number -- Returns the currently set color depth.""")
  fun getDepth(context: Context, args: Arguments): Result =
    screen { s -> result(PackedColor.Depth.bits(s.colorDepth)) }

  @Callback(doc = """function(depth:number):number -- Set the color depth. Returns the previous value.""")
  fun setDepth(context: Context, args: Arguments): Result {
    val depth = args.checkInteger(0)
    return screen { s ->
      val oldDepth = s.colorDepth
      val depth = when (depth) {
        1 -> TextBuffer.ColorDepth.OneBit
        4 -> TextBuffer.ColorDepth.FourBit
        8 -> TextBuffer.ColorDepth.EightBit
        else -> throw IllegalArgumentException("unsupported depth")
      }
      if (maxDepth.ordinal < depth.ordinal)
        throw IllegalArgumentException("unsupported depth")
      s.setColorDepth(depth)
      return result(oldDepth)
    }
  }

  @Callback(direct = true, doc = """function():number -- Get the maximum supported color depth.""")
  fun maxDepth(context: Context, args: Arguments): Result =
    screen { s -> result(PackedColor.Depth.bits(TextBuffer.ColorDepth.values()[min(maxDepth.ordinal, s.maximumColorDepth.ordinal)])) }

  @Callback(direct = true, doc = """function():number, number -- Get the current screen resolution.""")
  fun getResolution(context: Context, args: Arguments): Result =
    screen { s -> result(s.width, s.height) }

  @Callback(doc = """function(width:number, height:number):boolean -- Set the screen resolution. Returns true if the resolution changed.""")
  fun setResolution(context: Context, args: Arguments): Result {
    val w = args.checkInteger(0)
    val h = args.checkInteger(1)
    val (mw, mh) = maxResolution
    // Even though the buffer itself checks this again, we need this here for
    // the minimum of screen and GPU resolution.
    if (w < 1 || h < 1 || w > mw || h > mw || h * w > mw * mh)
      throw IllegalArgumentException("unsupported resolution")
    return screen { s -> result(s.setResolution(w, h)) }
  }

  @Callback(direct = true, doc = """function():number, number -- Get the maximum screen resolution.""")
  fun maxResolution(context: Context, args: Arguments): Result =
    screen { s ->
      val (gmw, gmh) = maxResolution
      val smw = s.maximumWidth
      val smh = s.maximumHeight
      return result(min(gmw, smw), min(gmh, smh))
    }

  @Callback(direct = true, doc = """function():number, number -- Get the current viewport resolution.""")
  fun getViewport(context: Context, args: Arguments): Result =
    screen { s -> result(s.viewportWidth, s.viewportHeight) }

  @Callback(doc = """function(width:number, height:number):boolean -- Set the viewport resolution. Cannot exceed the screen resolution. Returns true if the resolution changed.""")
  fun setViewport(context: Context, args: Arguments): Result {
    val w = args.checkInteger(0)
    val h = args.checkInteger(1)
    val (mw, mh) = maxResolution
    // Even though the buffer itself checks this again, we need this here for
    // the minimum of screen and GPU resolution.
    if (w < 1 || h < 1 || w > mw || h > mw || h * w > mw * mh)
      throw IllegalArgumentException("unsupported viewport size")
    return screen { s ->
      if (w > s.width || h > s.height)
        throw IllegalArgumentException("unsupported viewport size")
      result(s.setViewport(w, h))
    }
  }

  @Callback(direct = true, doc = """function(x:number, y:number):string, number, number, number or nil, number or nil -- Get the value displayed on the screen at the specified index, as well as the foreground and background color. If the foreground or background is from the palette, returns the palette indices as fourth and fifth results, else nil, respectively.""")
  fun get(context: Context, args: Arguments): Result {
    // maybe one day:
//    if (bufferIndex != RESERVED_SCREEN_INDEX && args.count() == 0) {
//      return screen {
//        case ram: GpuTextBuffer => {
//          val nbt = new NBTTagCompound
//          ram.data.save(nbt)
//          result(nbt)
//        }
//      }
//    }
    val x = args.checkInteger(0) - 1
    val y = args.checkInteger(1) - 1
    return screen { s ->
      val fgValue = s.getForegroundColor(x, y)
      val (fgColor, fgIndex) =
        if (s.isForegroundFromPalette(x, y)) {
          Pair(s.getPaletteColor(fgValue), fgValue)
        } else {
          Pair(fgValue, Unit)
        }

      val bgValue = s.getBackgroundColor(x, y)
      val (bgColor, bgIndex) =
        if (s.isBackgroundFromPalette(x, y)) {
          Pair(s.getPaletteColor(bgValue), bgValue)
        } else {
          Pair(bgValue, Unit)
        }

      return result(
        StringBuilder().appendCodePoint(s.getCodePoint(x, y)).toString(),
        fgColor, bgColor,
        fgIndex, bgIndex,
      )
    }
  }

  @Callback(direct = true, doc = """function(x:number, y:number, value:string[, vertical:boolean]):boolean -- Plots a string value to the screen at the specified position. Optionally writes the string vertically.""")
  fun set(context: Context, args: Arguments): Result {
    val x = args.checkInteger(0) - 1
    val y = args.checkInteger(1) - 1
    val value = args.checkString(2)
    val vertical = args.optBoolean(3, false)

    return screen { s ->
      if (!resolveInvokeCosts(bufferIndex, context, costs.set, value.unicodeLength, Settings.get.gpuSetCost))
        return result(Unit, "not enough energy")
      s.set(x, y, value, vertical)
      return result(true)
    }
  }

  @Callback(direct = true, doc = """function(x:number, y:number, width:number, height:number, tx:number, ty:number):boolean -- Copies a portion of the screen from the specified location with the specified size by the specified translation.""")
  fun copy(context: Context, args: Arguments): Result {
    val x = args.checkInteger(0) - 1
    val y = args.checkInteger(1) - 1
    val w = args.checkInteger(2).coerceAtLeast(0)
    val h = args.checkInteger(3).coerceAtLeast(0)
    val tx = args.checkInteger(4)
    val ty = args.checkInteger(5)
    return screen { s ->
      if (!resolveInvokeCosts(bufferIndex, context, costs.copy, w * h, Settings.get.gpuCopyCost))
        return result(Unit, "not enough energy")
      s.copy(x, y, w, h, tx, ty)
      return result(true)
    }
  }

  @Callback(direct = true, doc = """function(x:number, y:number, width:number, height:number, char:string):boolean -- Fills a portion of the screen at the specified position with the specified size with the specified character.""")
  fun fill(context: Context, args: Arguments): Result {
    val x = args.checkInteger(0) - 1
    val y = args.checkInteger(1) - 1
    val w = args.checkInteger(2).coerceAtLeast(0)
    val h = args.checkInteger(3).coerceAtLeast(0)
    val value = args.checkString(4)
    if (value.unicodeLength != 1)
      throw Exception("invalid fill value")
    return screen { s ->
      val c = value.codePointAt(0)
      val cost = if (c == ' '.code) Settings.get.gpuClearCost else Settings.get.gpuFillCost
      if (resolveInvokeCosts(bufferIndex, context, costs.fill, w * h, cost)) {
        s.fill(x, y, w, h, c)
        result(true)
      } else {
        result(Unit, "not enough energy")
      }
    }
  }

  private fun consumePower(n: Double, cost: Double) = node!!.tryChangeBuffer(-n * cost)

  // ----------------------------------------------------------------------- //

  override fun onMessage(message: Message) {
    super.onMessage(message)
    if (node!!.isNeighborOf(message.source())) {
      if (message.name() == "computer.stopped" || message.name() == "computer.started") {
        bufferIndex = RESERVED_SCREEN_INDEX
        device.removeAllBuffers()
      }
    }

    if (message.name() == "computer.stopped" && node!!.isNeighborOf(message.source())) {
      screen { s ->
        val (gmw, gmh) = maxResolution
        val smw = s.maximumWidth
        val smh = s.maximumHeight
        s.setResolution(min(gmw, smw), min(gmh, smh))
        s.setColorDepth(TextBuffer.ColorDepth.values()[min(maxDepth.ordinal, s.maximumColorDepth.ordinal)])
        s.setForegroundColor(0xFFFFFF)
        val w = s.width
        val h = s.height
        val host = message.source().host()
        val lastError = (host as? Machine)?.lastError()
        if (lastError != null) {
          if (s.colorDepth.ordinal > TextBuffer.ColorDepth.OneBit.ordinal) {
            s.setBackgroundColor(0x0000FF)
          } else {
            s.setBackgroundColor(0x000000)
          }
          s.fill(0, 0, w, h, 0x20)
          try {
            val wrapRegEx = Regex("(.{1,${max(1, w - 2)}})\\s")
            val errorText = Localization.localizeImmediately(lastError).replace("\t", "  ") + "\n"
            val wrappedText = wrapRegEx.replace(errorText) { m -> m.groupValues[1] + "\n" }
            val lines = wrappedText.lines().filter { it.isNotEmpty() }
            val firstRow = ((h - lines.size) / 2).coerceAtLeast(2)

            val errorMessage = "Unrecoverable Error"
            s.set((w - errorMessage.length) / 2, firstRow - 2, errorMessage, false)

            val maxLineLength = lines.maxOfOrNull { it.length } ?: 0
            val col = ((w - maxLineLength) / 2).coerceAtLeast(0)
            for ((idx, line) in lines.withIndex()) {
              val row = firstRow + idx
              s.set(col, row, line, false)
            }
          } catch (t: Throwable) {
            t.printStackTrace()
          }
        } else {
          s.setBackgroundColor(0x000000)
          s.fill(0, 0, w, h, 0x20)
        }
        return@screen result(Unit) // For screen()
      }
    }
  }

  override fun onConnect(node: Node): Unit {
    super.onConnect(node)
    if (screenInstance == null && screenAddress == node.address()) {
      val buffer = node.host()
      if (buffer is TextBuffer) {
        screenInstance = buffer
      } else {
        // Not the screen node we're looking for.
      }
    }
  }

  override fun onDisconnect(node: Node) {
    super.onDisconnect(node)
    if (node == this.node || screenAddress == node.address()) {
      screenAddress = null
      screenInstance = null
    }
  }

  // ----------------------------------------------------------------------- //

  private val SCREEN_KEY: String = "screen"
  private val BUFFER_INDEX_KEY: String = "bufferIndex"
  private val VIDEO_RAM_KEY: String = "videoRam"
  private final val NBT_PAGES: String = "pages"
  private final val NBT_PAGE_IDX: String = "page_idx"
  private final val NBT_PAGE_DATA: String = "page_data"
  private val COMPOUND_ID = NBTTagCompound().id

  override fun load(nbt: NBTTagCompound) {
    super.load(nbt)

    if (nbt.hasKey(SCREEN_KEY)) {
      val screen = nbt.getString(SCREEN_KEY)
      screenAddress = if (!screen.isNullOrEmpty()) screen else null
      screenInstance = null
    }

    if (nbt.hasKey(BUFFER_INDEX_KEY)) {
      bufferIndex = nbt.getInteger(BUFFER_INDEX_KEY)
    }

    device.removeAllBuffers() // JUST in case
    if (nbt.hasKey(VIDEO_RAM_KEY)) {
      val videoRamNbt = nbt.getCompoundTag(VIDEO_RAM_KEY)
      val nbtPages = videoRamNbt.getTagList(NBT_PAGES, COMPOUND_ID.toInt())
      for (i in 0 until nbtPages.tagCount()) {
        val nbtPage = nbtPages.getCompoundTagAt(i)
        val idx: Int = nbtPage.getInteger(NBT_PAGE_IDX)
        val data = nbtPage.getCompoundTag(NBT_PAGE_DATA)
        device.loadBuffer(node!!.address()!!, idx, data)
      }
    }
  }

  override fun save(nbt: NBTTagCompound) {
    super.save(nbt)

    if (screenAddress != null) {
      nbt.setString(SCREEN_KEY, screenAddress)
    }

    nbt.setInteger(BUFFER_INDEX_KEY, bufferIndex)

    val videoRamNbt = NBTTagCompound()
    val nbtPages = NBTTagList()

    val indexes = device.bufferIndexes()
    for (idx in indexes) {
      val page = device.getBuffer(idx) ?: continue // ignore
      val nbtPage = NBTTagCompound()
      nbtPage.setInteger(NBT_PAGE_IDX, idx)
      val data = NBTTagCompound()
      page.data.save(data)
      nbtPage.setTag(NBT_PAGE_DATA, data)
      nbtPages.appendTag(nbtPage)
    }
    videoRamNbt.setTag(NBT_PAGES, nbtPages)
    nbt.setTag(VIDEO_RAM_KEY, videoRamNbt)
  }
}
