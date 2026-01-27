package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.SidedEnvironment
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.SaveHandler
import li.cil.oc.common.tileentity.traits.*
import li.cil.oc.common.tileentity.traits.RotatableTile
import li.cil.oc.server.PacketSender as ServerPacketSender
import li.cil.oc.server.component.result
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import li.cil.oc.common.tileentity.traits.RotatableTile as TraitRotatableTile
import li.cil.oc.common.tileentity.traits.Tickable as TraitTickable

class Hologram @JvmOverloads constructor(
    @JvmField var tier: Int = 0
): TileEntityBase.TEEnvironmentBase(), SidedEnvironment, Analyzable, TraitRotatableTile, TraitTickable, DeviceInfo {
    @JvmField
    val node: Connector? = ApiNetwork.newNode(this, Visibility.Network)!!
        .withComponent("hologram")
        .withConnector()
        .create()
    override fun node() = node

    override val rotatableDelegate: RotatableTile.Delegate = register(RotatableTile::Delegate)


    @JvmField
    val width = 3 * 16

    @JvmField
    val height = 2 * 16 // 32 bit in an int

    private val deviceInfo_: Map<String, String> by lazy {
        mapOf(
            DeviceAttribute.Class to DeviceClass.Display,
            DeviceAttribute.Description to "Holographic projector",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "VirtualViewer H1-${tier + 1}",
            DeviceAttribute.Capacity to (width * width * height).toString(),
            DeviceAttribute.Width to colors.size.toString()
        )
    }

    override fun getDeviceInfo(): Map<String, String> = deviceInfo_

    // ----------------------------------------------------------------------- //

    // Layout is: first half is lower bit, second half is higher bit for the
    // voxels in the cube. This is to retain compatibility with pre 1.3 saves.
    @JvmField
    val volume = IntArray(width * width * 2)

    // Render scale.
    @JvmField
    var scale = 1.0

    // Projection Y position offset - consider adding X,Z later perhaps
    @JvmField
    var translation = Vec3d(0.0, 0.0, 0.0)

    // Relative number of lit columns (for energy cost).
    @JvmField
    var litRatio = -1.0

    // Whether we need to recompile our display list.
    @JvmField
    var needsRendering = false

    // Store it here for convenience, this is the number of visible voxel faces
    // as determined in the last VBO index update. See HologramRenderer.
    @JvmField
    var visibleQuads = 0

    // What parts of the hologram changed and need an update packet.
    @JvmField
    val dirty: MutableSet<Short> = mutableSetOf()

    // Interval of dirty columns.
    @JvmField
    var dirtyFromX = Int.MAX_VALUE
    @JvmField
    var dirtyUntilX = -1
    @JvmField
    var dirtyFromZ = Int.MAX_VALUE
    @JvmField
    var dirtyUntilZ = -1

    @JvmField
    var hasPower = true

    // Rotation base state. Current rotation is based on world time. See HologramRenderer.
    @JvmField
    var rotationAngle = 0f
    @JvmField
    var rotationX = 0f
    @JvmField
    var rotationY = 0f
    @JvmField
    var rotationZ = 0f
    @JvmField
    var rotationSpeed = 0f
    @JvmField
    var rotationSpeedX = 0f
    @JvmField
    var rotationSpeedY = 0f
    @JvmField
    var rotationSpeedZ = 0f

    @JvmField
    val colorsByTier = arrayOf(intArrayOf(0x00FF00), intArrayOf(0x0000FF, 0x00FF00, 0xFF0000)) // 0xBBGGRR for rendering convenience

    // This is a fun and not a val for loading (where the tier comes from the nbt and is always 0 here).
    val colors: IntArray
        get() = colorsByTier[tier]

    fun getColor(x: Int, y: Int, z: Int): Int {
        val lbit = (volume[x + z * width] ushr y) and 1
        val hbit = (volume[x + z * width + width * width] ushr y) and 1
        return lbit or (hbit shl 1)
    }

    fun setColor(x: Int, y: Int, z: Int, value: Int) {
        if ((value and 3) != getColor(x, y, z)) {
            val lbit = value and 1
            val hbit = (value ushr 1) and 1
            volume[x + z * width] = (volume[x + z * width] and (1 shl y).inv()) or (lbit shl y)
            volume[x + z * width + width * width] = (volume[x + z * width + width * width] and (1 shl y).inv()) or (hbit shl y)
            setDirty(x, z)
        }
    }

    private fun setDirty(x: Int, z: Int) {
        dirty.add(((x.toByte().toInt() shl 8) or z.toByte().toInt()).toShort())
        dirtyFromX = minOf(dirtyFromX, x)
        dirtyUntilX = maxOf(dirtyUntilX, x + 1)
        dirtyFromZ = minOf(dirtyFromZ, z)
        dirtyUntilZ = maxOf(dirtyUntilZ, z + 1)
        litRatio = -1.0
    }

    private fun resetDirtyFlag() {
        dirty.clear()
        dirtyFromX = Int.MAX_VALUE
        dirtyUntilX = -1
        dirtyFromZ = Int.MAX_VALUE
        dirtyUntilZ = -1
    }

    // ----------------------------------------------------------------------- //

    @SideOnly(Side.CLIENT)
    override fun canConnect(side: EnumFacing): Boolean = toLocal(side) == EnumFacing.DOWN

    override fun sidedNode(side: EnumFacing): Node? = if (toLocal(side) == EnumFacing.DOWN) node else null

    // Override automatic analyzer implementation for sided environments.
    override fun onAnalyze(player: EntityPlayer, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Array<Node> = arrayOf(node!!)

    // ----------------------------------------------------------------------- //

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function() -- Clears the hologram.""")
    @Synchronized
    fun clear(context: Context, args: Arguments): Array<Any?>? {
        for (i in volume.indices) volume[i] = 0
        ServerPacketSender.sendHologramClear(this)
        resetDirtyFlag()
        litRatio = 0.0
        return null
    }

    @Suppress("unused", "unused_parameter")
    @Callback(direct = true, doc = """function(x:number, y:number, z:number):number -- Returns the value for the specified voxel.""")
    @Synchronized
    fun get(context: Context, args: Arguments): Array<Any?> {
        val (x, y, z) = checkCoordinates(args)
        return result(getColor(x, y, z))
    }

    @Suppress("unused", "unused_parameter")
    @Callback(direct = true, limit = 256, doc = """function(x:number, y:number, z:number, value:number or boolean) -- Set the value for the specified voxel.""")
    @Synchronized
    fun set(context: Context, args: Arguments): Array<Any?>? {
        val (x, y, z) = checkCoordinates(args)
        val value = checkColor(args, 3)
        setColor(x, y, z, value)
        return null
    }

    @Suppress("unused", "unused_parameter")
    @Callback(direct = true, limit = 128, doc = """function(x:number, z:number[, minY:number], maxY:number, value:number or boolean) -- Fills an interval of a column with the specified value.""")
    @Synchronized
    fun fill(context: Context, args: Arguments): Array<Any?>? {
        val (x, _, z) = checkCoordinates(args, 0, -1, 1)
        val (minY, maxY, value) = if (args.count() > 4) {
            Triple(
                minOf(32, maxOf(1, args.checkInteger(2))),
                minOf(32, maxOf(1, args.checkInteger(3))),
                checkColor(args, 4)
            )
        } else {
            Triple(1, minOf(32, maxOf(1, args.checkInteger(2))), checkColor(args, 3))
        }
        if (minY > maxY) throw IllegalArgumentException("interval is empty")

        val mask = (0xFFFFFFFF.toInt() ushr (31 - (maxY - minY))) shl (minY - 1)
        val lbit = value and 1
        val hbit = (value ushr 1) and 1
        if (lbit == 0 || height == 0) volume[x + z * width] = volume[x + z * width] and mask.inv()
        else volume[x + z * width] = volume[x + z * width] or mask
        if (hbit == 0 || height == 0) volume[x + z * width + width * width] = volume[x + z * width + width * width] and mask.inv()
        else volume[x + z * width + width * width] = volume[x + z * width + width * width] or mask

        setDirty(x, z)
        return null
    }

    @Suppress("unused")
    @Callback(doc = """function(data:string) -- Set the raw buffer to the specified byte array, where each byte represents a voxel color. Nesting is x,z,y.""")
    @Synchronized
    fun setRaw(context: Context, args: Arguments): Array<Any?>? {
        val data = args.checkByteArray(0)
        for (x in 0 until width) {
            for (z in 0 until width) {
                val offset = z * height + x * height * width
                if (data.size >= offset + height) {
                    var lbit = 0
                    var hbit = 0
                    for (y in (height - 1) downTo 0) {
                        val color = data[offset + y].toInt()
                        lbit = lbit or ((color and 1) shl y)
                        hbit = hbit or (((color and 3) ushr 1) shl y)
                    }
                    val index = x + z * width
                    if (volume[index] != lbit || volume[index + width * width] != hbit) {
                        volume[index] = lbit
                        volume[index + width * width] = hbit
                        setDirty(x, z)
                    }
                }
            }
        }
        context.pause(Settings.get.hologramSetRawDelay)
        return null
    }

    @Callback(doc = """function(x:number, z:number, sx:number, sz:number, tx:number, tz:number) -- Copies an area of columns by the specified translation.""")
    @Synchronized
    fun copy(context: Context, args: Arguments): Array<Any?>? {
        val (x, _, z) = checkCoordinates(args, 0, -1, 1)
        val w = args.checkInteger(2)
        val h = args.checkInteger(3)
        val tx = args.checkInteger(4)
        val tz = args.checkInteger(5)

        // Anything to do at all?
        if (w <= 0 || h <= 0) return null
        if (tx == 0 && tz == 0) return null

        val (dx0, dx1) = run {
            val pair = Pair(maxOf(0, minOf(width - 1, x + tx + w - 1)), maxOf(0, minOf(width, x + tx)))
            if (tx > 0) pair else Pair(pair.second, pair.first)
        }
        val (dz0, dz1) = run {
            val pair = Pair(maxOf(0, minOf(width - 1, z + tz + h - 1)), maxOf(0, minOf(width, z + tz)))
            if (tz > 0) pair else Pair(pair.second, pair.first)
        }
        val (sx, sz) = Pair(if (tx > 0) -1 else 1, if (tz > 0) -1 else 1)

        var nz = dz0
        while ((sz > 0 && nz <= dz1) || (sz < 0 && nz >= dz1)) {
            val oz = nz - tz
            if (oz >= 0 && oz < width) {
                var nx = dx0
                while ((sx > 0 && nx <= dx1) || (sx < 0 && nx >= dx1)) {
                    val ox = nx - tx
                    if (ox >= 0 && ox < width) {
                        volume[nz * width + nx] = volume[oz * width + ox]
                        volume[nz * width + nx + width * width] = volume[oz * width + ox + width * width]
                        setDirty(nx, nz)
                    }
                    nx += sx
                }
            }
            nz += sz
        }

        val area = (maxOf(dx0, dx1) - minOf(dx0, dx1)) * (maxOf(dz0, dz1) - minOf(dz0, dz1))
        val relativeArea = maxOf(0f, area / (width * width).toFloat() - 0.25f)
        context.pause(relativeArea.toDouble())

        return null
    }

    @Suppress("unused", "unused_parameter")
    @Callback(direct = true, doc = """function():number -- Returns the render scale of the hologram.""")
    fun getScale(context: Context, args: Arguments): Array<Any?> = result(scale)

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function(value:number) -- Set the render scale. A larger scale consumes more energy.""")
    fun setScale(context: Context, args: Arguments): Array<Any?>? {
        scale = maxOf(0.333333, minOf(Settings.get.hologramMaxScaleByTier[tier], args.checkDouble(0)))
        ServerPacketSender.sendHologramScale(this)
        return null
    }

    @Suppress("unused", "unused_parameter")
    @Callback(direct = true, doc = """function():number, number, number -- Returns the relative render projection offsets of the hologram.""")
    fun getTranslation(context: Context, args: Arguments): Array<Any?> =
        result(translation.x, translation.y, translation.z)

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function(tx:number, ty:number, tz:number) -- Sets the relative render projection offsets of the hologram.""")
    fun setTranslation(context: Context, args: Arguments): Array<Any?>? {
        val maxTranslation = Settings.get.hologramMaxTranslationByTier[tier]
        val tx = maxOf(-maxTranslation, minOf(maxTranslation, args.checkDouble(0)))
        val ty = maxOf(0.0, minOf(maxTranslation * 2, args.checkDouble(1)))
        val tz = maxOf(-maxTranslation, minOf(maxTranslation, args.checkDouble(2)))

        translation = Vec3d(tx, ty, tz)

        ServerPacketSender.sendHologramOffset(this)
        return null
    }

    @Suppress("unused", "unused_parameter")
    @Callback(direct = true, doc = """function():number -- The color depth supported by the hologram.""")
    fun maxDepth(context: Context, args: Arguments): Array<Any?> = result(tier + 1)

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function(index:number):number -- Get the color defined for the specified value.""")
    fun getPaletteColor(context: Context, args: Arguments): Array<Any?> {
        val index = args.checkInteger(0)
        if (index < 1 || index > colors.size) throw ArrayIndexOutOfBoundsException()
        return result(convertColor(colors[index - 1]))
    }

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function(index:number, value:number):number -- Set the color defined for the specified value.""")
    fun setPaletteColor(context: Context, args: Arguments): Array<Any?> {
        val index = args.checkInteger(0)
        if (index < 1 || index > colors.size) throw ArrayIndexOutOfBoundsException()
        val value = args.checkInteger(1)
        val oldValue = colors[index - 1]
        colors[index - 1] = convertColor(value)
        ServerPacketSender.sendHologramColor(this, index - 1, colors[index - 1])
        return result(oldValue)
    }

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function(angle:number, x:number, y:number, z:number):boolean -- Set the base rotation of the displayed hologram.""")
    fun setRotation(context: Context, args: Arguments): Array<Any?> {
        return if (tier > 0) {
            val r = args.checkDouble(0) % 360
            val x = args.checkDouble(1)
            val y = args.checkDouble(2)
            val z = args.checkDouble(3)

            rotationAngle = r.toFloat()
            rotationX = x.toFloat()
            rotationY = y.toFloat()
            rotationZ = z.toFloat()
            ServerPacketSender.sendHologramRotation(this)

            result(true)
        } else {
            result(Unit, "not supported")
        }
    }

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function(speed:number, x:number, y:number, z:number):boolean -- Set the rotation speed of the displayed hologram.""")
    fun setRotationSpeed(context: Context, args: Arguments): Array<Any?> {
        return if (tier > 0) {
            val v = maxOf(-360.0 * 4, minOf(360.0 * 4, args.checkDouble(0)))
            val x = args.checkDouble(1)
            val y = args.checkDouble(2)
            val z = args.checkDouble(3)

            rotationSpeed = v.toFloat()
            rotationSpeedX = x.toFloat()
            rotationSpeedY = y.toFloat()
            rotationSpeedZ = z.toFloat()
            ServerPacketSender.sendHologramRotationSpeed(this)

            result(true)
        } else {
            result(Unit, "not supported")
        }
    }

    @Suppress("unused", "unused_parameter")
    @Callback(direct = true, doc = "function():number, number, number -- Get the dimension of the x,y,z axes.")
    fun getDimensions(context: Context, args: Arguments): Array<Any?> = result(width, height, width)

    private fun checkCoordinates(args: Arguments, idxX: Int = 0, idxY: Int = 1, idxZ: Int = 2): Triple<Int, Int, Int> {
        val x = if (idxX >= 0) args.checkInteger(idxX) - 1 else 0
        if (x < 0 || x >= width) throw ArrayIndexOutOfBoundsException("x")
        val y = if (idxY >= 0) args.checkInteger(idxY) - 1 else 0
        if (y < 0 || y >= height) throw ArrayIndexOutOfBoundsException("y")
        val z = if (idxZ >= 0) args.checkInteger(idxZ) - 1 else 0
        if (z < 0 || z >= width) throw ArrayIndexOutOfBoundsException("z")
        return Triple(x, y, z)
    }

    private fun checkColor(args: Arguments, index: Int): Int {
        val value = if (args.isBoolean(index)) {
            if (args.checkBoolean(index)) 1 else 0
        } else {
            args.checkInteger(index)
        }
        if (value < 0 || value > colors.size) throw IllegalArgumentException("invalid value")
        return value
    }

    private fun convertColor(color: Int): Int {
        return ((color and 0x0000FF) shl 16) or (color and 0x00FF00) or ((color and 0xFF0000) ushr 16)
    }

    // ----------------------------------------------------------------------- //

    override fun updateEntity() {
        super.updateEntity()
        if (isServer) {
            if (dirty.isNotEmpty()) {
                synchronized(this) {
                    val dirtySizeX = dirtyUntilX - dirtyFromX
                    val dirtySizeZ = dirtyUntilZ - dirtyFromZ
                    if (dirty.size > dirtySizeX * dirtySizeZ * 0.8) {
                        ServerPacketSender.sendHologramArea(this)
                    } else {
                        ServerPacketSender.sendHologramValues(this)
                    }
                    resetDirtyFlag()
                }
            }
            if (Settings.get.isTickMultiple(world)) {
                if (litRatio < 0) {
                    synchronized(this) {
                        litRatio = 0.0
                        for (i in volume.indices) {
                            if (volume[i] != 0) litRatio += 1
                        }
                        litRatio /= volume.size
                    }
                }

                val hadPower = hasPower
                val neededPower = Settings.get.hologramCost * litRatio * scale * Settings.get.tickFrequency
                hasPower = node!!.tryChangeBuffer(-neededPower)
                if (hasPower != hadPower) {
                    ServerPacketSender.sendHologramPowerChange(this)
                }
            }
        }
    }

    // ----------------------------------------------------------------------- //

    override fun shouldRenderInPass(pass: Int): Boolean = pass == 1

    override fun getMaxRenderDistanceSquared(): Double =
        scale / Settings.get.hologramMaxScaleByTier.max() * Settings.get.hologramRenderDistance * Settings.get.hologramRenderDistance

    fun getFadeStartDistanceSquared(): Double =
        scale / Settings.get.hologramMaxScaleByTier.max() * Settings.get.hologramFadeStartDistance * Settings.get.hologramFadeStartDistance

    companion object {
        private val Sqrt2 = Math.sqrt(2.0)

        private val TierTag = Settings.namespace + "tier"
        private const val VolumeTag = "volume"
        private const val ColorsTag = "colors"
        private val ScaleTag = Settings.namespace + "scale"
        private val OffsetXTag = Settings.namespace + "offsetX"
        private val OffsetYTag = Settings.namespace + "offsetY"
        private val OffsetZTag = Settings.namespace + "offsetZ"
        private val RotationAngleTag = Settings.namespace + "rotationAngle"
        private val RotationXTag = Settings.namespace + "rotationX"
        private val RotationYTag = Settings.namespace + "rotationY"
        private val RotationZTag = Settings.namespace + "rotationZ"
        private val RotationSpeedTag = Settings.namespace + "rotationSpeed"
        private val RotationSpeedXTag = Settings.namespace + "rotationSpeedX"
        private val RotationSpeedYTag = Settings.namespace + "rotationSpeedY"
        private val RotationSpeedZTag = Settings.namespace + "rotationSpeedZ"
        private val HasPowerTag = Settings.namespace + "hasPower"
    }

    override fun getRenderBoundingBox(): AxisAlignedBB {
        val cx = x + 0.5
        val cy = y + 0.5
        val cz = z + 0.5
        val sh = width / 16 * scale * Sqrt2
        val sv = height / 16 * scale * Sqrt2
        return AxisAlignedBB(
            cx + (-0.5 + translation.x) * sh,
            cy + translation.y * sv,
            cz + (-0.5 + translation.z) * sh,
            cx + (0.5 + translation.x) * sh,
            cy + (1 + translation.y) * sv,
            cz + (0.5 + translation.x) * sh
        )
    }

    // ----------------------------------------------------------------------- //

    private val dataPath: String
        get() = node!!.address() + "_data"

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        tier = maxOf(0, minOf(1, nbt.getByte(TierTag).toInt()))
        super.readFromNBTForServer(nbt)
        val tag = SaveHandler.loadNBT(nbt, dataPath)
        tag.getIntArray(VolumeTag).copyInto(volume)
        tag.getIntArray(ColorsTag).map { convertColor(it) }.toIntArray().copyInto(colors)
        scale = nbt.getDouble(ScaleTag)
        val tx = nbt.getDouble(OffsetXTag)
        val ty = nbt.getDouble(OffsetYTag)
        val tz = nbt.getDouble(OffsetZTag)
        translation = Vec3d(tx, ty, tz)
        rotationAngle = nbt.getFloat(RotationAngleTag)
        rotationX = nbt.getFloat(RotationXTag)
        rotationY = nbt.getFloat(RotationYTag)
        rotationZ = nbt.getFloat(RotationZTag)
        rotationSpeed = nbt.getFloat(RotationSpeedTag)
        rotationSpeedX = nbt.getFloat(RotationSpeedXTag)
        rotationSpeedY = nbt.getFloat(RotationSpeedYTag)
        rotationSpeedZ = nbt.getFloat(RotationSpeedZTag)
    }

    @Synchronized
    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        nbt.setByte(TierTag, tier.toByte())
        super.writeToNBTForServer(nbt)
        SaveHandler.scheduleSave(world, x.toDouble(), z.toDouble(), nbt, dataPath) { tag ->
            tag.setIntArray(VolumeTag, volume)
            tag.setIntArray(ColorsTag, colors.map { convertColor(it) }.toIntArray())
        }
        nbt.setDouble(ScaleTag, scale)
        nbt.setDouble(OffsetXTag, translation.x)
        nbt.setDouble(OffsetYTag, translation.y)
        nbt.setDouble(OffsetZTag, translation.z)
        nbt.setFloat(RotationAngleTag, rotationAngle)
        nbt.setFloat(RotationXTag, rotationX)
        nbt.setFloat(RotationYTag, rotationY)
        nbt.setFloat(RotationZTag, rotationZ)
        nbt.setFloat(RotationSpeedTag, rotationSpeed)
        nbt.setFloat(RotationSpeedXTag, rotationSpeedX)
        nbt.setFloat(RotationSpeedYTag, rotationSpeedY)
        nbt.setFloat(RotationSpeedZTag, rotationSpeedZ)
    }

    @SideOnly(Side.CLIENT)
    override fun readFromNBTForClient(nbt: NBTTagCompound) {
        super.readFromNBTForClient(nbt)
        nbt.getIntArray(VolumeTag).copyInto(volume)
        nbt.getIntArray(ColorsTag).copyInto(colors)
        scale = nbt.getDouble(ScaleTag)
        hasPower = nbt.getBoolean(HasPowerTag)
        val tx = nbt.getDouble(OffsetXTag)
        val ty = nbt.getDouble(OffsetYTag)
        val tz = nbt.getDouble(OffsetZTag)
        translation = Vec3d(tx, ty, tz)
        rotationAngle = nbt.getFloat(RotationAngleTag)
        rotationX = nbt.getFloat(RotationXTag)
        rotationY = nbt.getFloat(RotationYTag)
        rotationZ = nbt.getFloat(RotationZTag)
        rotationSpeed = nbt.getFloat(RotationSpeedTag)
        rotationSpeedX = nbt.getFloat(RotationSpeedXTag)
        rotationSpeedY = nbt.getFloat(RotationSpeedYTag)
        rotationSpeedZ = nbt.getFloat(RotationSpeedZTag)
    }

    override fun writeToNBTForClient(nbt: NBTTagCompound) {
        super.writeToNBTForClient(nbt)
        nbt.setIntArray(VolumeTag, volume)
        nbt.setIntArray(ColorsTag, colors)
        nbt.setDouble(ScaleTag, scale)
        nbt.setBoolean(HasPowerTag, hasPower)
        nbt.setDouble(OffsetXTag, translation.x)
        nbt.setDouble(OffsetYTag, translation.y)
        nbt.setDouble(OffsetZTag, translation.z)
        nbt.setFloat(RotationAngleTag, rotationAngle)
        nbt.setFloat(RotationXTag, rotationX)
        nbt.setFloat(RotationYTag, rotationY)
        nbt.setFloat(RotationZTag, rotationZ)
        nbt.setFloat(RotationSpeedTag, rotationSpeed)
        nbt.setFloat(RotationSpeedXTag, rotationSpeedX)
        nbt.setFloat(RotationSpeedYTag, rotationSpeedY)
        nbt.setFloat(RotationSpeedZTag, rotationSpeedZ)
    }
}
