package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.common.container.DiskDrive as ContainerDiskDrive
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.inventory.IInventory

class DiskDrive(playerInventory: InventoryPlayer, val drive: IInventory) : DynamicGuiContainer<ContainerDiskDrive>(ContainerDiskDrive(playerInventory, drive)) {
  override fun drawSecondaryForegroundLayer(mouseX: Int, mouseY: Int) {
    super.drawSecondaryForegroundLayer(mouseX, mouseY)
    fontRenderer.drawString(
      Localization.localizeImmediately(drive.name),
      8, 6, 0x404040)
  }
}
