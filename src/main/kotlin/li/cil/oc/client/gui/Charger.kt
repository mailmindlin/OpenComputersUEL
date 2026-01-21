package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.common.container.Charger as ContainerCharger
import li.cil.oc.common.tileentity.Charger as TileEntityCharger
import net.minecraft.entity.player.InventoryPlayer

class Charger(playerInventory: InventoryPlayer, val charger: TileEntityCharger) : DynamicGuiContainer<ContainerCharger>(ContainerCharger(playerInventory, charger)) {
  override fun drawSecondaryForegroundLayer(mouseX: Int, mouseY: Int) {
    super.drawSecondaryForegroundLayer(mouseX, mouseY)
    fontRenderer.drawString(
      Localization.localizeImmediately(charger.getName()),
      8, 6, 0x404040)
  }
}
