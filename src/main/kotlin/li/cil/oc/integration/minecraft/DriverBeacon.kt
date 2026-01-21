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
import net.minecraft.potion.Potion
import net.minecraft.tileentity.TileEntityBeacon
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object DriverBeacon : DriverSidedTileEntity() {
    override fun getTileEntityClass(): Class<*> = TileEntityBeacon::class.java

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing): ManagedEnvironment =
        Environment(world.getTileEntity(pos) as TileEntityBeacon)

    class Environment(tileEntity: TileEntityBeacon) :
        ManagedTileEntityEnvironment<TileEntityBeacon>(tileEntity, "beacon"), NamedBlock {

        override fun preferredName(): String = "beacon"

        override fun priority(): Int = 0

        @Callback(doc = "function():number -- Get the number of levels for this beacon.")
        fun getLevels(context: Context, args: Arguments): Array<Any?> {
            return result(tileEntity.getField(0))
        }

        @Callback(doc = "function():string -- Get the name of the active primary effect.")
        fun getPrimaryEffect(context: Context, args: Arguments): Array<Any?> {
            return result(getEffectName(tileEntity.getField(1)))
        }

        @Callback(doc = "function():string -- Get the name of the active secondary effect.")
        fun getSecondaryEffect(context: Context, args: Arguments): Array<Any?> {
            return result(getEffectName(tileEntity.getField(2)))
        }

        private fun getEffectName(id: Int): String? {
            val potion = Potion.getPotionById(id)
            return potion?.name
        }
    }

    object Provider : EnvironmentProvider {
        override fun getEnvironment(stack: ItemStack): Class<*>? {
            return if (!stack.isEmpty && Block.getBlockFromItem(stack.item) == Blocks.BEACON)
                Environment::class.java
            else null
        }
    }
}
