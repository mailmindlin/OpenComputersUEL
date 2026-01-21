package li.cil.oc.integration.appeng

import appeng.api.implementations.tiles.ISegmentedInventory
import appeng.api.parts.IPartHost
import appeng.api.parts.PartItemStack
import li.cil.oc.api.driver.DriverBlock
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.integration.ManagedTileEntityEnvironment
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object DriverImportBus : DriverBlock {
  override fun worksWith(world: World, pos: BlockPos, side: EnumFacing): Boolean =
    when (val te = world.getTileEntity(pos)) {
      is IPartHost -> EnumFacing.VALUES
        .mapNotNull { te.getPart(it) }
        .map { it.getItemStack(PartItemStack.PICK) }
        .any { AEUtil.isImportBus(it) }
      else -> false
    }

  override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing): ManagedEnvironment =
    Environment(world.getTileEntity(pos) as IPartHost)

  class Environment(override val host: IPartHost) :
    ManagedTileEntityEnvironment<IPartHost>(host, "me_importbus"),
    NamedBlock,
    PartEnvironmentBase {

    override fun preferredName() = "me_importbus"

    override fun priority() = 1

    @Callback(doc = "function(side:number[, slot:number]):boolean -- Get the configuration of the import bus pointing in the specified direction.")
    fun getImportConfiguration(context: Context, args: Arguments): Array<Any?> =
      getPartConfig<ISegmentedInventory>(context, args)

    @Callback(doc = "function(side:number[, slot:number][, database:address, entry:number]):boolean -- Configure the import bus pointing in the specified direction to import item stacks matching the specified descriptor.")
    fun setImportConfiguration(context: Context, args: Arguments): Array<Any?> =
      setPartConfig<ISegmentedInventory>(context, args)
  }

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (AEUtil.isImportBus(stack))
        Environment::class.java
      else null
  }
}
