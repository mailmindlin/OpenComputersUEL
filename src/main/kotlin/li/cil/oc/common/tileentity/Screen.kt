package li.cil.oc.common.tileentity

import li.cil.oc.Settings
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network.*
import li.cil.oc.client.gui.Screen as ScreenGui
import li.cil.oc.common.component.TextBuffer
import li.cil.oc.common.tileentity.traits.*
import li.cil.oc.common.tileentity.traits.Colored
import li.cil.oc.common.tileentity.traits.RedstoneAware
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.Color
import li.cil.oc.common.tileentity.traits.TextBuffer as TraitTextBuffer
import li.cil.oc.util.blockExists
import li.cil.oc.util.getTileEntity
import net.minecraft.client.Minecraft
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.projectile.EntityArrow
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.util.*
import kotlin.collections.LinkedHashSet
import kotlin.math.max
import kotlin.math.min
import li.cil.oc.common.tileentity.traits.RedstoneAware as TraitRedstoneAware
import li.cil.oc.common.tileentity.traits.Colored as TraitColored

class Screen(var tier: Int = 0) : TileEntityBase.TEEnvironmentBase(), TraitTextBuffer, SidedEnvironment, Rotatable, TraitRedstoneAware, TraitColored, Analyzable, Comparable<Screen> {
    override val colorDelegate: Colored.Delegate = register(Colored::Delegate)
    override val textBufferDelegate: TraitTextBuffer.Delegate = register { TraitTextBuffer.Delegate(this, tier) }
    override val rotatableDelegate: Rotatable.RotatableDelegate = register(Rotatable::RotatableDelegate)
    override val redstoneDelegate: RedstoneAware.Delegate = register(RedstoneAware::Delegate)
    init {
        // Enable redstone functionality.
        redstoneDelegate.isOutputEnabled = true
        color = Color.rgbValues(Color.byTier[tier])
    }


    // ----------------------------------------------------------------------- //

    /**
     * Check for multi-block screen option in next update. We do this in the
     * update to avoid unnecessary checks on chunk unload.
     */
    var shouldCheckForMultiBlock = true

    /**
     * On the client we delay connecting screens a little, to avoid glitches
     * when not all tile entity data for a chunk has been received within a
     * single tick (meaning some screens are still "missing").
     */
    var delayUntilCheckForMultiBlock = 40

    var width = 1
    var height = 1

    var origin: Screen = this

    val screens = LinkedHashSet<Screen>().apply { add(this@Screen) }

    var hadRedstoneInput = false

    var cachedBounds: AxisAlignedBB? = null

    var invertTouchMode = false

    private val arrows = mutableSetOf<EntityArrow>()

    private val lastWalked = WeakHashMap<Entity, Pair<Int, Int>>()

    @SideOnly(Side.CLIENT)
    override fun canConnect(side: EnumFacing): Boolean = side != facing()

    // Allow connections from front for keyboards, and keyboards only...
    override fun sidedNode(side: EnumFacing): Node? =
        if (side != facing() || (world.isBlockLoaded(pos.offset(side)) && world.getTileEntity(pos.offset(side)) is Keyboard))
            node()
        else
            null

    // ----------------------------------------------------------------------- //

    fun isOrigin(): Boolean = origin == this

    fun localPosition(): Pair<Int, Int> {
        val lpos = project(this)
        val opos = project(origin)
        return Pair(lpos.x - opos.x, lpos.y - opos.y)
    }

    fun hasKeyboard(): Boolean = screens.any { screen ->
        EnumFacing.values().any { side ->
            val blockPos = BlockPosition(screen).offset(side)
            if (!world.blockExists(blockPos)) return@any false
            val te = world.getTileEntity(pos.offset(side)) as? Keyboard ?: return@any false
            te.hasNodeOnSide(side.opposite)
        }
    }

    fun checkMultiBlock() {
        shouldCheckForMultiBlock = true
        width = 1
        height = 1
        origin = this
        screens.clear()
        screens.add(this)
        cachedBounds = null
        invertTouchMode = false
    }

