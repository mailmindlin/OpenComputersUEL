package li.cil.oc.common.component

import com.google.common.base.Strings
import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.internal.TextBuffer as InternalTextBuffer
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.client.renderer.TextBufferRenderCache
import li.cil.oc.client.renderer.font.TextBufferRenderData
import li.cil.oc.client.ComponentTracker as ClientComponentTracker
import li.cil.oc.client.PacketSender as ClientPacketSender
import li.cil.oc.common.Tier
import li.cil.oc.common.tileentity.Screen as TEScreen
import li.cil.oc.common.tileentity.Computer as TEComputer
import li.cil.oc.common.item.data.NodeData
import li.cil.oc.common.component.traits.TextBufferProxy
import li.cil.oc.common.component.traits.VideoRamRasterizer
import li.cil.oc.common.SaveHandler
import li.cil.oc.common.CompressedPacketBuilder
import li.cil.oc.common.PacketBuilder
import li.cil.oc.common.PacketType
import li.cil.oc.server.component.DeviceInfoKt
import li.cil.oc.server.component.Keyboard
import li.cil.oc.util.*
import li.cil.oc.util.by
import li.cil.oc.server.ComponentTracker as ServerComponentTracker
import li.cil.oc.server.PacketSender as ServerPacketSender
import li.cil.oc.util.TextBuffer as UtilTextBuffer
import net.minecraft.client.Minecraft
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumHand
import net.minecraftforge.event.world.ChunkEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

open class TextBuffer(val host: EnvironmentHost) : AbstractManagedEnvironment(), TextBufferProxy, VideoRamRasterizer, DeviceInfoKt {
    private val node: Node = ApiNetwork.newNode(this, Visibility.Network)
        .withComponent("screen")
        .withConnector()
        .create()

    override val internalRasterizerBuffers: MutableMap<String, VideoRamRasterizer.VirtualRamDevice> = mutableMapOf()

    private var maxResolution: Pair<Int, Int> = Settings.screenResolutionsByTier(Tier.One)

    private var maxDepth = Settings.screenDepthsByTier(Tier.One)

    private var aspectRatio = Pair(1.0, 1.0)

    private var powerConsumptionPerTick = Settings.get.screenCost

    private var precisionMode = false

    // For client side only.
    private var isRendering = true

    private var isDisplaying = true

    private var hasPower = true

    private var relativeLitArea = -1.0

    private var _pendingCommands: PacketBuilder? = null

    private val syncInterval = 100

    private var syncCooldown = syncInterval

    private val pendingCommands: PacketBuilder
        get() {
            var pb = _pendingCommands
            if (pb == null) {
                pb = CompressedPacketBuilder(PacketType.TextBufferMulti)
                pb.writeUTF(node.address())
                _pendingCommands = pb
            }
            return pb
        }

    var fullyLitCost: Double = computeFullyLitCost()

    // This computes the energy cost (per tick) to keep the screen running if
    // every single "pixel" is lit. This cost increases with higher tiers as
    // their maximum resolution (pixel density) increases. For a basic screen
    // this is simply the configured cost.
    fun computeFullyLitCost(): Double {
        val (w, h) = Settings.screenResolutionsByTier(0)
        val mw = maximumWidth
        val mh = maximumHeight
        return powerConsumptionPerTick * (mw * mh) / (w * h)
    }

    val proxy: Proxy =
        if (SideTracker.isClient()) ClientProxy(this)
        else ServerProxy(this)

    override val data: UtilTextBuffer = UtilTextBuffer(maxResolution, PackedColor.Depth.format(maxDepth))

    var viewport: Pair<Int, Int> = data.size

    fun markInitialized() {
        syncCooldown = -1 // Stop polling for init state.
        relativeLitArea = -1.0 // Recompute lit area, avoid screens blanking out until something changes.
    }

    private val deviceInfo: Map<String, String> by lazy {
        mapOf(
            DeviceAttribute.Class.toString() to DeviceClass.Display.toString(),
            DeviceAttribute.Description.toString() to "Text buffer",
            DeviceAttribute.Vendor.toString() to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product.toString() to "Text Screen V0",
            DeviceAttribute.Capacity.toString() to (maxResolution.first * maxResolution.second).toString(),
            DeviceAttribute.Width.toString() to arrayOf("1", "4", "8")[maxDepth.ordinal]
        )
    }

    override fun getDeviceInfo(): java.util.Map<String, String> = deviceInfo as java.util.Map<String, String>

    // ----------------------------------------------------------------------- //

    override val canUpdate = true

