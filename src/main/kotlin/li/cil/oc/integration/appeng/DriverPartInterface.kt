package li.cil.oc.integration.appeng

import appeng.api.implementations.tiles.ISegmentedInventory
import appeng.api.networking.IGridHost
import appeng.api.networking.security.IActionHost
import appeng.api.parts.IPartHost
import appeng.api.parts.PartItemStack
import appeng.api.util.AEPartLocation
import li.cil.oc.api.driver.DriverBlock
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.integration.ManagedTileEntityEnvironment
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object DriverPartInterface : DriverBlock {
  override fun worksWith(world: World, pos: BlockPos, side: EnumFacing): Boolean =
    when (val te = world.getTileEntity(pos)) {
      is IPartHost -> {
        EnumFacing.VALUES
          .mapNotNull { te.getPart(it) }
          .map { it.getItemStack(PartItemStack.PICK) }
          .any { AEUtil.isPartInterface(it) }
      }
      else -> false
    }

  override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing): ManagedEnvironment {
    val host: IPartHost = world.getTileEntity(pos) as IPartHost
    val tile = host as TilePartInterface
    val aePos: AEPartLocation = when (side) {
      EnumFacing.EAST -> AEPartLocation.WEST
      EnumFacing.WEST -> AEPartLocation.EAST
      EnumFacing.NORTH -> AEPartLocation.SOUTH
      EnumFacing.SOUTH -> AEPartLocation.NORTH
      EnumFacing.UP -> AEPartLocation.DOWN
      EnumFacing.DOWN -> AEPartLocation.UP
    }
    return Environment(host, tile, aePos)
  }

  class Environment(
    override val host: IPartHost,
    override val tile: TilePartInterface,
    override val pos: AEPartLocation
  ) : ManagedTileEntityEnvironment<IPartHost>(host, "me_interface"),
    NamedBlock,
    PartEnvironmentBase,
    NetworkControl<TilePartInterface> {

    override fun preferredName() = "me_interface"

    override fun priority() = 0

    @Callback(doc = "function(side:number[, slot:number]):table -- Get the configuration of the interface pointing in the specified direction.")
    fun getInterfaceConfiguration(context: Context, args: Arguments): Array<Any?> =
      getPartConfig<ISegmentedInventory>(context, args)

    @Callback(doc = "function(side:number[, slot:number][, database:address, entry:number[, size:number]]):boolean -- Configure the interface pointing in the specified direction.")
    fun setInterfaceConfiguration(context: Context, args: Arguments): Array<Any?> =
      setPartConfig<ISegmentedInventory>(context, args)
  }

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (AEUtil.isPartInterface(stack))
        Environment::class.java
      else null
  }
}

// Kotlin doesn't support intersection types, so we use TileEntity as base
// and cast to required interfaces when needed
private typealias TilePartInterface = TileEntity
