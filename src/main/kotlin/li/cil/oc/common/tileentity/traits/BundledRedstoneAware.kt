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

    class Delegate(te: BundledRedstoneAware): RedstoneAware.Delegate(te) {
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

    /*fun getBundledInput(): Array<IntArray> {
        return Array(6) { side ->
            IntArray(16) { color ->
                maxOf(_bundledInput[side][color], _rednetInput[side][color], 0)
            }
        }
    }

    private fun checkSide(side: EnumFacing): Int {
        val index = side.ordinal
        if (index >= 6) throw IndexOutOfBoundsException("Bad side $side")
        return index
    }

    private fun checkColor(color: Int): Int {
        if (color < 0 || color >= 16) throw IndexOutOfBoundsException("Bad color $color")
        return color
    }

    fun getBundledInput(side: EnumFacing): IntArray {
        val sideIndex = checkSide(side)
        val bundled = _bundledInput[sideIndex]
        val rednet = _rednetInput[sideIndex]
        return IntArray(16) { i -> maxOf(bundled[i], rednet[i], 0) }
    }

    fun getBundledInput(side: EnumFacing, color: Int): Int {
        val sideIndex = checkSide(side)
        val colorIndex = checkColor(color)
        val bundled = _bundledInput[sideIndex][colorIndex]
        val rednet = _rednetInput[sideIndex][colorIndex]
        return maxOf(bundled, rednet, 0)
    }

    fun setBundledInput(side: EnumFacing, color: Int, newValue: Int) {
        updateInput(_bundledInput, side, color, newValue)
    }

    fun setBundledInput(side: EnumFacing, newBundledInput: IntArray?) {
        for (color in 0 until 16) {
            val value = if (newBundledInput == null || color >= newBundledInput.size) 0 else newBundledInput[color]
            setBundledInput(side, color, value)
        }
    }

    fun setRednetInput(side: EnumFacing, color: Int, value: Int) {
        updateInput(_rednetInput, side, color, value)
    }

    fun updateInput(inputs: Array<IntArray>, side: EnumFacing, color: Int, newValue: Int) {
        val sideIndex = checkSide(side)
        val colorIndex = checkColor(color)
        val oldValue = inputs[sideIndex][colorIndex]
        if (oldValue != newValue) {
            inputs[sideIndex][colorIndex] = newValue
            if (oldValue != -1) {
                onRedstoneInputChanged(RedstoneChangedEventArgs(side, oldValue, newValue, colorIndex))
            }
        }
    }

    fun getBundledOutput(): Array<IntArray> = _bundledInput

    fun getBundledOutput(side: EnumFacing): IntArray = _bundledOutput[checkSide(toLocal(side))]

    fun getBundledOutput(side: EnumFacing, color: Int): Int = getBundledOutput(side)[checkColor(color)]

    fun setBundledOutput(side: EnumFacing, color: Int, value: Int): Boolean {
        if (value != getBundledOutput(side, color)) {
            _bundledOutput[checkSide(toLocal(side))][checkColor(color)] = value
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
                _bundledOutput[sideIndex][color] = newValue
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

    // ----------------------------------------------------------------------- //

    // Note: updateRedstoneInput override for bundled input is handled by Scala integration

    // ----------------------------------------------------------------------- //

    // Note: Capability handling for Charset integration is done via Scala mixin*/
}
