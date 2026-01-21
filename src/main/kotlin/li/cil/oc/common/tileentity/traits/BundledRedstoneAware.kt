package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.util.setNewTagList
import li.cil.oc.util.toNbt
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagIntArray
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.util.Constants.NBT

/**
 * Abstract base class for tile entities that interact with bundled redstone.
 * Extends RedstoneAware with support for 16-color bundled cables.
 *
 * Note: ProjectRed and Charset integration is handled via Scala integration modules.
 */
abstract class BundledRedstoneAware : RedstoneAware() {

    protected val _bundledInput: Array<IntArray> = Array(6) { IntArray(16) { -1 } }

    protected val _rednetInput: Array<IntArray> = Array(6) { IntArray(16) { -1 } }

    protected val _bundledOutput: Array<IntArray> = Array(6) { IntArray(16) { 0 } }

    // ----------------------------------------------------------------------- //

    override fun setOutputEnabled(value: Boolean): RedstoneAware {
        if (value != _isOutputEnabled) {
            if (!value) {
                for (i in _bundledOutput.indices) {
                    for (j in _bundledOutput[i].indices) {
                        _bundledOutput[i][j] = 0
                    }
                }
            }
        }
        return super.setOutputEnabled(value)
    }

    fun getBundledInput(): Array<IntArray> {
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

    companion object {
        private val BundledInputTag = Settings.namespace + "rs.bundledInput"
        private val BundledOutputTag = Settings.namespace + "rs.bundledOutput"
        private val RednetInputTag = Settings.namespace + "rs.rednetInput"
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)

        val bundledInputList = nbt.getTagList(BundledInputTag, NBT.TAG_INT_ARRAY)
        for (index in 0 until bundledInputList.tagCount()) {
            if (index < _bundledInput.size) {
                val input = (bundledInputList.get(index) as NBTTagIntArray).intArray
                val safeLength = minOf(input.size, _bundledInput[index].size)
                input.copyInto(_bundledInput[index], 0, 0, safeLength)
            }
        }

        val bundledOutputList = nbt.getTagList(BundledOutputTag, NBT.TAG_INT_ARRAY)
        for (index in 0 until bundledOutputList.tagCount()) {
            if (index < _bundledOutput.size) {
                val output = (bundledOutputList.get(index) as NBTTagIntArray).intArray
                val safeLength = minOf(output.size, _bundledOutput[index].size)
                output.copyInto(_bundledOutput[index], 0, 0, safeLength)
            }
        }

        val rednetInputList = nbt.getTagList(RednetInputTag, NBT.TAG_INT_ARRAY)
        for (index in 0 until rednetInputList.tagCount()) {
            if (index < _rednetInput.size) {
                val input = (rednetInputList.get(index) as NBTTagIntArray).intArray
                val safeLength = minOf(input.size, _rednetInput[index].size)
                input.copyInto(_rednetInput[index], 0, 0, safeLength)
            }
        }
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)

        nbt.setNewTagList(BundledInputTag, _bundledInput.map { it.toNbt() })
        nbt.setNewTagList(BundledOutputTag, _bundledOutput.map { it.toNbt() })
        nbt.setNewTagList(RednetInputTag, _rednetInput.map { it.toNbt() })
    }

    // Note: Capability handling for Charset integration is done via Scala mixin
}