    override fun update() {
        super.update()
        if (isDisplaying && Settings.get.isTickMultiple(host.world())) {
            if (relativeLitArea < 0) {
                // The relative lit area is the number of pixels that are not blank
                // versus the number of pixels in the *current* resolution. This is
                // scaled to multi-block screens, since we only compute this for the
                // origin.
                val w = viewportWidth
                val h = viewportHeight
                var acc = 0f
                for (y in 0 until h) {
                    val line = data.buffer[y]
                    val colors = data.color[y]
                    for (x in 0 until w) {
                        val char = line[x]
                        val color = colors[x]
                        val bg = PackedColor.unpackBackground(color, data.format)
                        val fg = PackedColor.unpackForeground(color, data.format)
                        acc += when {
                            char == ' '.code -> if (bg == 0u) 0 else 1
                            char == 0x2588 -> if (fg == 0u) 0 else 1
                            fg == 0u && bg == 0u -> 0
                            else -> 1
                        }
                    }
                }
                relativeLitArea = acc / (w * h).toDouble()
            }
            if (node != null) {
                val hadPower = hasPower
                val neededPower = relativeLitArea * fullyLitCost * Settings.get.tickFrequency
                hasPower = node.tryChangeBuffer(-neededPower)
                if (hasPower != hadPower) {
                    ServerPacketSender.sendTextBufferPowerChange(node.address(), isDisplaying && hasPower, host)
                }
            }
        }

        synchronized(this) {
            _pendingCommands?.sendToPlayersNearHost(host, Settings.get.maxWirelessRange(Tier.Two) * Settings.get.maxWirelessRange(Tier.Two))
            _pendingCommands = null
        }

        if (SideTracker.isClient() && syncCooldown > 0) {
            syncCooldown -= 1
            if (syncCooldown == 0) {
                syncCooldown = syncInterval
                ClientPacketSender.sendTextBufferInit(proxy.nodeAddress)
            }
        }
    }

    // ----------------------------------------------------------------------- //

    @Callback(direct = true, doc = """function():boolean -- Returns whether the screen is currently on.""")
    fun isOn(computer: Context, args: Arguments): Array<Any?> = result(isDisplaying)

    @Callback(doc = """function():boolean -- Turns the screen on. Returns whether the state changed, and whether it is now on.""")
    fun turnOn(computer: Context, args: Arguments): Array<Any?> {
        val oldPowerState = isDisplaying
        setPowerState(true)
        return result(isDisplaying != oldPowerState, isDisplaying)
    }

    @Callback(doc = """function():boolean -- Turns off the screen. Returns whether the state changed, and whether it is now on.""")
    fun turnOff(computer: Context, args: Arguments): Array<Any?> {
        val oldPowerState = isDisplaying
        setPowerState(false)
        return result(isDisplaying != oldPowerState, isDisplaying)
    }

    @Callback(direct = true, doc = """function():number, number -- The aspect ratio of the screen. For multi-block screens this is the number of blocks, horizontal and vertical.""")
    fun getAspectRatio(context: Context, args: Arguments): Array<Any?> = synchronized(this) {
        result(aspectRatio.first, aspectRatio.second)
    }

    @Callback(doc = """function():table -- The list of keyboards attached to the screen.""")
    fun getKeyboards(context: Context, args: Arguments): Array<Any?> {
        context.pause(0.25)
        return when (host) {
            is TEScreen -> {
                arrayOf((host as TEScreen).screens.mapNotNull { it.node() }
                    .flatMap { it.neighbors().filter { n -> n.host() is Keyboard }.map { n -> n.address() } }
                    .toTypedArray())
            }
            else -> {
                arrayOf(node.neighbors().filter { it.host() is Keyboard }.map { it.address() }.toTypedArray())
            }
        }
    }

    @Callback(direct = true, doc = """function():boolean -- Returns whether the screen is in high precision mode (sub-pixel mouse event positions).""")
    fun isPrecise(computer: Context, args: Arguments): Array<Any?> = result(precisionMode)

    @Callback(doc = """function(enabled:boolean):boolean -- Set whether to use high precision mode (sub-pixel mouse event positions).""")
    fun setPrecise(computer: Context, args: Arguments): Array<Any?> {
        // Available for T3 screens only... easiest way to check for us is to
        // base it off of the maximum color depth.
        return if (maxDepth == Settings.screenDepthsByTier(Tier.Three)) {
            val oldValue = precisionMode
            precisionMode = args.checkBoolean(0)
            result(oldValue)
        } else {
            result(Unit, "unsupported operation")
        }
    }

    // ----------------------------------------------------------------------- //

    override fun setEnergyCostPerTick(value: Double) {
        powerConsumptionPerTick = value
        fullyLitCost = computeFullyLitCost()
    }

