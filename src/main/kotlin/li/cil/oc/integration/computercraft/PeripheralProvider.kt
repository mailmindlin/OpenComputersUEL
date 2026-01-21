package li.cil.oc.integration.computercraft

import dan200.computercraft.api.ComputerCraftAPI
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.api.peripheral.IPeripheralProvider
import li.cil.oc.common.tileentity.Relay
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object PeripheralProvider : IPeripheralProvider {
    fun init() {
        ComputerCraftAPI.registerPeripheralProvider(this)
    }

    override fun getPeripheral(world: World, blockPos: BlockPos, enumFacing: EnumFacing): IPeripheral? {
        return when (val tileEntity = world.getTileEntity(blockPos)) {
            is Relay -> RelayPeripheral(tileEntity)
            else -> null
        }
    }
}
