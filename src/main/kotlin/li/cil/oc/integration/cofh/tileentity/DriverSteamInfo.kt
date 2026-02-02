package li.cil.oc.integration.cofh.tileentity

import cofh.api.tileentity.ISteamInfo
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

internal class EnvironmentSteamInfo(tileEntity: ISteamInfo) : ManagedTileEntityEnvironment<ISteamInfo>(tileEntity, "steam_info") {
    @Callback(doc = "function():number --  Returns the steam per tick.")
    fun getSteamPerTick(context: Context?, args: Arguments?): Array<Any> {
        return arrayOf(tileEntity.infoSteamPerTick)
    }

    @Callback(doc = "function():number --  Returns the maximum steam per tick.")
    fun getMaxSteamPerTick(context: Context?, args: Arguments?): Array<Any> {
        return arrayOf(tileEntity.infoMaxSteamPerTick)
    }
}