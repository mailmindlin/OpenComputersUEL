package li.cil.oc.common.item.data

import java.lang.reflect.Method
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.*
import li.cil.oc.common.IMC
import li.cil.oc.common.item.data.ItemData
import li.cil.oc.util.surface
import li.cil.oc.util.volume
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.AxisAlignedBB
import net.minecraftforge.common.util.Constants.NBT

class PrintData() : ItemData(Constants.BlockName.Print) {
    constructor(stack: ItemStack): this() {
        load(stack)
    }

    var label: String? = null
    var tooltip: String? = null
    var isButtonMode = false
    var redstoneLevel = 0
    var pressurePlate = false
    val stateOff = mutableSetOf<Shape>()
    val stateOn = mutableSetOf<Shape>()
    var isBeaconBase = false
    var lightLevel = 0
    var noclipOff = false
    var noclipOn = false

    val complexity: Int
        get() = maxOf(stateOn.size, stateOff.size)

    val hasActiveState: Boolean
        get() = stateOn.isNotEmpty()

    val emitLight: Boolean
        get() = lightLevel > 0

    val emitRedstone: Boolean
        get() = redstoneLevel > 0

    fun emitRedstone(state: Boolean): Boolean = if (state) emitRedstoneWhenOn else emitRedstoneWhenOff

    val emitRedstoneWhenOff: Boolean
        get() = emitRedstone && !hasActiveState

    val emitRedstoneWhenOn: Boolean
        get() = emitRedstone && hasActiveState

    val opacity: Float
        get() {
            if (opacityDirty) {
                opacityDirty = false
                _opacity = minOf(computeApproximateOpacity(stateOn), computeApproximateOpacity(stateOff))
            }
            return _opacity
        }

    // lazily computed and stored, because potentially slow
    private var _opacity = 0f
    private var opacityDirty = true

    private val LabelTag = "label"
    private val TooltipTag = "tooltip"
    private val IsButtonModeTag = "isButtonMode"
    private val RedstoneLevelTag = "redstoneLevel"
    private val RedstoneLevelTagCompat = "emitRedstone"
    private val PressurePlateTag = "pressurePlate"
    private val StateOffTag = "stateOff"
    private val StateOnTag = "stateOn"
    private val IsBeaconBaseTag = "isBeaconBase"
    private val LightLevelTag = "lightLevel"
    private val NoclipOffTag = "noclipOff"
    private val NoclipOnTag = "noclipOn"

    override fun load(nbt: NBTTagCompound) {
        label = if (nbt.hasKey(LabelTag)) nbt.getString(LabelTag) else null
        tooltip = if (nbt.hasKey(TooltipTag)) nbt.getString(TooltipTag) else null
        isButtonMode = nbt.getBoolean(IsButtonModeTag)
        redstoneLevel = nbt.getInteger(RedstoneLevelTag).coerceIn(0, 15)
        if (nbt.getBoolean(RedstoneLevelTagCompat)) redstoneLevel = 15
        pressurePlate = nbt.getBoolean(PressurePlateTag)
        stateOff.clear()
        stateOff.addAll(nbt.getTagList(StateOffTag, NBT.TAG_COMPOUND).map { nbtToShape(it as NBTTagCompound) })
        stateOn.clear()
        stateOn.addAll(nbt.getTagList(StateOnTag, NBT.TAG_COMPOUND).map { nbtToShape(it as NBTTagCompound) })
        isBeaconBase = nbt.getBoolean(IsBeaconBaseTag)
        lightLevel = (nbt.getByte(LightLevelTag).toInt() and 0xFF).coerceIn(0, 15)
        noclipOff = nbt.getBoolean(NoclipOffTag)
        noclipOn = nbt.getBoolean(NoclipOnTag)

        opacityDirty = true
    }

