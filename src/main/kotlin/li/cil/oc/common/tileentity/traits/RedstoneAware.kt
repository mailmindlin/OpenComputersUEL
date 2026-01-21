package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.common.EventHandler
import li.cil.oc.integration.util.BundledRedstone
import li.cil.oc.server.PacketSender as ServerPacketSender
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

data class RedstoneChangedEventArgs(val side: EnumFacing, val oldValue: Int, val newValue: Int, val color: Int = -1)

/**
 * Abstract base class for tile entities that interact with redstone.
 * Extends Environment to participate in the OC network.
 */
abstract class RedstoneAware : Environment(), RotationAware {
    protected val _input: IntArray = IntArray(6) { -1 }

    protected val _output: IntArray = IntArray(6) { 0 }

    protected var _isOutputEnabled: Boolean = false

    protected var shouldUpdateInput = true

    open val isOutputEnabled: Boolean get() = _isOutputEnabled

    open fun setOutputEnabled(value: Boolean): RedstoneAware {
        if (value != _isOutputEnabled) {
            _isOutputEnabled = value
            if (!value) {
                for (i in _output.indices) {
                    _output[i] = 0
                }
            }
            onRedstoneOutputEnabledChanged()
        }
        return this
    }

    protected fun getObjectFuzzy(map: Map<*, *>, key: Int): Any? {
        return when {
            map.containsKey(key) -> map[key]
            map.containsKey(key as Any) -> map[key as Any]
            map.containsKey(key.toDouble()) -> map[key.toDouble()]
            else -> null
        }
    }

    protected fun valueToInt(value: Any?): Int? {
        return when (value) {
            is Number -> value.toInt()
            else -> null
        }
    }

    fun getInput(): IntArray = _input.map { maxOf(it, 0) }.toIntArray()

    fun getInput(side: EnumFacing): Int = maxOf(_input[side.ordinal], 0)

    fun setInput(side: EnumFacing, newInput: Int) {
        val oldInput = _input[side.ordinal]
        _input[side.ordinal] = newInput
        if (oldInput >= 0 && newInput != oldInput) {
            onRedstoneInputChanged(RedstoneChangedEventArgs(side, oldInput, newInput))
        }
    }

    fun setInput(values: IntArray) {
        for (side in EnumFacing.values()) {
            val value = if (side.ordinal < values.size) values[side.ordinal] else 0
            setInput(side, value)
        }
    }

    fun maxInput(): Int = _input.map { maxOf(it, 0) }.maxOrNull() ?: 0

    fun getOutput(): IntArray = EnumFacing.values().map { side -> _output[toLocal(side).ordinal] }.toIntArray()

    fun getOutput(side: EnumFacing): Int {
        val localSide = toLocal(side)
        return if (_output.size > localSide.ordinal) _output[localSide.ordinal] else 0
    }

    fun setOutput(side: EnumFacing, value: Int): Boolean {
        if (value == getOutput(side)) return false
        _output[toLocal(side).ordinal] = value
        onRedstoneOutputChanged(side)
        return true
    }

    fun setOutput(values: Map<*, *>): Boolean {
        var changed = false
        for (side in EnumFacing.values()) {
            val sideIndex = toLocal(side).ordinal
            val newValue = valueToInt(getObjectFuzzy(values, sideIndex))
            if (newValue != null && setOutput(side, newValue)) {
                changed = true
            }
        }
        return changed
    }

    fun checkRedstoneInputChanged() {
        if (this is Tickable) {
            shouldUpdateInput = isServer
        } else {
            for (side in EnumFacing.values()) {
                updateRedstoneInput(side)
            }
        }
    }

    // ----------------------------------------------------------------------- //

    override fun updateEntity() {
        super.updateEntity()
        if (isServer) {
            if (shouldUpdateInput) {
                shouldUpdateInput = false
                for (side in EnumFacing.values()) {
                    updateRedstoneInput(side)
                }
            }
        }
    }

    override fun initialize() {
        super.initialize()
        if (this !is Tickable && isServer) {
            EventHandler.scheduleServer {
                for (side in EnumFacing.values()) {
                    updateRedstoneInput(side)
                }
            }
        }
    }

    open fun updateRedstoneInput(side: EnumFacing) {
        setInput(side, BundledRedstone.computeInput(position, side))
    }

    // ----------------------------------------------------------------------- //

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)

        val input = nbt.getIntArray(Settings.namespace + "rs.input")
        input.copyInto(_input, 0, 0, minOf(input.size, _input.size))
        val output = nbt.getIntArray(Settings.namespace + "rs.output")
        output.copyInto(_output, 0, 0, minOf(output.size, _output.size))
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)

        nbt.setIntArray(Settings.namespace + "rs.input", _input)
        nbt.setIntArray(Settings.namespace + "rs.output", _output)
    }

    @SideOnly(Side.CLIENT)
    override fun readFromNBTForClient(nbt: NBTTagCompound) {
        super.readFromNBTForClient(nbt)
        _isOutputEnabled = nbt.getBoolean("isOutputEnabled")
        nbt.getIntArray("output").copyInto(_output)
    }

    override fun writeToNBTForClient(nbt: NBTTagCompound) {
        super.writeToNBTForClient(nbt)
        nbt.setBoolean("isOutputEnabled", _isOutputEnabled)
        nbt.setIntArray("output", _output)
    }

    // ----------------------------------------------------------------------- //

    protected open fun onRedstoneInputChanged(args: RedstoneChangedEventArgs) {}

    protected open fun onRedstoneOutputEnabledChanged() {
        val w = world ?: return
        w.notifyNeighborsOfStateChange(pos, blockType, true)
        if (isServer) ServerPacketSender.sendRedstoneState(this)
        else w.notifyBlockUpdate(pos, w.getBlockState(pos), w.getBlockState(pos), 3)
    }

    protected open fun onRedstoneOutputChanged(side: EnumFacing) {
        val w = world ?: return
        val blockPos = pos.offset(side)
        w.neighborChanged(blockPos, blockType, blockPos)
        w.notifyNeighborsOfStateExcept(blockPos, w.getBlockState(blockPos).block, side.opposite)

        if (isServer) ServerPacketSender.sendRedstoneState(this)
        else w.notifyBlockUpdate(pos, w.getBlockState(pos), w.getBlockState(pos), 3)
    }
}
