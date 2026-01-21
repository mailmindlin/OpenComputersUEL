package li.cil.oc.integration.opencomputers

import java.io.File

import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.FileSystem as ApiFileSystem
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import net.minecraft.item.ItemStack
import net.minecraftforge.common.DimensionManager

// This is deprecated and kept for compatibility with old saves.
// As of OC 1.5.10, loot disks are generated using normal floppies, and using
// a factory system that allows third-party mods to register loot disks.
object DriverLootDisk : Item() {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    ApiItems.get(Constants.ItemName.Floppy)) &&
    (stack.hasTagCompound && stack.tagCompound.hasKey(Settings.namespace + "lootPath"))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (!host.world.isRemote && stack.hasTagCompound && DimensionManager.getWorld(0) != null) {
      val lootPath = "loot/" + stack.tagCompound.getString(Settings.namespace + "lootPath")
      val savePath = File(DimensionManager.getCurrentSaveRootDirectory(), Settings.savePath + lootPath)
      val fs =
        if (savePath.exists() && savePath.isDirectory) {
          ApiFileSystem.fromSaveDirectory(lootPath, 0, false)
        }
        else {
          ApiFileSystem.fromClass(OpenComputers::class.java, Settings.resourceDomain, lootPath)
        }
      val label =
        if (dataTag(stack).hasKey(Settings.namespace + "fs.label")) {
          dataTag(stack).getString(Settings.namespace + "fs.label")
        }
        else null
      ApiFileSystem.asManagedEnvironment(fs, label, host, Settings.resourceDomain + ":floppy_access")
    }
    else null

  override fun slot(stack: ItemStack) = Slot.Floppy
}
