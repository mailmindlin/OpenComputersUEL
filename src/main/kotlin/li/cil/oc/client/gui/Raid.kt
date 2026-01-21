package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.common.container.Raid as ContainerRaid
import li.cil.oc.common.tileentity.Raid as TileEntityRaid
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.player.InventoryPlayer

class Raid(playerInventory: InventoryPlayer, val raid: TileEntityRaid) : DynamicGuiContainer<ContainerRaid>(ContainerRaid(playerInventory, raid)) {
  override fun drawSecondaryForegroundLayer(mouseX: Int, mouseY: Int) {
    super.drawSecondaryForegroundLayer(mouseX, mouseY)
    fontRenderer.drawString(
      Localization.localizeImmediately(raid.name),
      8, 6, 0x404040)

    fontRenderer.drawSplitString(
      Localization.Raid.Warning,
      8, 46, 0x404040, width - 16)
  }

  override fun drawGuiContainerBackgroundLayer(dt: Float, mouseX: Int, mouseY: Int) {
    GlStateManager.color(1f, 1f, 1f) // Required under Linux.
    Textures.bind(Textures.GUI.Raid)
    drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize)
  }
}