    override fun save(nbt: NBTTagCompound) {
        label?.let { nbt.setString("label", it) }
        tooltip?.let { nbt.setString("tooltip", it) }
        nbt.setBoolean("isButtonMode", isButtonMode)
        nbt.setInteger("redstoneLevel", redstoneLevel)
        nbt.setBoolean("pressurePlate", pressurePlate)
        setNewShapeSet(nbt, StateOffTag, stateOff)
        setNewShapeSet(nbt, StateOnTag, stateOn)
        nbt.setBoolean("isBeaconBase", isBeaconBase)
        nbt.setByte("lightLevel", lightLevel.toByte())
        nbt.setBoolean("noclipOff", noclipOff)
        nbt.setBoolean("noclipOn", noclipOn)
    }

    // Shapes are stored in a set and sets do not have an order, that means NBT shape lists may be in any order.
    // Because NBT list comparison considers order of tags in a list, and prints may have arbitrarily ordered list of shapes,
    // the comparison fails and minecraft considers two identical prints different.
    // One possible solution is to sort the shapes before serializing them to NBT
    private fun setNewShapeSet(nbt: NBTTagCompound, name: String, values: Iterable<Shape>) {
        val seq = values.sortedWith(::compareShape)
        nbt.setNewTagList(name, seq.map(::shapeToNBT))
    }

    private fun compareShape(a: Shape, b: Shape): Int {
        if (a.bounds.minX != b.bounds.minX) return if (a.bounds.minX > b.bounds.minX) -1 else 1
        if (a.bounds.minY != b.bounds.minY) return if (a.bounds.minY > b.bounds.minY) -1 else 1
        if (a.bounds.minZ != b.bounds.minZ) return if (a.bounds.minZ > b.bounds.minZ) -1 else 1
        if (a.bounds.maxX != b.bounds.maxX) return if (a.bounds.maxX > b.bounds.maxX) -1 else 1
        if (a.bounds.maxY != b.bounds.maxY) return if (a.bounds.maxY > b.bounds.maxY) -1 else 1
        if (a.bounds.maxZ != b.bounds.maxZ) return if (a.bounds.maxZ > b.bounds.maxZ) -1 else 1
        if (a.tint != b.tint) {
            val aTint = a.tint ?: Int.MIN_VALUE
            val bTint = b.tint ?: Int.MIN_VALUE
            return if (aTint > bTint) -1 else 1
        }
        if (a.texture != b.texture) return if (a.texture > b.texture) -1 else 1
        return 0
    }

    class Shape(val bounds: AxisAlignedBB, val texture: String, val tint: Int?)

