package li.cil.oc.integration.appeng

import appeng.api.config.Actionable
import appeng.api.config.FuzzyMode
import appeng.api.config.Settings
import appeng.api.config.Upgrades
import appeng.api.implementations.IUpgradeableHost
import appeng.api.implementations.tiles.ISegmentedInventory
import appeng.api.networking.IGridHost
import appeng.api.networking.security.IActionHost
import appeng.api.parts.IPartHost
import appeng.api.parts.PartItemStack
import appeng.api.storage.IMEMonitor
import appeng.api.storage.data.IAEItemStack
import appeng.api.util.AEPartLocation
import appeng.api.util.IConfigurableObject
import li.cil.oc.api.driver.DriverBlock
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.ResultWrapper.result
import li.cil.oc.util.checkSideAny
import li.cil.oc.util.optSlot
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.items.IItemHandler

object DriverExportBus : DriverBlock {
  override fun worksWith(world: World, pos: BlockPos, side: EnumFacing): Boolean =
    when (val te = world.getTileEntity(pos)) {
      is IPartHost -> EnumFacing.VALUES
        .mapNotNull { te.getPart(it) }
        .map { it.getItemStack(PartItemStack.PICK) }
        .any { AEUtil.isExportBus(it) }
      else -> false
    }

  override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing): ManagedEnvironment =
    Environment(world.getTileEntity(pos) as IPartHost)

  class Environment(override val host: IPartHost) :
    ManagedTileEntityEnvironment<IPartHost>(host, "me_exportbus"),
    NamedBlock,
    PartEnvironmentBase {

    override fun preferredName() = "me_exportbus"

    override fun priority() = 2

    @Callback(doc = "function(side:number, [ slot:number]):boolean -- Get the configuration of the export bus pointing in the specified direction.")
    fun getExportConfiguration(context: Context, args: Arguments): Array<Any?> =
      getPartConfig<ISegmentedInventory>(context, args)

    @Callback(doc = "function(side:number[, slot:number][, database:address, entry:number):boolean -- Configure the export bus pointing in the specified direction to export item stacks matching the specified descriptor.")
    fun setExportConfiguration(context: Context, args: Arguments): Array<Any?> =
      setPartConfig<ISegmentedInventory>(context, args)

    private fun doExport(
      itemStorage: IMEMonitor<IAEItemStack>,
      ais: IAEItemStack,
      inventory: IItemHandler,
      targetSlot: Int?,
      count: Int,
      source: MachineSource,
      simulate: Boolean
    ): Boolean {
      val limit = ais.stackSize.toInt().coerceAtMost(count)
      ais.stackSize = limit.toLong()
      val itemStack = ais.createItemStack()
      if (targetSlot != null) {
        if (!InventoryUtils.insertIntoInventorySlot(itemStack, inventory, targetSlot, count, simulate)) {
          return false
        }
      } else if (!InventoryUtils.insertIntoInventory(itemStack, inventory, count, simulate)) {
        return false
      }

      if (itemStack.count > 0) {
        ais.stackSize = (limit - itemStack.count).toLong()
      }

      val extracted: IAEItemStack? = itemStorage.extractItems(
        ais,
        if (simulate) Actionable.SIMULATE else Actionable.MODULATE,
        source
      )

      return extracted != null
    }

    @Callback(doc = "function(side:number, [slot:number]):boolean -- Make the export bus facing the specified direction perform a single export operation into the specified slot.")
    fun exportIntoSlot(context: Context, args: Arguments): Array<Any?> {
      val side = args.checkSideAny(0)
      val part = host.getPart(side)

      if (part == null || !AEUtil.isExportBus(part.getItemStack(PartItemStack.PICK))) {
        return result(Unit, "no export bus")
      }

      val exportBus = part
      if (exportBus !is ISegmentedInventory || exportBus !is IUpgradeableHost || exportBus !is IGridHost || exportBus !is IActionHost)
        throw AssertionError()
      val location = host.location

      val inventory: IItemHandler = InventoryUtils.inventoryAt(
        BlockPosition(location.x, location.y, location.z, location.world).offset(side),
        side.opposite
      ) ?: return result(Unit, "no inventory")

      val targetSlot: Int? = when (val slot = args.optSlot(inventory, 1, -1)) {
        -1 -> null
        else -> slot
      }
      val config = exportBus.getInventoryByName("config")
      val itemStorage = AEUtil.getGridStorage(exportBus.getGridNode(AEPartLocation.fromFacing(side))!!.grid)
        .getInventory(AEUtil.itemStorageChannel)
      var count = when (exportBus.getInstalledUpgrades(Upgrades.SPEED)) {
        1 -> 8
        2 -> 32
        3 -> 64
        4 -> 96
        else -> 1
      }
      val fuzzyMode = exportBus.configManager.getSetting(Settings.FUZZY_MODE) as FuzzyMode
      val source = MachineSource(exportBus)
      val potentialWork = count

      for (slot in 0 until config.slots) {
        if (count <= 0) break
        val filter = AEUtil.itemStorageChannel.createStack(config.getStackInSlot(slot))
        val stacks: Sequence<IAEItemStack> =
          if (exportBus.getInstalledUpgrades(Upgrades.FUZZY) > 0)
            itemStorage.storageList.findFuzzy(filter, fuzzyMode).asSequence()
              .map { it as IAEItemStack }
          else
            sequenceOf(itemStorage.storageList.findPrecise(filter))

        for (ais in stacks.filterNotNull().map { it.copy() }.filter { it.stackSize > 0 }) {
          if (count <= 0) break
          if (doExport(itemStorage, ais, inventory, targetSlot, count, source, simulate = true)) {
            if (doExport(itemStorage, ais, inventory, targetSlot, count, source, simulate = false)) {
              count = (count - ais.stackSize.toInt()).coerceAtLeast(0)
              context.pause(0.25)
            }
          }
        }
      }
      return if (potentialWork == count)
        result(Unit, "no items moved")
      else
        result(potentialWork - count)
    }
  }

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (AEUtil.isExportBus(stack))
        Environment::class.java
      else null
  }
}

// Kotlin doesn't support intersection types, so we use the base interface
// and cast as needed. In practice, AE2's export bus implements all of these.
private typealias TileExportBus = ISegmentedInventory

private fun <T> Some(value: T): T? = value