    private fun toScreenCoordinates(hitX: Double, hitY: Double, hitZ: Double): Pair<Boolean, Pair<Double, Double>?> {
        // Compute absolute position of the click on the face, measured in blocks.
        fun dot(f: EnumFacing) = f.xOffset * hitX + f.yOffset * hitY + f.zOffset * hitZ
        val hx = dot(toGlobal(EnumFacing.EAST))
        val hy = dot(toGlobal(EnumFacing.UP))
        val tx = if (hx < 0) 1 + hx else hx
        val ty = 1 - (if (hy < 0) 1 + hy else hy)
        val (lx, ly) = localPosition()
        val ax = lx + tx
        val ay = height - 1 - ly + ty

        // Get the relative position in the *display area* of the face.
        val border = 2.25 / 16.0
        if (ax <= border || ay <= border || ax >= width - border || ay >= height - border) {
            return Pair(false, null)
        }
        if (!world.isRemote) return Pair(true, null)

        val iw = width - border * 2
        val ih = height - border * 2
        val rx = (ax - border) / iw
        val ry = (ay - border) / ih

        // Make it a relative position in the displayed buffer.
        val bw = origin.buffer.viewportWidth
        val bh = origin.buffer.viewportHeight
        val bpw = origin.buffer.renderWidth() / iw
        val bph = origin.buffer.renderHeight() / ih
        val (brx, bry) = when {
            bpw > bph -> {
                val rh = bph / bpw
                val bry = (ry - (1 - rh) * 0.5) / rh
                Pair(rx, bry)
            }
            bph > bpw -> {
                val rw = bpw / bph
                val brx = (rx - (1 - rw) * 0.5) / rw
                Pair(brx, ry)
            }
            else -> Pair(rx, ry)
        }

        val inBounds = bry >= 0 && bry <= 1 && brx >= 0 || brx <= 1
        return Pair(inBounds, Pair(brx * bw, bry * bh))
    }

    /**
     * Copy the character at the given coordinates to the analyzer
     */
    fun copyToAnalyzer(player: EntityPlayer, hitX: Float, hitY: Float, hitZ: Float): Boolean {
        val (inBounds, coordinates) = toScreenCoordinates(hitX.toDouble(), hitY.toDouble(), hitZ.toDouble())
        val (x, y) = coordinates ?: return inBounds
        val buffer = origin.buffer as? TextBuffer ?: return false
        buffer.copyToAnalyzer(y.toInt(), player)
        return true
    }

    fun click(hitX: Double, hitY: Double, hitZ: Double): Boolean {
        val (inBounds, coordinates) = toScreenCoordinates(hitX, hitY, hitZ)
        return coordinates?.let { (x, y) ->
            // Send the packet to the server (manually, for accuracy).
            origin.buffer.mouseDown(x, y, 0, null)
            true
        } ?: inBounds
    }

    fun walk(entity: Entity) {
        val (x, y) = localPosition()
        val lastPos = lastWalked.put(entity, localPosition())
        if (lastPos == null || lastPos.first != x || lastPos.second != y) {
            when {
                entity is EntityPlayer && Settings.get.inputUsername ->
                    origin.node().sendToReachable("computer.signal", "walk", x + 1, height - y, entity.name)
                else ->
                    origin.node().sendToReachable("computer.signal", "walk", x + 1, height - y)
            }
        }
    }

    fun shot(arrow: EntityArrow) {
        arrows.add(arrow)
    }

    // ----------------------------------------------------------------------- //

    internal val buffer get() = textBufferDelegate.buffer

