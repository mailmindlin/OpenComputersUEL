package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.util.setNewTagList
import li.cil.oc.util.toNbt
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagIntArray
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.util.Constants

class SidedArray<T> internal constructor(private val value: Array<T>): Iterable<T> {
    init {
        assert(value.size == 6)
    }

    override fun iterator(): Iterator<T> = value.iterator()

    operator fun get(side: EnumFacing): T = this.value[side.index]
    operator fun get(side: Int): T = this.value[side]
    operator fun set(side: EnumFacing, value: T) {
        this.value[side.index] = value
    }
    operator fun set(side: Int, value: T) {
        this.value[side] = value
    }
    val size: Int get() = 6
    val indices: IntRange get() = 0 until 6
    companion object {
        internal inline operator fun <reified T> invoke(value: T): SidedArray<T> = SidedArray(arrayOf(value, value, value, value, value, value, ))
        internal inline operator fun <reified T> invoke(crossinline f: (EnumFacing) -> T): SidedArray<T> = SidedArray(Array(6) { f(EnumFacing.values()[it]) })
    }
}

/**
 * Abstract base class for tile entities that interact with bundled redstone.
 * Extends RedstoneAware with support for 16-color bundled cables.
 *
 * Note: ProjectRed and Charset integration is handled via Scala integration modules.
 */
interface BundledRedstoneAware : RedstoneAware {
    override val redstoneDelegate: Delegate

    open class Delegate(te: BundledRedstoneAware): RedstoneAware.Delegate(te) {
        internal val bundledInput: SidedArray<IntArray> = SidedArray { IntArray(16) { -1 } }
        internal val rednetInput: SidedArray<IntArray> = SidedArray { IntArray(16) { -1 } }
        internal val bundledOutput: SidedArray<IntArray> = SidedArray { IntArray(16) { 0 } }

        companion object {
            private const val BundledInputTag = Settings.namespace + "rs.bundledInput"
            private const val BundledOutputTag = Settings.namespace + "rs.bundledOutput"
            private const val RednetInputTag = Settings.namespace + "rs.rednetInput"
        }

        override fun readFromNBTForServer(nbt: NBTTagCompound) {
            super.readFromNBTForServer(nbt)
            val bundledInputList = nbt.getTagList(BundledInputTag, Constants.NBT.TAG_INT_ARRAY)
            for (index in 0 until bundledInputList.tagCount()) {
                if (index < bundledInput.size) {
                    val input = (bundledInputList.get(index) as NBTTagIntArray).intArray
                    val safeLength = minOf(input.size, bundledInput[index].size)
                    input.copyInto(bundledInput[index], 0, 0, safeLength)
                }
            }

            val bundledOutputList = nbt.getTagList(BundledOutputTag, Constants.NBT.TAG_INT_ARRAY)
            for (index in 0 until bundledOutputList.tagCount()) {
                if (index < bundledOutput.size) {
                    val output = (bundledOutputList.get(index) as NBTTagIntArray).intArray
                    val safeLength = minOf(output.size, bundledOutput[index].size)
                    output.copyInto(bundledOutput[index], 0, 0, safeLength)
                }
            }

            val rednetInputList = nbt.getTagList(RednetInputTag, Constants.NBT.TAG_INT_ARRAY)
            for (index in 0 until rednetInputList.tagCount()) {
                if (index < rednetInput.size) {
                    val input = (rednetInputList.get(index) as NBTTagIntArray).intArray
                    val safeLength = minOf(input.size, rednetInput[index].size)
                    input.copyInto(rednetInput[index], 0, 0, safeLength)
                }
            }
        }

        override fun writeToNBTForServer(nbt: NBTTagCompound) {
            super.writeToNBTForServer(nbt)

            nbt.setNewTagList(BundledInputTag, bundledInput.map { it.toNbt() })
            nbt.setNewTagList(BundledOutputTag, bundledOutput.map { it.toNbt() })
            nbt.setNewTagList(RednetInputTag, rednetInput.map { it.toNbt() })
        }
    }

    // ----------------------------------------------------------------------- //

    override var outputEnabled: Boolean
        get() = super.outputEnabled
        set(value) {
            val delegate = redstoneDelegate
            if (!value && delegate._isOutputEnabled) {
                // Falling edge
                for (i in delegate.bundledOutput.indices) {
                    delegate.bundledOutput[i].fill(0)
                }
            }
            super.outputEnabled = value
        }

    fun getBundledInput(): Array<IntArray> {
        val delegate = redstoneDelegate
        return Array(6) { side ->
            IntArray(16) { color ->
                maxOf(delegate.bundledInput[side][color], delegate.rednetInput[side][color], 0)
            }
        }
    }