    override fun getEnergyCostPerTick(): Double = powerConsumptionPerTick

    override fun setPowerState(value: Boolean) {
        if (isDisplaying != value) {
            isDisplaying = value
            if (isDisplaying) {
                val neededPower = fullyLitCost * Settings.get.tickFrequency
                hasPower = node.changeBuffer(-neededPower) == 0.0
            }
            ServerPacketSender.sendTextBufferPowerChange(node.address(), isDisplaying && hasPower, host)
        }
    }

    override fun getPowerState(): Boolean = isDisplaying

    override fun setMaximumResolution(width: Int, height: Int) {
        if (width < 1) throw IllegalArgumentException("width must be larger or equal to one")
        if (height < 1) throw IllegalArgumentException("height must be larger or equal to one")
        maxResolution = Pair(width, height)
        fullyLitCost = computeFullyLitCost()
        proxy.onBufferMaxResolutionChange(width, width)
    }

    override fun getMaximumWidth(): Int = maxResolution.first

    override fun getMaximumHeight(): Int = maxResolution.second

    override fun setAspectRatio(width: Double, height: Double) = synchronized(this) {
        aspectRatio = Pair(width, height)
    }

    override fun getAspectRatio(): Double = aspectRatio.first / aspectRatio.second

    override fun setResolution(w: Int, h: Int): Boolean {
        val (mw, mh) = maxResolution
        if (w < 1 || h < 1 || w > mw || h > mw || h * w > mw * mh)
            throw IllegalArgumentException("unsupported resolution")
        // Always send to clients, their state might be dirty.
        proxy.onBufferResolutionChange(w, h)
        // Force set viewport to new resolution. This is partially for
        // backwards compatibility, and partially to enforce a valid one.
        val sizeChanged = data.setSize(w, h)
        val viewportChanged = setViewport(w, h)
        if (sizeChanged || viewportChanged) {
            if (!viewportChanged && node != null) {
                node.sendToReachable("computer.signal", "screen_resized", Integer.valueOf(w), Integer.valueOf(h))
            }
            return true
        }
        return false
    }

    override fun setViewport(w: Int, h: Int): Boolean {
        val (mw, mh) = data.size
        if (w < 1 || h < 1 || w > mw || h > mh)
            throw IllegalArgumentException("unsupported viewport resolution")
        // Always send to clients, their state might be dirty.
        proxy.onBufferViewportResolutionChange(w, h)
        val (cw, ch) = viewport
        if (w != cw || h != ch) {
            viewport = Pair(w, h)
            if (node != null) {
                node.sendToReachable("computer.signal", "screen_resized", Integer.valueOf(w), Integer.valueOf(h))
            }
            return true
        }
        return false
    }

    override fun getViewportWidth(): Int = viewport.first

    override fun getViewportHeight(): Int = viewport.second

    override fun setMaximumColorDepth(depth: InternalTextBuffer.ColorDepth) {
        maxDepth = depth
    }

    override fun getMaximumColorDepth(): InternalTextBuffer.ColorDepth = maxDepth

    override fun setColorDepth(depth: InternalTextBuffer.ColorDepth): Boolean {
        val colorDepthChanged: Boolean = super.setColorDepth(depth)
        // Always send to clients, their state might be dirty.
        proxy.onBufferDepthChange(depth)
        return colorDepthChanged
    }

    override fun onBufferPaletteChange(index: Int) =
        proxy.onBufferPaletteChange(index)

    override fun onBufferColorChange() =
        proxy.onBufferColorChange()

    override fun onBufferCopy(col: Int, row: Int, w: Int, h: Int, tx: Int, ty: Int) {
        proxy.onBufferCopy(col, row, w, h, tx, ty)
    }

    override fun onBufferFill(col: Int, row: Int, w: Int, h: Int, c: Int) {
        proxy.onBufferFill(col, row, w, h, c)
    }

    override fun onBufferSet(col: Int, row: Int, s: String, vertical: Boolean) {
        proxy.onBufferSet(col, row, s, vertical)
    }

    fun onBufferBitBlt(col: Int, row: Int, w: Int, h: Int, ram: GpuTextBuffer, fromCol: Int, fromRow: Int) {
        proxy.onBufferBitBlt(col, row, w, h, ram, fromCol, fromRow)
    }

    override fun onBufferRamInit(ram: GpuTextBuffer) {
        proxy.onBufferRamInit(ram)
    }

    override fun onBufferRamDestroy(ram: GpuTextBuffer) {
        proxy.onBufferRamDestroy(ram)
    }