    companion object {
        // The following logic is used to approximate the opacity of a print, for
        // which we use the volume as a heuristic. Because computing the actual
        // volume is a) expensive b) not necessarily a good heuristic (e.g. a
        // "dotted grid") we take a shortcut and divide the space into a few
        // sub-sections, for each of which we check if there's anything in it.
        // If so, we consider that area "opaque". To compensate, prints can never
        // be fully light-opaque. This gives a little bit of shading as a nice
        // effect, but avoid it looking derpy when there are only a few sparse
        // shapes in the model.
        private const val stepping = 4
        private const val step = stepping / 16f
        private const val invMaxVolume = 1f / (stepping * stepping * stepping)

        private val inkProviders = linkedSetOf<Method>()

        @JvmStatic
        fun addInkProvider(provider: Method) {
            inkProviders.add(provider)
        }

        @JvmStatic
        fun computeApproximateOpacity(shapes: Iterable<Shape>): Float {
            var volume = 1f
            if (shapes.any()) {
                for (x in 0 until 16 / stepping) {
                    for (y in 0 until 16 / stepping) {
                        for (z in 0 until 16 / stepping) {
                            val bounds = AxisAlignedBB(
                                (x * step).toDouble(), (y * step).toDouble(), (z * step).toDouble(),
                                ((x + 1) * step).toDouble(), ((y + 1) * step).toDouble(), ((z + 1) * step).toDouble()
                            )
                            if (!shapes.any { it.bounds.intersects(bounds) }) {
                                volume -= invMaxVolume
                            }
                        }
                    }
                }
            }
            return volume
        }

        @JvmStatic
        fun computeCosts(data: PrintData): Pair<Int, Int>? {
            val totalVolume = data.stateOn.fold(0) { acc, shape -> acc + shape.bounds.volume } +
                data.stateOff.fold(0) { acc, shape -> acc + shape.bounds.volume }
            val totalSurface = data.stateOn.fold(0) { acc, shape -> acc + shape.bounds.surface } +
                data.stateOff.fold(0) { acc, shape -> acc + shape.bounds.surface }
            val multiplier = if (data.noclipOff || data.noclipOn) Settings.get.noclipMultiplier else 1.0

            return if (totalVolume > 0) {
                val baseMaterialRequired = maxOf(totalVolume / 2, 1)
                val materialRequired = if (data.redstoneLevel > 0 && data.redstoneLevel < 15) {
                    baseMaterialRequired + Settings.get.printCustomRedstone
                } else {
                    baseMaterialRequired
                }
                val inkRequired = maxOf(totalSurface / 6, 1)

                Pair((materialRequired * multiplier).toInt(), inkRequired)
            } else null
        }

        private val materialPerItem: Int
            get() = Settings.get.printMaterialValue

        @JvmStatic
        fun materialValue(stack: ItemStack): Int {
            return when {
                api.Items.get(stack) == api.Items.get(Constants.ItemName.Chamelium) -> materialPerItem
                api.Items.get(stack) == api.Items.get(Constants.BlockName.Print) -> {
                    val data = PrintData(stack)
                    val costs = computeCosts(data)
                    if (costs != null) {
                        (costs.first * Settings.get.printRecycleRate).toInt()
                    } else 0
                }
                else -> 0
            }
        }

        @JvmStatic
        fun inkValue(stack: ItemStack): Int {
            for (provider in inkProviders) {
                val value = IMC.tryInvokeStatic(provider, stack, default = 0)
                if (value > 0) {
                    return value
                }
            }
            return 0
        }

        @JvmStatic
        fun nbtToShape(nbt: NBTTagCompound): Shape {
            val aabb = if (nbt.hasKey("minX")) {
                // Compatibility with shapes created with earlier dev-builds.
                val minX = nbt.getByte("minX") / 16.0
                val minY = nbt.getByte("minY") / 16.0
                val minZ = nbt.getByte("minZ") / 16.0
                val maxX = nbt.getByte("maxX") / 16.0
                val maxY = nbt.getByte("maxY") / 16.0
                val maxZ = nbt.getByte("maxZ") / 16.0
                AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ)
            } else {
                val bounds = nbt.getByteArray("bounds").let { arr ->
                    if (arr.size < 6) ByteArray(6) else arr
                }
                val minX = bounds[0] / 16.0
                val minY = bounds[1] / 16.0
                val minZ = bounds[2] / 16.0
                val maxX = bounds[3] / 16.0
                val maxY = bounds[4] / 16.0
                val maxZ = bounds[5] / 16.0
                AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ)
            }
            val texture = nbt.getString("texture")
            val tint = if (nbt.hasKey("tint")) nbt.getInteger("tint") else null
            return Shape(aabb, texture, tint)
        }

        @JvmStatic
        fun shapeToNBT(shape: Shape): NBTTagCompound {
            val nbt = NBTTagCompound()
            nbt.setByteArray("bounds", byteArrayOf(
                (shape.bounds.minX * 16).toInt().toByte(),
                (shape.bounds.minY * 16).toInt().toByte(),
                (shape.bounds.minZ * 16).toInt().toByte(),
                (shape.bounds.maxX * 16).toInt().toByte(),
                (shape.bounds.maxY * 16).toInt().toByte(),
                (shape.bounds.maxZ * 16).toInt().toByte()
            ))
            nbt.setString("texture", shape.texture)
            shape.tint?.let { nbt.setInteger("tint", it) }
            return nbt
        }
    }
}
