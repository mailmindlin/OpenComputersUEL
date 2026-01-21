package li.cil.oc.integration.ec

import appeng.api.implementations.tiles.ISegmentedInventory
import appeng.api.networking.IGridHost
import appeng.api.networking.security.IActionHost
import appeng.api.util.AEPartLocation
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.integration.appeng.AEUtil
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object DriverBlockInterface : DriverSidedTileEntity() {
    override fun getTileEntityClass(): Class<*> = AEUtil.interfaceClass

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing): ManagedEnvironment {
        val tile = world.getTileEntity(pos) as TileEntity
        return Environment(tile as InterfaceTile)
    }

    class Environment(override val tile: InterfaceTile) :
        ManagedTileEntityEnvironment<InterfaceTile>(tile, "me_interface"),
        NetworkControl<InterfaceTile> {
        override val pos: AEPartLocation = AEPartLocation.INTERNAL
    }

    object Provider : EnvironmentProvider {
        override fun getEnvironment(stack: ItemStack): Class<*>? =
            if (AEUtil.isBlockInterface(stack)) Environment::class.java else null
    }
}

// Type alias for the complex intersection type
typealias InterfaceTile = TileEntity
