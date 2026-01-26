package li.cil.oc.server.component

import li.cil.oc.api.Network
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.common.tileentity.traits.RedstoneChangedEventArgs
import net.minecraft.nbt.NBTTagCompound

abstract class RedstoneSignaller : ManagedEnvironmentKt() {
    override val node: Node = nodeFactory(Visibility.Network)
        .withComponent("redstone", Visibility.Neighbors)
        .create()

    var wakeThreshold = 0

    var wakeNeighborsOnly = true

    // ----------------------------------------------------------------------- //

    @Callback(direct = true, doc = """function():number -- Get the current wake-up threshold.""")
    fun getWakeThreshold(context: Context, args: Arguments): Array<Any?> = result(wakeThreshold)

    @Callback(doc = """function(threshold:number):number -- Set the wake-up threshold.""")
    fun setWakeThreshold(context: Context, args: Arguments): Array<Any?> {
        val oldThreshold = wakeThreshold
        wakeThreshold = args.checkInteger(0)
        return result(oldThreshold)
    }

    // ----------------------------------------------------------------------- //

    fun onRedstoneChanged(args: RedstoneChangedEventArgs) {
        val side: Any = if (args.side == null) "wireless" else args.side.ordinal
        val flatArgs = mutableListOf<Any?>("redstone_changed", side, args.oldValue, args.newValue)
        if (args.color >= 0) {
            flatArgs.add(args.color)
        }
        node().sendToReachable("computer.signal", *flatArgs.toTypedArray())
        if (args.oldValue < wakeThreshold && args.newValue >= wakeThreshold) {
            if (wakeNeighborsOnly)
                node().sendToNeighbors("computer.start")
            else
                node().sendToReachable("computer.start")
        }
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private const val WakeThresholdNbt = "wakeThreshold"
    }

    override fun load(nbt: NBTTagCompound) {
        super.load(nbt)
        wakeThreshold = nbt.getInteger(WakeThresholdNbt)
    }

    override fun save(nbt: NBTTagCompound) {
        super.save(nbt)
        nbt.setInteger(WakeThresholdNbt, wakeThreshold)
    }
}