    override fun onBufferBitBlt(col: Int, row: Int, w: Int, h: Int, ram: GpuTextBuffer, fromCol: Int, fromRow: Int) {
        proxy.onBufferBitBlt(col, row, w, h, ram, fromCol, fromRow)
    }

    override fun rawSetText(col: Int, row: Int, text: Array<IntArray>) {
        super.rawSetText(col, row, text)
        proxy.onBufferRawSetText(col, row, text)
    }

    override fun rawSetBackground(col: Int, row: Int, color: Array<IntArray>) {
        super.rawSetBackground(col, row, color)
        // Better for bandwidth to send packed shorts here. Would need a special case for handling on client,
        // though, so let's be wasteful for once...
        proxy.onBufferRawSetBackground(col, row, color)
    }

    override fun rawSetForeground(col: Int, row: Int, color: Array<IntArray>) {
        super.rawSetForeground(col, row, color)
        // Better for bandwidth to send packed shorts here. Would need a special case for handling on client,
        // though, so let's be wasteful for once...
        proxy.onBufferRawSetForeground(col, row, color)
    }

    @SideOnly(Side.CLIENT)
    override fun renderText(): Boolean = relativeLitArea != 0.0 && proxy.render()

    @SideOnly(Side.CLIENT)
    override fun renderWidth(): Int = TextBufferRenderCache.renderer.charRenderWidth * viewportWidth

    @SideOnly(Side.CLIENT)
    override fun renderHeight(): Int = TextBufferRenderCache.renderer.charRenderHeight * viewportHeight

    @SideOnly(Side.CLIENT)
    override fun setRenderingEnabled(enabled: Boolean) {
        isRendering = enabled
    }

    @SideOnly(Side.CLIENT)
    override fun isRenderingEnabled(): Boolean = isRendering

    override fun keyDown(character: Char, code: Int, player: EntityPlayer) =
        proxy.keyDown(character, code, player)

    override fun keyUp(character: Char, code: Int, player: EntityPlayer) =
        proxy.keyUp(character, code, player)

    override fun clipboard(value: String, player: EntityPlayer) =
        proxy.clipboard(value, player)

    override fun mouseDown(x: Double, y: Double, button: Int, player: EntityPlayer) =
        proxy.mouseDown(x, y, button, player)

    override fun mouseDrag(x: Double, y: Double, button: Int, player: EntityPlayer) =
        proxy.mouseDrag(x, y, button, player)

    override fun mouseUp(x: Double, y: Double, button: Int, player: EntityPlayer) =
        proxy.mouseUp(x, y, button, player)

    override fun mouseScroll(x: Double, y: Double, delta: Int, player: EntityPlayer) =
        proxy.mouseScroll(x, y, delta, player)

    fun copyToAnalyzer(line: Int, player: EntityPlayer) {
        proxy.copyToAnalyzer(line, player)
    }

    // ----------------------------------------------------------------------- //

    override fun onConnect(node: Node) {
        super.onConnect(node)
        if (node == this.node) {
            ServerComponentTracker.add(host.world(), node.address(), this)
        }
    }

    override fun onDisconnect(node: Node) {
        super.onDisconnect(node)
        if (node == this.node) {
            ServerComponentTracker.remove(host.world(), this)
        }
    }

    // ----------------------------------------------------------------------- //

    private fun bufferPath() = node.address() + "_buffer"
    private val IsOnTag = Settings.namespace + "isOn"
    private val HasPowerTag = Settings.namespace + "hasPower"
    private val MaxWidthTag = Settings.namespace + "maxWidth"
    private val MaxHeightTag = Settings.namespace + "maxHeight"
    private val PreciseTag = Settings.namespace + "precise"
    private val ViewportWidthTag = Settings.namespace + "viewportWidth"
    private val ViewportHeightTag = Settings.namespace + "viewportHeight"

    override fun load(nbt: NBTTagCompound) {
        super.load(nbt)
        if (SideTracker.isClient()) {
            if (!Strings.isNullOrEmpty(proxy.nodeAddress)) return // Only load once.
            proxy.nodeAddress = nbt.getCompoundTag(NodeData.NodeTag).getString(NodeData.AddressTag)
            Companion.registerClientBuffer(this)
        } else {
            if (nbt.hasKey(NodeData.BufferTag)) {
                data.load(nbt.getCompoundTag(NodeData.BufferTag))
            } else if (!Strings.isNullOrEmpty(node.address())) {
                data.load(SaveHandler.loadNBT(nbt, bufferPath()))
            }
        }

        if (nbt.hasKey(IsOnTag)) {
            isDisplaying = nbt.getBoolean(IsOnTag)
        }
        if (nbt.hasKey(HasPowerTag)) {
            hasPower = nbt.getBoolean(HasPowerTag)
        }
        if (nbt.hasKey(MaxWidthTag) && nbt.hasKey(MaxHeightTag)) {
            val maxWidth = nbt.getInteger(MaxWidthTag)
            val maxHeight = nbt.getInteger(MaxHeightTag)
            maxResolution = Pair(maxWidth, maxHeight)
        }
        precisionMode = nbt.getBoolean(PreciseTag)

        if (nbt.hasKey(ViewportWidthTag)) {
            val vpw = nbt.getInteger(ViewportWidthTag)
            val vph = nbt.getInteger(ViewportHeightTag)
            viewport = Pair(minOf(vpw, data.width).coerceAtLeast(1), minOf(vph, data.height).coerceAtLeast(1))
        } else {
            viewport = data.size
        }
    }

