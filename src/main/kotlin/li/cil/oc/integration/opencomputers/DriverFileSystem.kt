package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.FileSystem as ApiFileSystem
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.fs.Label
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Loot
import li.cil.oc.common.Slot
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.item.FloppyDisk
import li.cil.oc.common.item.HardDiskDrive
import li.cil.oc.common.item.data.DriveData
import li.cil.oc.server.component.Drive
import li.cil.oc.server.fs.FileSystem.ItemLabel
import li.cil.oc.server.fs.FileSystem.ReadOnlyLabel
import li.cil.oc.server.network.Node as NetworkNode
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.DimensionManager

object DriverFileSystem : Item() {
  val UUIDVerifier = """^([0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12})$""".toRegex()

  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    ApiItems.get(Constants.ItemName.HDDTier1),
    ApiItems.get(Constants.ItemName.HDDTier2),
    ApiItems.get(Constants.ItemName.HDDTier3),
    ApiItems.get(Constants.ItemName.Floppy)) &&
    (!stack.hasTagCompound() || !stack.tagCompound!!.hasKey(Settings.namespace + "lootPath"))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world() != null && host.world().isRemote) null
    else when (val item = Delegator.subItem(stack)) {
      is HardDiskDrive -> createEnvironment(stack, item.kiloBytes * 1024, item.platterCount, host, item.tier + 2)
      is FloppyDisk -> createEnvironment(stack, Settings.get.floppySize * 1024, 1, host, 1)
      else -> null
    }

  override fun slot(stack: ItemStack): String =
    when (val item = Delegator.subItem(stack)) {
      is HardDiskDrive -> Slot.HDD
      is FloppyDisk -> Slot.Floppy
      else -> throw IllegalArgumentException()
    }

  override fun tier(stack: ItemStack): Int =
    when (val item = Delegator.subItem(stack)) {
      is HardDiskDrive -> item.tier
      else -> 0
    }

  private fun createEnvironment(stack: ItemStack, capacity: Int, platterCount: Int, host: EnvironmentHost, speed: Int) = if (DimensionManager.getWorld(0) != null) {
    if (stack.hasTagCompound() && stack.tagCompound.hasKey(Settings.namespace + "lootFactory")) {
      // Loot disk, create file system using factory callback.
      Loot.factories[stack.tagCompound.getString(Settings.namespace + "lootFactory")]?.let { factory ->
        val label =
          if (dataTag(stack).hasKey(Settings.namespace + "fs.label"))
            dataTag(stack).getString(Settings.namespace + "fs.label")
          else null
        ApiFileSystem.asManagedEnvironment(factory.call(), label, host, Settings.resourceDomain + ":floppy_access")
      }
    }
    else {
      // We have a bit of a chicken-egg problem here, because we want to use the
      // node's address as the folder name... so we generate the address here,
      // if necessary. No one will know, right? Right!?
      val address = addressFromTag(dataTag(stack))
      var label: Label = ReadWriteItemLabel(stack)
      val isFloppy = ApiItems.get(stack) == ApiItems.get(Constants.ItemName.Floppy)
      val sound = Settings.resourceDomain + ":" + (if (isFloppy) "floppy_access" else "hdd_access")
      val drive = DriveData(stack)
      val environment = if (drive.isUnmanaged) {
        Drive(capacity.coerceAtLeast(0), platterCount, label, host, sound, speed, drive.isLocked)
      }
      else {
        var fs = ApiFileSystem.fromSaveDirectory(address, capacity.coerceAtLeast(0), Settings.get.bufferChanges)
        if (drive.isLocked) {
          fs = ApiFileSystem.asReadOnly(fs)
          label = ReadOnlyLabel(label.label)
        }
        ApiFileSystem.asManagedEnvironment(fs, label, host, sound, speed)
      }
      if (environment != null && environment.node() != null) {
        (environment.node() as NetworkNode).address = address
      }
      environment
    }
  }
  else null

  private fun addressFromTag(tag: NBTTagCompound): String =
    if (tag.hasKey("node") && tag.getCompoundTag("node").hasKey("address")) {
      val addressString = tag.getCompoundTag("node").getString("address")
      if (UUIDVerifier.matches(addressString)) {
        addressString
      } else {
        // Invalid disk address.
        val newAddress = java.util.UUID.randomUUID().toString()
        tag.getCompoundTag("node").setString("address", newAddress)
        OpenComputers.log.warn("Generated new address for disk '$newAddress'.")
        newAddress
      }
    }
    else java.util.UUID.randomUUID().toString()

  private class ReadWriteItemLabel(stack: ItemStack) : ItemLabel(stack) {
    var label: String? = null

    override fun getLabel(): String? = label

    override fun setLabel(value: String?) {
      label = value?.take(16)
    }

    private val LabelTag = Settings.namespace + "fs.label"

    override fun load(nbt: NBTTagCompound) {
      if (nbt.hasKey(LabelTag)) {
        label = nbt.getString(LabelTag)
      }
    }

    override fun save(nbt: NBTTagCompound) {
      label?.let { nbt.setString(LabelTag, it) }
    }
  }
}
