package li.cil.oc.integration.minecraft

import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.block.Block
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntityMobSpawner
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object DriverMobSpawner : DriverSidedTileEntity() {
    override fun getTileEntityClass(): Class<*> = TileEntityMobSpawner::class.java

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing): ManagedEnvironment =
        Environment(world.getTileEntity(pos) as TileEntityMobSpawner)

    class Environment(tileEntity: TileEntityMobSpawner) :
        ManagedTileEntityEnvironment<TileEntityMobSpawner>(tileEntity, "mob_spawner"), NamedBlock {

        override fun preferredName(): String = "mob_spawner"

        override fun priority(): Int = 0

        @Callback(doc = "function():string -- Get the name of the entity that is being spawned by this spawner.")
        fun getSpawningMobName(context: Context, args: Arguments): Array<Any?> {
            return result(tileEntity.spawnerBaseLogic.entityId)
        }
    }

    object Provider : EnvironmentProvider {
        override fun getEnvironment(stack: ItemStack): Class<*>? {
            return if (!stack.isEmpty && Block.getBlockFromItem(stack.item) == Blocks.MOB_SPAWNER)
                Environment::class.java
            else null
        }
    }
}
