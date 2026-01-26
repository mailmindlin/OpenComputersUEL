package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.common.tileentity.behaviors.Behavior
import li.cil.oc.common.tileentity.behaviors.NbtSeriailzable
import li.cil.oc.integration.util.BundledRedstone
import li.cil.oc.server.PacketSender
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

data class RedstoneChangedEventArgs(val side: EnumFacing?, val oldValue: Int, val newValue: Int, val color: Int = -1)

@JvmInline
value class RedstoneValues private constructor(val values: IntArray) {
    constructor(value: Int): this(IntArray(6) { value })
    constructor(): this(-1)
    init {
        assert(values.size == 6)
    }
    operator fun get(side: EnumFacing): Int = this.values[side.ordinal]
    operator fun set(side: EnumFacing, value: Int) {
        this.values[side.ordinal] = value
    }
    fun fill(value: Int) {
        this.values.fill(value)
    }
}

/**
 * Abstract base class for tile entities that interact with redstone.
 * Extends Environment to participate in the OC network.
 */
interface RedstoneAware : Environment, RotationAware {
    val redstoneDelegate: Delegate

    open class Delegate(): Behavior, NbtSeriailzable {
        constructor(te: RedstoneAware): this()

        internal var input: RedstoneValues = RedstoneValues()
        internal var output: RedstoneValues = RedstoneValues()
        internal var _isOutputEnabled: Boolean = false
        internal var shouldUpdateInput = true

        override fun readFromNBTForServer(nbt: NBTTagCompound) {
            super.readFromNBTForServer(nbt)

            val input = nbt.getIntArray(Settings.namespace + "rs.input")
            input.copyInto(this.input.values, 0, 0, minOf(input.size, this.input.values.size))
            val output = nbt.getIntArray(Settings.namespace + "rs.output")
            output.copyInto(this.output.values, 0, 0, minOf(output.size, this.output.values.size))
        }

        override fun writeToNBTForServer(nbt: NBTTagCompound) {
            super.writeToNBTForServer(nbt)

            nbt.setIntArray(Settings.namespace + "rs.input", this.input.values)
            nbt.setIntArray(Settings.namespace + "rs.output", this.output.values)
        }

        @SideOnly(Side.CLIENT)
        override fun readFromNBTForClient(nbt: NBTTagCompound) {
            super.readFromNBTForClient(nbt)
            _isOutputEnabled = nbt.getBoolean("isOutputEnabled")
            nbt.getIntArray("output").copyInto(output.values)
        }

        override fun writeToNBTForClient(nbt: NBTTagCompound) {
            super.writeToNBTForClient(nbt)
            nbt.setBoolean("isOutputEnabled", _isOutputEnabled)
            nbt.setIntArray("output", output.values)
        }
    }

    var outputEnabled: Boolean
        get() = redstoneDelegate._isOutputEnabled
        set(value) {
            val delegate = redstoneDelegate
            if (value != delegate._isOutputEnabled) {
                delegate._isOutputEnabled = value
                if (!value)
                    delegate.output.fill(0)
                onRedstoneOutputEnabledChanged()
            }
        }

    fun getOutput(side: EnumFacing): Int = redstoneDelegate.output[side]

    fun getOutput(): IntArray = EnumFacing.values().map { side ->
        redstoneDelegate.output[toLocal(side)]
    }.toIntArray()

    fun setOutput(side: EnumFacing, value: Int): Boolean {
        if (value == getOutput(side)) return false
        val localSide = toLocal(side)
        redstoneDelegate.output[localSide] = value
        onRedstoneOutputChanged(side)
        return true
    }

    fun setOutput(values: RedstoneValues): Boolean {
        var changed = false
        for (side in EnumFacing.values())
            if (setOutput(side, values[side]))
                changed = true
        return changed
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

    fun getInput(): IntArray = redstoneDelegate.input.values.map { maxOf(it, 0) }.toIntArray()

    fun getInput(side: EnumFacing): Int = maxOf(redstoneDelegate.input[side], 0)

    fun setInput(side: EnumFacing, newInput: Int) {
        val delegate = redstoneDelegate
        val oldInput = delegate.input[side]
        delegate.input[side] = newInput
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

    val maxInput: Int
        get() = this.redstoneDelegate.input.values.max()

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

    fun checkRedstoneInputChanged() {
        if (this is Tickable) {
            redstoneDelegate.shouldUpdateInput = isServer
        } else {
            for (side in EnumFacing.values()) {
                updateRedstoneInput(side)
            }
        }
    }

    // ----------------------------------------------------------------------- //

    /*override fun updateEntity() {
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
    }*/

    fun updateRedstoneInput(side: EnumFacing) {
//        setInput(side, BundledRedstone.computeInput(position, side))
        TODO()
    }

    // ----------------------------------------------------------------------- //


    // ----------------------------------------------------------------------- //

    fun onRedstoneInputChanged(args: RedstoneChangedEventArgs) {}

    fun onRedstoneOutputEnabledChanged() {
        val w = world ?: return
        w.notifyNeighborsOfStateChange(pos, blockType, true)
        if (isServer) PacketSender.sendRedstoneState(this)
        else w.notifyBlockUpdate(pos, w.getBlockState(pos), w.getBlockState(pos), 3)
    }

    fun onRedstoneOutputChanged(side: EnumFacing) {
        val w = world ?: return
        val blockPos = pos.offset(side)
        w.neighborChanged(blockPos, blockType, blockPos)
        w.notifyNeighborsOfStateExcept(blockPos, w.getBlockState(blockPos).block, side.opposite)

        if (isServer) PacketSender.sendRedstoneState(this)
        else w.notifyBlockUpdate(pos, w.getBlockState(pos), w.getBlockState(pos), 3)
    }
}