    // Null check for Waila (and other mods that may call this client side).
    override fun save(nbt: NBTTagCompound) {
        if (node == null) return
        super.save(nbt)
        // Happy thread synchronization hack! Here's the problem: GPUs allow direct
        // calls for modifying screens to give a more responsive experience. This
        // causes the following problem: when saving, if the screen is saved first,
        // then the executor runs in parallel and changes the screen *before* the
        // server thread begins saving that computer, the saved computer will think
        // it changed the screen, although the saved screen wasn't. To avoid that we
        // wait for all computers the screen is connected to to finish their current
        // execution and pausing them (which will make them resume in the next tick
        // when their update() runs).
        if (node.network() != null) {
            for (networkNode in node.network().nodes()) {
                val host = networkNode.host()
                if (host is TEComputer) {
                    if (!host.machine.isPaused) {
                        host.machine.pause(0.1)
                    }
                }
            }
        }

        SaveHandler.scheduleSave(host, nbt, bufferPath()) { data.save(it) }
        nbt.setBoolean(IsOnTag, isDisplaying)
        nbt.setBoolean(HasPowerTag, hasPower)
        nbt.setInteger(MaxWidthTag, maxResolution.first)
        nbt.setInteger(MaxHeightTag, maxResolution.second)
        nbt.setBoolean(PreciseTag, precisionMode)
        nbt.setInteger(ViewportWidthTag, viewport.first)
        nbt.setInteger(ViewportHeightTag, viewport.second)
    }

    companion object {
        @JvmField
        var clientBuffers = mutableListOf<TextBuffer>()

        @JvmStatic
        @SubscribeEvent
        fun onChunkUnload(e: ChunkEvent.Unload) {
            val chunk = e.chunk
            clientBuffers = clientBuffers.filter { t ->
                val blockPos = BlockPosition(t.host)
                val keep = t.host.world() != e.world || !chunk.isAtLocation(blockPos.x shr 4, blockPos.z shr 4)
                if (!keep) {
                    ClientComponentTracker.remove(t.host.world(), t)
                }
                keep
            }.toMutableList()
        }

        @JvmStatic
        @SubscribeEvent
        fun onWorldUnload(e: WorldEvent.Unload) {
            clientBuffers = clientBuffers.filter { t ->
                val keep = t.host.world() != e.world
                if (!keep) {
                    ClientComponentTracker.remove(t.host.world(), t)
                }
                keep
            }.toMutableList()
        }

        @JvmStatic
        fun registerClientBuffer(t: TextBuffer) {
            ClientPacketSender.sendTextBufferInit(t.proxy.nodeAddress)
            ClientComponentTracker.add(t.host.world(), t.proxy.nodeAddress, t)
            clientBuffers.add(t)
        }
    }

    abstract class Proxy {
        abstract val owner: TextBuffer

        var dirty = false

        var nodeAddress = ""

        fun markDirty() {
            dirty = true
        }

        open fun render(): Boolean = false

        abstract fun onBufferColorChange()

        open fun onBufferCopy(col: Int, row: Int, w: Int, h: Int, tx: Int, ty: Int) {
            owner.relativeLitArea = -1.0
        }

        abstract fun onBufferDepthChange(depth: InternalTextBuffer.ColorDepth)

        open fun onBufferFill(col: Int, row: Int, w: Int, h: Int, c: Int) {
            owner.relativeLitArea = -1.0
        }

        abstract fun onBufferPaletteChange(index: Int)

        open fun onBufferResolutionChange(w: Int, h: Int) {
            owner.relativeLitArea = -1.0
        }

        open fun onBufferViewportResolutionChange(w: Int, h: Int) {
            owner.relativeLitArea = -1.0
        }

        open fun onBufferMaxResolutionChange(w: Int, h: Int) {
        }

        open fun onBufferSet(col: Int, row: Int, s: String, vertical: Boolean) {
            owner.relativeLitArea = -1.0
        }

