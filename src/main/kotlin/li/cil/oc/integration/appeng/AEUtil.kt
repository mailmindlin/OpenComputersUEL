package li.cil.oc.integration.appeng

import javax.annotation.Nonnull

import appeng.api.AEApi
import appeng.api.networking.IGrid
import appeng.api.networking.crafting.ICraftingGrid
import appeng.api.networking.energy.IEnergyGrid
import appeng.api.networking.storage.IStorageGrid
import appeng.api.storage.channels.IFluidStorageChannel
import appeng.api.storage.channels.IItemStorageChannel
import appeng.api.storage.data.IAEFluidStack
import appeng.api.storage.data.IAEItemStack
import li.cil.oc.integration.Mods
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.common.versioning.VersionRange
import net.minecraftforge.fml.common.Loader

object AEUtil {
  val versionsWithNewItemDefinitionAPI: VersionRange = VersionRange.createFromVersionSpec("[rv6-stable-5,)")

  val itemStorageChannel: IItemStorageChannel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel::class.java)
  val fluidStorageChannel: IFluidStorageChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel::class.java)

  @JvmStatic
  fun useNewItemDefinitionAPI(): Boolean = versionsWithNewItemDefinitionAPI.containsVersion(
    Loader.instance().indexedModList[Mods.AppliedEnergistics2.id]!!.processedVersion)

  // ----------------------------------------------------------------------- //

  @JvmStatic
  fun controllerClass(): Class<*>? {
    if (AEApi.instance() != null) {
      val maybe = AEApi.instance().definitions().blocks().controller().maybeEntity()
      if (maybe.isPresent)
        return maybe.get()
      else
        return null
    }
    else return null
  }

  // ----------------------------------------------------------------------- //

  @JvmStatic
  fun interfaceClass(): Class<*>? =
    if (AEApi.instance() != null)
      AEApi.instance().definitions().blocks().iface().maybeEntity().get()
    else null

  // ----------------------------------------------------------------------- //

  @JvmStatic
  fun isController(stack: ItemStack?): Boolean = stack != null && AEApi.instance() != null && AEApi.instance().definitions().blocks().controller().isSameAs(stack)

  // ----------------------------------------------------------------------- //

  @JvmStatic
  fun isExportBus(stack: ItemStack?): Boolean = stack != null && AEApi.instance() != null && AEApi.instance().definitions().parts().exportBus().isSameAs(stack)

  // ----------------------------------------------------------------------- //

  @JvmStatic
  fun isImportBus(stack: ItemStack?): Boolean = stack != null && AEApi.instance() != null && AEApi.instance().definitions().parts().importBus().isSameAs(stack)

  // ----------------------------------------------------------------------- //

  @JvmStatic
  fun isBlockInterface(stack: ItemStack?): Boolean = stack != null && AEApi.instance() != null && AEApi.instance().definitions().blocks().iface().isSameAs(stack)

  // ----------------------------------------------------------------------- //

  @JvmStatic
  fun isPartInterface(stack: ItemStack?): Boolean = stack != null && AEApi.instance() != null && AEApi.instance().definitions().parts().iface().isSameAs(stack)

  // ----------------------------------------------------------------------- //

  @JvmStatic
  fun getGridStorage(@Nonnull grid: IGrid): IStorageGrid = grid.getCache(IStorageGrid::class.java)

  // ----------------------------------------------------------------------- //

  @JvmStatic
  fun getGridCrafting(@Nonnull grid: IGrid): ICraftingGrid = grid.getCache(ICraftingGrid::class.java)

  // ----------------------------------------------------------------------- //

  @JvmStatic
  fun getGridEnergy(@Nonnull grid: IGrid): IEnergyGrid = grid.getCache(IEnergyGrid::class.java)
}