    override fun updateEntity() {
        super<TEEnvironmentBase>.updateEntity()
        if (shouldCheckForMultiBlock && ((isClient && isClientReadyForMultiBlockCheck()) || (isServer && isConnected))) {
            // Make sure we merge in a deterministic order, to avoid getting
            // different results on server and client due to the update order
            // differing between the two. This also saves us from having to save
            // any multi-block specific state information.
            val pending = sortedSetOf<Screen>(this)
            val queue = ArrayDeque<Screen>().apply { add(this@Screen) }
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                val lpos = project(current)
                fun tryQueue(dx: Int, dy: Int) {
                    val npos = unproject(lpos.x + dx, lpos.y + dy, lpos.z)
                    if (world.blockExists(npos)) {
                        val te = world.getTileEntity(npos)
                        if (te is Screen && te.pitch == pitch && te.yaw == yaw && pending.add(te)) {
                            queue.add(te)
                        }
                    }
                }
                tryQueue(-1, 0)
                tryQueue(1, 0)
                tryQueue(0, -1)
                tryQueue(0, 1)
            }
            // Perform actual merges.
            while (pending.isNotEmpty()) {
                val current = pending.first()
                while (current.tryMerge()) {}
                val screenList = current.screens.toList()
                screenList.forEach { screen ->
                    screen.shouldCheckForMultiBlock = false
                    pending.remove(screen)
                    queue.add(screen)
                }
                if (isClient) {
                    val bounds = current.origin.getRenderBoundingBox()
                    world.markBlockRangeForRenderUpdate(
                        bounds.minX.toInt(), bounds.minY.toInt(), bounds.minZ.toInt(),
                        bounds.maxX.toInt(), bounds.maxY.toInt(), bounds.maxZ.toInt()
                    )
                }
            }
            // Update visibility after everything is done, to avoid noise.
            queue.forEach { screen ->
                val buffer = screen.buffer
                if (screen.isOrigin()) {
                    if (isServer) {
                        (buffer.node() as Component).setVisibility(Visibility.Network)
                        buffer.energyCostPerTick = Settings.get.screenCost * screen.width * screen.height
                        buffer.setAspectRatio(screen.width.toDouble(), screen.height.toDouble())
                    }
                } else {
                    if (isServer) {
                        (buffer.node() as Component).setVisibility(Visibility.None)
                        buffer.energyCostPerTick = Settings.get.screenCost
                    }
                    buffer.setAspectRatio(1.0, 1.0)
                    val w = buffer.width
                    val h = buffer.height
                    buffer.setForegroundColor(0xFFFFFF, false)
                    buffer.setBackgroundColor(0x000000, false)
                    buffer.fill(0, 0, w, h, 0x20)
                }
            }
        }
        if (arrows.isNotEmpty()) {
            for (arrow in arrows) {
                val hitX = arrow.posX - pos.x
                val hitY = arrow.posY - pos.y
                val hitZ = arrow.posZ - pos.z
                when (val shooter = arrow.shootingEntity) {
                    is EntityPlayer -> {
                        if (shooter == Minecraft.getMinecraft().player) {
                            click(hitX, hitY, hitZ)
                        }
                    }
                }
            }
            arrows.clear()
        }
    }

    private fun isClientReadyForMultiBlockCheck(): Boolean {
        return if (delayUntilCheckForMultiBlock > 0) {
            delayUntilCheckForMultiBlock--
            false
        } else {
            true
        }
    }

    override fun dispose() {
        super<TEEnvironmentBase>.dispose()
        screens.toList().forEach { it.checkMultiBlock() }
        if (isClient) {
            val currentScreen = Minecraft.getMinecraft().currentScreen
            if (currentScreen is ScreenGui && currentScreen.buffer == buffer) {
                Minecraft.getMinecraft().displayGuiScreen(null)
            }
        }
    }

    override fun onColorChanged() {
        super.onColorChanged()
        screens.toList().forEach { it.checkMultiBlock() }
    }

    // ----------------------------------------------------------------------- //

    private val TierTag = Settings.namespace + "tier"
    private val HadRedstoneInputTag = Settings.namespace + "hadRedstoneInput"
    private val InvertTouchModeTag = Settings.namespace + "invertTouchMode"

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        tier = max(0, min(2, nbt.getByte(TierTag).toInt()))
        color = Color.rgbValues(Color.byTier[tier])
        super<RedstoneAware>.readFromNBTForServer(nbt)
        hadRedstoneInput = nbt.getBoolean(HadRedstoneInputTag)
        invertTouchMode = nbt.getBoolean(InvertTouchModeTag)
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        nbt.setByte(TierTag, tier.toByte())
        super<TEEnvironmentBase>.writeToNBTForServer(nbt)
        nbt.setBoolean(HadRedstoneInputTag, hadRedstoneInput)
        nbt.setBoolean(InvertTouchModeTag, invertTouchMode)
    }

    @SideOnly(Side.CLIENT)
    override fun readFromNBTForClient(nbt: NBTTagCompound) {
        tier = max(0, min(2, nbt.getByte(TierTag).toInt()))
        super.readFromNBTForClient(nbt)
        invertTouchMode = nbt.getBoolean(InvertTouchModeTag)
    }

    override fun writeToNBTForClient(nbt: NBTTagCompound) {
        nbt.setByte(TierTag, tier.toByte())
        super.writeToNBTForClient(nbt)
        nbt.setBoolean(InvertTouchModeTag, invertTouchMode)
    }

    // ----------------------------------------------------------------------- //

    @SideOnly(Side.CLIENT)
    override fun getRenderBoundingBox(): AxisAlignedBB {
        if ((width == 1 && height == 1) || !isOrigin()) return super.getRenderBoundingBox()

        return cachedBounds ?: run {
            val spos = unproject(width, height, 1)
            val ox = pos.x + (if (spos.x < 0) 1 else 0)
            val oy = pos.y + (if (spos.y < 0) 1 else 0)
            val oz = pos.z + (if (spos.z < 0) 1 else 0)
            val btmp = AxisAlignedBB(
                ox.toDouble(), oy.toDouble(), oz.toDouble(),
                (ox + spos.x).toDouble(), (oy + spos.y).toDouble(), (oz + spos.z).toDouble()
            )
            val b = AxisAlignedBB(
                min(btmp.minX, btmp.maxX), min(btmp.minY, btmp.maxY), min(btmp.minZ, btmp.maxZ),
                max(btmp.minX, btmp.maxX), max(btmp.minY, btmp.maxY), max(btmp.minZ, btmp.maxZ)
            )
            cachedBounds = b
            b
        }
    }

    @SideOnly(Side.CLIENT)
    override fun getMaxRenderDistanceSquared(): Double =
        if (isOrigin()) super.getMaxRenderDistanceSquared() else 0.0

    // ----------------------------------------------------------------------- //

    override fun onAnalyze(player: EntityPlayer, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Array<Node> =
        arrayOf(origin.node())

    override fun onRedstoneInputChanged(args: RedstoneChangedEventArgs) {
        super.onRedstoneInputChanged(args)
        val hasRedstoneInput = screens.maxOfOrNull { it.maxInput } ?: 0 > 0
        if (hasRedstoneInput != hadRedstoneInput) {
            hadRedstoneInput = hasRedstoneInput
            if (hasRedstoneInput) {
                origin.buffer.powerState = !origin.buffer.powerState
            }
        }
    }

    override fun onRotationChanged() {
        super.onRotationChanged()
        screens.toList().forEach { it.checkMultiBlock() }
    }

    // ----------------------------------------------------------------------- //

    override fun compareTo(that: Screen): Int =
        when {
            pos.x != that.pos.x -> pos.x - that.pos.x
            pos.y != that.pos.y -> pos.y - that.pos.y
            else -> pos.z - that.pos.z
        }

    // ----------------------------------------------------------------------- //

    private fun tryMerge(): Boolean {
        val opos = project(origin)
        fun tryMergeTowards(dx: Int, dy: Int): Boolean {
            val npos = unproject(opos.x + dx, opos.y + dy, opos.z)
            if (!world.blockExists(npos)) return false

            val te = world.getTileEntity(npos)
            if (te !is Screen || te.tier != tier || te.pitch != pitch ||
                te.getColor() != getColor() || te.yaw != yaw || screens.contains(te)) {
                return false
            }

            val spos = project(te.origin)
            val canMergeAlongX = spos.y == opos.y && te.height == height &&
                te.width + width <= Settings.get.maxScreenWidth
            val canMergeAlongY = spos.x == opos.x && te.width == width &&
                te.height + height <= Settings.get.maxScreenHeight

            if (!canMergeAlongX && !canMergeAlongY) return false

            val newOrigin = if (canMergeAlongX) {
                if (spos.x < opos.x) te.origin else origin
            } else {
                if (spos.y < opos.y) te.origin else origin
            }

            val (newWidth, newHeight) = if (canMergeAlongX) {
                Pair(width + te.width, height)
            } else {
                Pair(width, height + te.height)
            }

            val newScreens = screens + te.screens
            for (screen in newScreens) {
                screen.width = newWidth
                screen.height = newHeight
                screen.origin = newOrigin
                screen.screens.addAll(newScreens)
                screen.cachedBounds = null
            }
            return true
        }

        return tryMergeTowards(0, height) || tryMergeTowards(0, -1) ||
               tryMergeTowards(width, 0) || tryMergeTowards(-1, 0)
    }

    private fun project(t: Screen): BlockPos {
        fun dot(f: EnumFacing, s: Screen) = f.xOffset * s.pos.x + f.yOffset * s.pos.y + f.zOffset * s.pos.z
        return BlockPos(
            dot(toGlobal(EnumFacing.EAST), t),
            dot(toGlobal(EnumFacing.UP), t),
            dot(toGlobal(EnumFacing.SOUTH), t)
        )
    }

    private fun unproject(x: Int, y: Int, z: Int): BlockPos {
        fun dot(f: EnumFacing) = f.xOffset * x + f.yOffset * y + f.zOffset * z
        return BlockPos(
            dot(toLocal(EnumFacing.EAST)),
            dot(toLocal(EnumFacing.UP)),
            dot(toLocal(EnumFacing.SOUTH))
        )
    }
}