        open fun onBufferBitBlt(col: Int, row: Int, w: Int, h: Int, ram: GpuTextBuffer, fromCol: Int, fromRow: Int) {
            owner.relativeLitArea = -1.0
        }

        open fun onBufferRamInit(ram: GpuTextBuffer) {
            owner.relativeLitArea = -1.0
        }

        open fun onBufferRamDestroy(ram: GpuTextBuffer) {
            owner.relativeLitArea = -1.0
        }

        open fun onBufferRawSetText(col: Int, row: Int, text: Array<IntArray>) {
            owner.relativeLitArea = -1.0
        }

        open fun onBufferRawSetBackground(col: Int, row: Int, color: Array<IntArray>) {
            owner.relativeLitArea = -1.0
        }

        open fun onBufferRawSetForeground(col: Int, row: Int, color: Array<IntArray>) {
            owner.relativeLitArea = -1.0
        }

        abstract fun keyDown(character: Char, code: Int, player: EntityPlayer)

        abstract fun keyUp(character: Char, code: Int, player: EntityPlayer)

        abstract fun clipboard(value: String, player: EntityPlayer)

        abstract fun mouseDown(x: Double, y: Double, button: Int, player: EntityPlayer)

        abstract fun mouseDrag(x: Double, y: Double, button: Int, player: EntityPlayer)

        abstract fun mouseUp(x: Double, y: Double, button: Int, player: EntityPlayer)

        abstract fun mouseScroll(x: Double, y: Double, delta: Int, player: EntityPlayer)

        abstract fun copyToAnalyzer(line: Int, player: EntityPlayer)
    }

    class ClientProxy(override val owner: TextBuffer) : Proxy() {
        val renderer = object : TextBufferRenderData() {
            override var dirty: Boolean
                get() = this@ClientProxy.dirty
                set(value) { this@ClientProxy.dirty = value }

            override val data: UtilTextBuffer get() = owner.data

            override val viewport: Pair<Int, Int> get() = owner.viewport
        }

        override fun render(): Boolean {
            val wasDirty = dirty
            TextBufferRenderCache.render(renderer)
            return wasDirty
        }

        override fun onBufferColorChange() {
            markDirty()
        }

        override fun onBufferCopy(col: Int, row: Int, w: Int, h: Int, tx: Int, ty: Int) {
            super.onBufferCopy(col, row, w, h, tx, ty)
            markDirty()
        }

        override fun onBufferDepthChange(depth: InternalTextBuffer.ColorDepth) {
            markDirty()
        }

        override fun onBufferFill(col: Int, row: Int, w: Int, h: Int, c: Int) {
            super.onBufferFill(col, row, w, h, c)
            markDirty()
        }

        override fun onBufferPaletteChange(index: Int) {
            markDirty()
        }

        override fun onBufferResolutionChange(w: Int, h: Int) {
            super.onBufferResolutionChange(w, h)
            markDirty()
        }

        override fun onBufferViewportResolutionChange(w: Int, h: Int) {
            super.onBufferViewportResolutionChange(w, h)
            markDirty()
        }

        override fun onBufferSet(col: Int, row: Int, s: String, vertical: Boolean) {
            super.onBufferSet(col, row, s, vertical)
            markDirty()
        }

        override fun onBufferBitBlt(col: Int, row: Int, w: Int, h: Int, ram: GpuTextBuffer, fromCol: Int, fromRow: Int) {
            super.onBufferBitBlt(col, row, w, h, ram, fromCol, fromRow)
            markDirty()
        }

        override fun onBufferRamInit(ram: GpuTextBuffer) {
            super.onBufferRamInit(ram)
        }

        override fun onBufferRamDestroy(ram: GpuTextBuffer) {
            super.onBufferRamDestroy(ram)
        }

        override fun keyDown(character: Char, code: Int, player: EntityPlayer) {
            debug("{type = keyDown, char = $character, code = $code}")
            ClientPacketSender.sendKeyDown(nodeAddress, character, code)
        }

        override fun keyUp(character: Char, code: Int, player: EntityPlayer) {
            debug("{type = keyUp, char = $character, code = $code}")
            ClientPacketSender.sendKeyUp(nodeAddress, character, code)
        }

        override fun clipboard(value: String, player: EntityPlayer) {
            debug("{type = clipboard}")
            ClientPacketSender.sendClipboard(nodeAddress, value)
        }

        override fun mouseDown(x: Double, y: Double, button: Int, player: EntityPlayer) {
            debug("{type = mouseDown, x = $x, y = $y, button = $button}")
            ClientPacketSender.sendMouseClick(nodeAddress, x, y, false, button)
        }

