package li.cil.oc.integration.appeng

import appeng.api.implementations.tiles.ISegmentedInventory
import appeng.api.parts.IPartHost
import li.cil.oc.api.internal.Database
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Component
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.util.ResultWrapper.result
import li.cil.oc.util.checkSideAny
import li.cil.oc.util.optSlot
import net.minecraft.item.ItemStack
import net.minecraftforge.items.IItemHandler

interface PartEnvironmentBase : ManagedEnvironment {
  val host: IPartHost

  // function(side:number[, slot:number]):table
  fun <PartType> getPartConfig(context: Context, args: Arguments): Array<Any?>
    where PartType : ISegmentedInventory {
    val side = args.checkSideAny(0)
    return when (val part = host.getPart(side)) {
      is ISegmentedInventory -> {
        val config = part.getInventoryByName("config")
        val slot = args.optSlot(config, 1, 0)
        val stack = config.getStackInSlot(slot)
        result(stack)
      }
      else -> result(Unit, "no matching part")
    }
  }

  // function(side:number[, slot:number][, database:address, entry:number[, size:number]]):boolean
  fun <PartType> setPartConfig(context: Context, args: Arguments): Array<Any?>
    where PartType : ISegmentedInventory {
    val side = args.checkSideAny(0)
    return when (val part = host.getPart(side)) {
      is ISegmentedInventory -> {
        val config: IItemHandler = part.getInventoryByName("config")
        val slot = if (args.isString(1)) 0 else args.optSlot(config, 1, 0)
        val stack = if (args.count() > 2) {
          val (address, entry, size) =
            if (args.isString(1))
              Triple(args.checkString(1), args.checkInteger(2), args.optInteger(3, 1))
            else
              Triple(args.checkString(2), args.checkInteger(3), args.optInteger(4, 1))

          when (val component = node().network().node(address)) {
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
        result(true)
      }
      else -> result(Unit, "no matching part")
    }
  }
}
