package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.network.Component
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.Slot
import li.cil.oc.common.item.Tablet
import li.cil.oc.common.item.data.TabletData
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.util.Constants.NBT

object DriverTablet : Item() {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.ItemName.Tablet))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world != null && host.world.isRemote) null
    else {
      Tablet.Server.cache.invalidate(Tablet.getOrCreateId(stack))
      val data = TabletData(stack)
      data.items.firstOrNull { fs ->
        !fs.isEmpty && DriverFileSystem.worksWith(fs)
      }?.let { fs ->
        DriverFileSystem.createEnvironment(fs, host)
      }?.let { environment ->
        when (val node = environment.node()) {
          is Component -> {
            node.setVisibility(Visibility.Network)
            environment.save(dataTag(stack))
            environment
          }
          else -> null
        }
      }
    }

  override fun slot(stack: ItemStack) = Slot.Tablet

  override fun dataTag(stack: ItemStack): NBTTagCompound {
    val data = TabletData(stack)
    val index = data.items.indexOfFirst { fs ->
      !fs.isEmpty && DriverFileSystem.worksWith(fs)
    }
    return if (index >= 0 && stack.hasTagCompound && stack.tagCompound.hasKey(Settings.namespace + "items")) {
      val baseTag = stack.tagCompound.getTagList(Settings.namespace + "items", NBT.TAG_COMPOUND).getCompoundTagAt(index)
      if (!baseTag.hasKey("item")) {
        baseTag.setTag("item", NBTTagCompound())
      }
      val itemTag = baseTag.getCompoundTag("item")
      if (!itemTag.hasKey("tag")) {
        itemTag.setTag("tag", NBTTagCompound())
      }
      val stackTag = itemTag.getCompoundTag("tag")
      if (!stackTag.hasKey(Settings.namespace + "data")) {
        stackTag.setTag(Settings.namespace + "data", NBTTagCompound())
      }
      stackTag.getCompoundTag(Settings.namespace + "data")
    }
    else NBTTagCompound()
  }
}