        override fun mouseDrag(x: Double, y: Double, button: Int, player: EntityPlayer) {
            debug("{type = mouseDrag, x = $x, y = $y, button = $button}")
            ClientPacketSender.sendMouseClick(nodeAddress, x, y, true, button)
        }

        override fun mouseUp(x: Double, y: Double, button: Int, player: EntityPlayer) {
            debug("{type = mouseUp, x = $x, y = $y, button = $button}")
            ClientPacketSender.sendMouseUp(nodeAddress, x, y, button)
        }

        override fun mouseScroll(x: Double, y: Double, delta: Int, player: EntityPlayer) {
            debug("{type = mouseScroll, x = $x, y = $y, delta = $delta}")
            ClientPacketSender.sendMouseScroll(nodeAddress, x, y, delta)
        }

        override fun copyToAnalyzer(line: Int, player: EntityPlayer) {
            ClientPacketSender.sendCopyToAnalyzer(nodeAddress, line)
        }

        private val Debugger by lazy { ApiItems.get(Constants.ItemName.Debugger) }

        private fun debug(message: String) {
            val mc = Minecraft.getMinecraft()
            if (mc != null && mc.player != null && ApiItems.get(mc.player.heldItemMainhand) == Debugger) {
                OpenComputers.log.info("[NETWORK DEBUGGER] Sending packet to node $nodeAddress: $message")
            }
        }
    }

    class ServerProxy(override val owner: TextBuffer) : Proxy() {
        override fun onBufferColorChange() {
            owner.host.markChanged()
            synchronized(owner) {
                ServerPacketSender.appendTextBufferColorChange(owner.pendingCommands, owner.data.foreground, owner.data.background)
            }
        }

        override fun onBufferCopy(col: Int, row: Int, w: Int, h: Int, tx: Int, ty: Int) {
            super.onBufferCopy(col, row, w, h, tx, ty)
            owner.host.markChanged()
            synchronized(owner) {
                ServerPacketSender.appendTextBufferCopy(owner.pendingCommands, col, row, w, h, tx, ty)
            }
        }

        override fun onBufferDepthChange(depth: InternalTextBuffer.ColorDepth) {
            owner.host.markChanged()
            synchronized(owner) {
                ServerPacketSender.appendTextBufferDepthChange(owner.pendingCommands, depth)
            }
        }

        override fun onBufferFill(col: Int, row: Int, w: Int, h: Int, c: Int) {
            super.onBufferFill(col, row, w, h, c)
            owner.host.markChanged()
            synchronized(owner) {
                ServerPacketSender.appendTextBufferFill(owner.pendingCommands, col, row, w, h, c)
            }
        }

        override fun onBufferPaletteChange(index: Int) {
            owner.host.markChanged()
            synchronized(owner) {
                ServerPacketSender.appendTextBufferPaletteChange(owner.pendingCommands, index, owner.getPaletteColor(index))
            }
        }

        override fun onBufferResolutionChange(w: Int, h: Int) {
            super.onBufferResolutionChange(w, h)
            owner.host.markChanged()
            synchronized(owner) {
                ServerPacketSender.appendTextBufferResolutionChange(owner.pendingCommands, w, h)
            }
        }

        override fun onBufferViewportResolutionChange(w: Int, h: Int) {
            super.onBufferViewportResolutionChange(w, h)
            owner.host.markChanged()
            synchronized(owner) {
                ServerPacketSender.appendTextBufferViewportResolutionChange(owner.pendingCommands, w, h)
            }
        }

        override fun onBufferMaxResolutionChange(w: Int, h: Int) {
            if (owner.node.network() != null) {
                super.onBufferMaxResolutionChange(w, h)
                owner.host.markChanged()
                synchronized(owner) {
                    ServerPacketSender.appendTextBufferMaxResolutionChange(owner.pendingCommands, w, h)
                }
            }
        }

        override fun onBufferSet(col: Int, row: Int, s: String, vertical: Boolean) {
            super.onBufferSet(col, row, s, vertical)
            owner.host.markChanged()
            synchronized(owner) {
                ServerPacketSender.appendTextBufferSet(owner.pendingCommands, col, row, s, vertical)
            }
        }

        override fun onBufferBitBlt(col: Int, row: Int, w: Int, h: Int, ram: GpuTextBuffer, fromCol: Int, fromRow: Int) {
            super.onBufferBitBlt(col, row, w, h, ram, fromCol, fromRow)
            owner.host.markChanged()
            synchronized(owner) {
                ServerPacketSender.appendTextBufferBitBlt(owner.pendingCommands, col, row, w, h, ram.owner, ram.id, fromCol, fromRow)
            }
        }

