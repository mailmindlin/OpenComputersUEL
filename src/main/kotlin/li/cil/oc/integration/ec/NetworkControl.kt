package li.cil.oc.integration.ec

import appeng.api.networking.IGridHost
import appeng.api.networking.security.IActionHost
import appeng.api.util.AEPartLocation
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.integration.appeng.AEUtil
import li.cil.oc.util.Result
import li.cil.oc.util.result
import net.minecraft.tileentity.TileEntity

// Note to self: this trait is used by ExtraCells (and potentially others), do not rename / drastically change it.
interface NetworkControl<AETile : TileEntity> where AETile : IActionHost, AETile : IGridHost {
    val tile: AETile
    val pos: AEPartLocation

    @Callback(doc = "function():table -- Get a list of the stored gases in the network.")
    fun getGasesInNetwork(context: Context, args: Arguments): Result {
        return if (ECUtil.isGasSystemEnabled) {
            val grid = tile.getGridNode(pos)!!.grid
            val storage = AEUtil.getGridStorage(grid)
            val inventory = storage.getInventory(ECUtil.gasStorageChannel)
            val storageList = inventory.storageList
            val gases = storageList.filterNotNull().map { it.gasStack }.toTypedArray()
            result(*gases)
        } else {
            result()
        }
    }
}
