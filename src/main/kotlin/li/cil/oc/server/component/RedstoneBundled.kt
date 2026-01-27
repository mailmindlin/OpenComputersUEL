package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.tileentity.traits.BundledRedstoneAware
import net.minecraft.util.EnumFacing

abstract class RedstoneBundled<T> : RedstoneVanilla<T>() where T : EnvironmentHost, T : BundledRedstoneAware {
    private val bundledDeviceInfo by lazy {
        mapOf(
            DeviceAttribute.Class to DeviceClass.Communication,
            DeviceAttribute.Description to "Advanced redstone controller",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "Rb800-M",
            DeviceAttribute.Capacity to "65536",
            DeviceAttribute.Width to "16"
        )
    }

    override fun getDeviceInfo() = bundledDeviceInfo

    private val COLOR_RANGE = 0 until 16

    // ----------------------------------------------------------------------- //

    private fun getBundleKey(args: Arguments): Pair<EnumFacing?, Int?> {
        return when (args.count()) {
            2 -> Pair(checkSide(args, 0), checkColor(args, 1))
            1 -> Pair(checkSide(args, 0), null)
            0 -> Pair(null, null)
            else -> throw Exception("too many arguments, expected 0, 1, or 2")
        }
    }

    private fun colorsToMap(ar: IntArray): Map<Int, Int> =
        COLOR_RANGE.filter { it < ar.size }.associateWith { ar[it] }

    private fun sidesToMap(ar: Array<IntArray>): Map<Int, Map<Int, Int>> =
        SIDE_RANGE.filter { it.ordinal < ar.size && ar[it.ordinal].isNotEmpty() }
            .associate { it.ordinal to colorsToMap(ar[it.ordinal]) }

    private sealed class BundleAssignment
    private data class SideColorValueAssignment(val side: EnumFacing, val color: Int, val value: Int) : BundleAssignment()
    private data class SideTableAssignment(val side: EnumFacing, val table: Map<*, *>) : BundleAssignment()
    private data class TableAssignment(val table: Map<*, *>) : BundleAssignment()

    private fun getBundleAssignment(args: Arguments): BundleAssignment {
        return when (args.count()) {
            3 -> SideColorValueAssignment(checkSide(args, 0), checkColor(args, 1), args.checkInteger(2))
            2 -> SideTableAssignment(checkSide(args, 0), args.checkTable(1))
            1 -> TableAssignment(args.checkTable(0))
            else -> throw Exception("invalid number of arguments, expected 1, 2, or 3")
        }
    }

    @Callback(direct = true, doc = "function([side:number[, color:number]]):number or table -- Fewer params returns set of inputs")
    fun getBundledInput(context: Context, args: Arguments): Array<Any?> {
        val (side, color) = getBundleKey(args)

        return when {
            color != null && side != null -> result(redstone.getBundledInput(side, color))
            side != null -> result(colorsToMap(redstone.getBundledInput(side)))
            else -> result(sidesToMap(redstone.getBundledInput()))
        }
    }

    @Callback(direct = true, doc = "function([side:number[, color:number]]):number or table -- Fewer params returns set of outputs")
    fun getBundledOutput(context: Context, args: Arguments): Array<Any?> {
        val (side, color) = getBundleKey(args)

        return when {
            color != null && side != null -> result(redstone.getBundledOutput(side, color))
            side != null -> result(colorsToMap(redstone.getBundledOutput(side)))
            else -> result(sidesToMap(redstone.getBundledOutput()))
        }
    }

    @Callback(doc = "function([side:number[, color:number,]] value:number or table):number or table --  Fewer params to assign set of outputs. Returns previous values")
    fun setBundledOutput(context: Context, args: Arguments): Array<Any?> {
        var ret: Any? = null
        val changed = when (val assignment = getBundleAssignment(args)) {
            is SideColorValueAssignment -> {
                ret = redstone.getBundledOutput(assignment.side, assignment.color)
                redstone.setBundledOutput(assignment.side, assignment.color, assignment.value)
            }
            is SideTableAssignment -> {
                ret = redstone.getBundledOutput(assignment.side)
                redstone.setBundledOutput(assignment.side, assignment.table)
            }
            is TableAssignment -> {
                ret = redstone.getBundledOutput()
                redstone.setBundledOutput(assignment.table)
            }
        }
        if (changed && Settings.get.redstoneDelay > 0) {
            context.pause(Settings.get.redstoneDelay)
        }
        return result(ret)
    }

    // ----------------------------------------------------------------------- //

    private fun checkColor(args: Arguments, index: Int): Int {
        val color = args.checkInteger(index)
        if (color !in COLOR_RANGE) {
            throw IllegalArgumentException("invalid color")
        }
        return color
    }
}