        override fun onBufferRamInit(ram: GpuTextBuffer) {
            super.onBufferRamInit(ram)
            owner.host.markChanged()
            val nbt = NBTTagCompound()
            ram.save(nbt)
            synchronized(owner) {
                ServerPacketSender.appendTextBufferRamInit(owner.pendingCommands, ram.owner, ram.id, nbt)
            }
        }

        override fun onBufferRamDestroy(ram: GpuTextBuffer) {
            super.onBufferRamDestroy(ram)
            owner.host.markChanged()
            synchronized(owner) {
                ServerPacketSender.appendTextBufferRamDestroy(owner.pendingCommands, ram.owner, ram.id)
            }
        }

        override fun onBufferRawSetText(col: Int, row: Int, text: Array<IntArray>) {
            super.onBufferRawSetText(col, row, text)
            owner.host.markChanged()
            synchronized(owner) {
                ServerPacketSender.appendTextBufferRawSetText(owner.pendingCommands, col, row, text)
            }
        }

        override fun onBufferRawSetBackground(col: Int, row: Int, color: Array<IntArray>) {
            super.onBufferRawSetBackground(col, row, color)
            owner.host.markChanged()
            synchronized(owner) {
                ServerPacketSender.appendTextBufferRawSetBackground(owner.pendingCommands, col, row, color)
            }
        }

        override fun onBufferRawSetForeground(col: Int, row: Int, color: Array<IntArray>) {
            super.onBufferRawSetForeground(col, row, color)
            owner.host.markChanged()
            synchronized(owner) {
                ServerPacketSender.appendTextBufferRawSetForeground(owner.pendingCommands, col, row, color)
            }
        }

        override fun keyDown(character: Char, code: Int, player: EntityPlayer) {
            sendToKeyboards("keyboard.keyDown", player, Character.valueOf(character), Integer.valueOf(code))
        }

        override fun keyUp(character: Char, code: Int, player: EntityPlayer) {
            sendToKeyboards("keyboard.keyUp", player, Character.valueOf(character), Integer.valueOf(code))
        }

        override fun clipboard(value: String, player: EntityPlayer) {
            sendToKeyboards("keyboard.clipboard", player, value)
        }

        override fun mouseDown(x: Double, y: Double, button: Int, player: EntityPlayer) {
            sendMouseEvent(player, "touch", x, y, button)
        }

        override fun mouseDrag(x: Double, y: Double, button: Int, player: EntityPlayer) {
            sendMouseEvent(player, "drag", x, y, button)
        }

        override fun mouseUp(x: Double, y: Double, button: Int, player: EntityPlayer) {
            sendMouseEvent(player, "drop", x, y, button)
        }

        override fun mouseScroll(x: Double, y: Double, delta: Int, player: EntityPlayer) {
            sendMouseEvent(player, "scroll", x, y, delta)
        }

        override fun copyToAnalyzer(line: Int, player: EntityPlayer) {
            val stack = player.getHeldItem(EnumHand.MAIN_HAND)
            if (!stack.isEmpty) {
                if (!stack.hasTagCompound()) {
                    stack.tagCompound = NBTTagCompound()
                }
                stack.tagCompound!!.removeTag(Settings.namespace + "clipboard")

                if (line >= 0 && line < owner.viewportHeight) {
                    val text = owner.data.lineToString(line)
                    if (!Strings.isNullOrEmpty(text)) {
                        stack.tagCompound!!.setString(Settings.namespace + "clipboard", text)
                    }
                }

                if (stack.tagCompound!!.isEmpty) {
                    stack.tagCompound = null
                }
            }
        }

        private fun sendMouseEvent(player: EntityPlayer, name: String, x: Double, y: Double, data: Int) {
            val args = mutableListOf<Any?>()

            args.add(player)
            args.add(name)
            if (owner.precisionMode) {
                args.add(java.lang.Double.valueOf(x))
                args.add(java.lang.Double.valueOf(y))
            } else {
                args.add(Integer.valueOf(x.toInt() + 1))
                args.add(Integer.valueOf(y.toInt() + 1))
            }
            args.add(Integer.valueOf(data))
            if (Settings.get.inputUsername) {
                args.add(player.name)
            }

            owner.node.sendToReachable("computer.checked_signal", *args.toTypedArray())
        }

        private fun sendToKeyboards(name: String, vararg values: Any?) {
            when (val host = owner.host) {
                is TEScreen -> {
                    host.screens.forEach { it.node()?.let { node -> node.sendToNeighbors(name, *values) } }
                }
                else -> {
                    owner.node.sendToNeighbors(name, *values)
                }
            }
        }
    }
}
