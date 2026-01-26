package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Node
import li.cil.oc.common.tileentity.traits.RedstoneAware
import li.cil.oc.common.tileentity.traits.RedstoneChangedEventArgs
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedWorld.extendedWorld
import net.minecraft.util.EnumFacing

abstract class RedstoneVanilla<T> : RedstoneSignaller(), DeviceInfoKt where T : EnvironmentHost, T : RedstoneAware {
    abstract val redstone: T

    override val node: Node
        get() = TODO("Not yet implemented")
    // ----------------------------------------------------------------------- //

    override val deviceInfo: Map<String, String> by lazy {
        mapOf(
            DeviceAttribute.Class to DeviceClass.Communication,
            DeviceAttribute.Description to "Redstone controller",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "Rs100-V",
            DeviceAttribute.Capacity to "16",
            DeviceAttribute.Width to "1"
        )
    }


    protected val SIDE_RANGE: Array<EnumFacing> = EnumFacing.values()

    // ----------------------------------------------------------------------- //

    @Callback(direct = true, doc = "function([side:number]):number or table -- Get the redstone input (all sides, or optionally on the specified side)")
    fun getInput(context: Context, args: Arguments): Array<Any?> {
        val side = getOptionalSide(args)
        return if (side != null) {
            result(redstone.getInput(side))
        } else {
            result(valuesToMap(redstone.getInput()))
        }
    }

    @Callback(direct = true, doc = "function([side:number]):number or table -- Get the redstone output (all sides, or optionally on the specified side)")
    fun getOutput(context: Context, args: Arguments): Array<Any?> {
        val side = getOptionalSide(args)
        return if (side != null) {
            result(redstone.getOutput(side))
        } else {
            result(valuesToMap(redstone.getOutput()))
        }
    }

    @Callback(doc = "function([side:number, ]value:number or table):number or table --  Set the redstone output (all sides, or optionally on the specified side). Returns previous values")
    fun setOutput(context: Context, args: Arguments): Array<Any?> {
        var ret: Any? = null
        val changed = when (val assignment = getAssignment(args)) {
            is SideValueAssignment -> {
                ret = redstone.getOutput(assignment.side)
                redstone.setOutput(assignment.side, assignment.value)
            }
            is TableAssignment -> {
                ret = valuesToMap(redstone.getOutput())
                redstone.setOutput(assignment.table)
            }
        }
        if (changed && Settings.get.redstoneDelay > 0) {
            context.pause(Settings.get.redstoneDelay)
        }
        return result(ret)
    }

    @Callback(direct = true, doc = "function(side:number):number -- Get the comparator input on the specified side.")
    fun getComparatorInput(context: Context, args: Arguments): Array<Any?> {
        val side = checkSide(args, 0)
        val world = redstone.world() ?: return result(0)
        val blockPos = BlockPosition(redstone).offset(side)
        if (world.extendedWorld().blockExists(blockPos)) {
            val block = world.extendedWorld().getBlock(blockPos)
            val pos = blockPos.toBlockPos()
            @Suppress("DEPRECATION")
            if (block != null && block.hasComparatorInputOverride(world.getBlockState(pos))) {
                @Suppress("DEPRECATION")
                val comparatorOverride = block.getComparatorInputOverride(
                    world.getBlockState(pos),
                    world,
                    pos
                )
                return result(comparatorOverride)
            }
        }
        return result(0)
    }

    // ----------------------------------------------------------------------- //

    override fun onMessage(message: Message) {
        super.onMessage(message)
        if (message.name() == "redstone.changed") {
            val data = message.data()
            if (data.isNotEmpty() && data[0] is RedstoneChangedEventArgs) {
                onRedstoneChanged(data[0] as RedstoneChangedEventArgs)
            }
        }
    }

    // ----------------------------------------------------------------------- //

    private fun getOptionalSide(args: Arguments): EnumFacing? {
        return if (args.count() == 1) {
            checkSide(args, 0)
        } else {
            null
        }
    }

    private sealed class Assignment
    private data class SideValueAssignment(val side: EnumFacing, val value: Int) : Assignment()
    private data class TableAssignment(val table: Map<*, *>) : Assignment()

    private fun getAssignment(args: Arguments): Assignment {
        return when (args.count()) {
            2 -> SideValueAssignment(checkSide(args, 0), args.checkInteger(1))
            1 -> TableAssignment(args.checkTable(0))
            else -> throw Exception("invalid number of arguments, expected 1 or 2")
        }
    }

    protected fun checkSide(args: Arguments, index: Int): EnumFacing {
        val side = args.checkInteger(index)
        if (side < 0 || side > 5) {
            throw IllegalArgumentException("invalid side")
        }
        return redstone.toGlobal(EnumFacing.byIndex(side))
    }

    private fun valuesToMap(ar: IntArray): Map<Int, Int> =
        SIDE_RANGE.filter { it.ordinal < ar.size }.associate { it.ordinal to ar[it.ordinal] }
}