    private fun checkBundledSide(side: EnumFacing): Int {
        val index = side.ordinal
        if (index >= 6) throw IndexOutOfBoundsException("Bad side $side")
        return index
    }

    private fun checkColor(color: Int): Int {
        if (color < 0 || color >= 16) throw IndexOutOfBoundsException("Bad color $color")
        return color
    }

    fun getBundledInput(side: EnumFacing): IntArray {
        val delegate = redstoneDelegate
        val sideIndex = checkBundledSide(side)
        val bundled = delegate.bundledInput[sideIndex]
        val rednet = delegate.rednetInput[sideIndex]
        return IntArray(16) { i -> maxOf(bundled[i], rednet[i], 0) }
    }

    fun getBundledInput(side: EnumFacing, color: Int): Int {
        val delegate = redstoneDelegate
        val sideIndex = checkBundledSide(side)
        val colorIndex = checkColor(color)
        val bundled = delegate.bundledInput[sideIndex][colorIndex]
        val rednet = delegate.rednetInput[sideIndex][colorIndex]
        return maxOf(bundled, rednet, 0)
    }

    fun setBundledInput(side: EnumFacing, color: Int, newValue: Int) {
        updateBundledInput(redstoneDelegate.bundledInput, side, color, newValue)
    }

    fun setBundledInput(side: EnumFacing, newBundledInput: IntArray?) {
        for (color in 0 until 16) {
            val value = if (newBundledInput == null || color >= newBundledInput.size) 0 else newBundledInput[color]
            setBundledInput(side, color, value)
        }
    }

    fun setRednetInput(side: EnumFacing, color: Int, value: Int) {
        updateBundledInput(redstoneDelegate.rednetInput, side, color, value)
    }

    fun updateBundledInput(inputs: SidedArray<IntArray>, side: EnumFacing, color: Int, newValue: Int) {
        val sideIndex = checkBundledSide(side)
        val colorIndex = checkColor(color)
        val oldValue = inputs[sideIndex][colorIndex]
        if (oldValue != newValue) {
            inputs[sideIndex][colorIndex] = newValue
            if (oldValue != -1) {
                onRedstoneInputChanged(RedstoneChangedEventArgs(side, oldValue, newValue, colorIndex))
            }
        }
    }

    fun getBundledOutput(): Array<IntArray> = Array(6) { redstoneDelegate.bundledOutput[it].copyOf() }

    fun getBundledOutput(side: EnumFacing): IntArray = redstoneDelegate.bundledOutput[checkBundledSide(toLocal(side))].copyOf()

    fun getBundledOutput(side: EnumFacing, color: Int): Int = redstoneDelegate.bundledOutput[checkBundledSide(toLocal(side))][checkColor(color)]

    fun setBundledOutput(side: EnumFacing, color: Int, value: Int): Boolean {
        if (value != getBundledOutput(side, color)) {
            redstoneDelegate.bundledOutput[checkBundledSide(toLocal(side))][checkColor(color)] = value
            onRedstoneOutputChanged(side)
            return true
        }
        return false
    }

    fun setBundledOutput(side: EnumFacing, values: Map<*, *>): Boolean {
        val sideIndex = toLocal(side).ordinal
        var changed = false
        for (color in 0 until 16) {
            val newValue = valueToInt(getObjectFuzzy(values, color))
            if (newValue != null && newValue != getBundledOutput(side, color)) {
                redstoneDelegate.bundledOutput[sideIndex][color] = newValue
                changed = true
            }
        }
        if (changed) {
            onRedstoneOutputChanged(side)
        }
        return changed
    }

    fun setBundledOutput(values: Map<*, *>): Boolean {
        var changed = false
        for (side in EnumFacing.values()) {
            val sideIndex = toLocal(side).ordinal
            val child = getObjectFuzzy(values, sideIndex)
            if (child is Map<*, *> && setBundledOutput(side, child)) {
                changed = true
            }
        }
        return changed
    }

    private fun getObjectFuzzy(map: Map<*, *>, key: Int): Any? {
        val keyAsAny: Any = key
        return when {
            map.containsKey(key) -> map[key]
            map.containsKey(keyAsAny) -> map[keyAsAny]
            map.containsKey(key.toDouble()) -> map[key.toDouble()]
            else -> null
        }
    }

    private fun valueToInt(value: Any?): Int? {
        return when (value) {
            is Number -> value.toInt()
            else -> null
        }
    }
}
