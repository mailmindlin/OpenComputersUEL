package li.cil.oc.integration.appeng

import appeng.api.implementations.tiles.ISegmentedInventory
import appeng.api.networking.IGridHost
import appeng.api.networking.security.IActionHost
import appeng.api.parts.IPartHost
import appeng.api.util.AEPartLocation
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.internal.Database
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Component
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.network.Node
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.util.Result
import li.cil.oc.util.optSlot
import li.cil.oc.util.result
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.items.IItemHandler

object DriverBlockInterface : DriverSidedTileEntity() {
  override fun getTileEntityClass(): Class<*>? = AEUtil.interfaceClass()

  override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing): ManagedEnvironment {
    val te = world.getTileEntity(pos)
    if (te !is ISegmentedInventory || te !is IActionHost || te !is IGridHost)
      throw AssertionError()
    return Environment(te)
  }

  class Environment<TE: TileEntity>(override val tile: TE) :
    ManagedTileEntityEnvironment<TE>(tile, "me_interface"),
    NamedBlock,
    NetworkControl<TE>
  where
    TE: ISegmentedInventory,
    TE: IActionHost,
    TE: IGridHost
  {

    override fun preferredName() = "me_interface"
    override val pos: AEPartLocation = AEPartLocation.INTERNAL

    override fun priority() = 5

    @Callback(doc = "function([slot:number]):table -- Get the configuration of the interface.")
    fun getInterfaceConfiguration(context: Context, args: Arguments): Result {
      val config: IItemHandler = tile.getInventoryByName("config")
      val slot = args.optSlot(config, 0, 0)
      val stack = config.getStackInSlot(slot)
      return result(stack)
    }

    @Callback(doc = "function([slot:number][, database:address, entry:number[, size:number]]):boolean -- Configure the interface.")
    fun setInterfaceConfiguration(context: Context, args: Arguments): Result {
      val config: IItemHandler = tile.getInventoryByName("config")
      val slot = if (args.isString(0)) 0 else args.optSlot(config, 0, 0)
      val stack = if (args.count() > 1) {
        val (address, entry, size) =
          if (args.isString(0))
            Triple(args.checkString(0), args.checkInteger(1), args.optInteger(2, 1))
          else
            Triple(args.checkString(1), args.checkInteger(2), args.optInteger(3, 1))

        when (val component = node()!!.network()!!.node(address)) {
          is Component -> when (val componentHost = component.host()) {
            is Database -> {
              val dbStack = componentHost.getStackInSlot(entry - 1)
              if (dbStack == null || size < 1 || dbStack.isEmpty) ItemStack.EMPTY
              else {
                dbStack.count = kotlin.math.min(size, dbStack.maxStackSize)
                dbStack
              }
            }
            else -> throw IllegalArgumentException("not a database")
          }
          else -> throw IllegalArgumentException("no such component")
        }
      } else ItemStack.EMPTY
      config.extractItem(slot, config.getStackInSlot(slot).count, false)
      config.insertItem(slot, stack, false)
      context.pause(0.5)
      return result(true)
    }
  }

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (AEUtil.isBlockInterface(stack))
        Environment::class.java
      else null
  }
}