package li.cil.oc.integration

import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

internal inline fun <reified T> driverFor(crossinline ctor: (T) -> ManagedTileEntityEnvironment<T>): DriverSidedTileEntity {
    return object : DriverSidedTileEntity() {
        override fun getTileEntityClass(): Class<*> = T::class.java

        override fun createEnvironment(world: World?, pos: BlockPos?, side: EnumFacing?): ManagedTileEntityEnvironment<T>?
            = (world?.getTileEntity(pos) as? T)?.let(ctor)
    }
}