package li.cil.oc.integration.ic2

import ic2.core.block.comp.Energy
import ic2.core.block.machine.tileentity.TileEntityMatter
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class DriverMassFab : DriverSidedTileEntity() {
    override fun getTileEntityClass(): Class<*> = TileEntityMatter::class.java

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing)
        = (world.getTileEntity(pos) as? TileEntityMatter)?.let(::Environment)

    class Environment(tileEntity: TileEntityMatter) : ManagedTileEntityEnvironment<TileEntityMatter>(tileEntity, "mass_fab"), NamedBlock {
        override fun preferredName(): String = "mass_fab"
        override fun priority(): Int = 0

        @Callback
        fun getProgress(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(
                (100 * tileEntity.getComponent(Energy::class.java).fillRatio)
                    .coerceAtMost(100.0)
            )
        }
    }
}
