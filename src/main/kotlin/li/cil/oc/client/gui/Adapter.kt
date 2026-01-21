package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.common.container.Adapter as ContainerAdapter
import li.cil.oc.common.tileentity.Adapter as TileEntityAdapter
import net.minecraft.entity.player.InventoryPlayer

class Adapter(playerInventory: InventoryPlayer, val adapter: TileEntityAdapter) : DynamicGuiContainer<ContainerAdapter>(ContainerAdapter(playerInventory, adapter)) {
  override fun drawSecondaryForegroundLayer(mouseX: Int, mouseY: Int) {
    super.drawSecondaryForegroundLayer(mouseX, mouseY)
    fontRenderer.drawString(
      Localization.localizeImmediately(adapter.getName()),
      8, 6, 0x404040)
  }
}
