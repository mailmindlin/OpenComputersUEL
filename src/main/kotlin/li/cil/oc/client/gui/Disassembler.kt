package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.client.gui.widget.ProgressBar
import li.cil.oc.common.container.Disassembler as ContainerDisassembler
import li.cil.oc.common.tileentity.Disassembler as TileEntityDisassembler
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.player.InventoryPlayer

class Disassembler(playerInventory: InventoryPlayer, val disassembler: TileEntityDisassembler) : DynamicGuiContainer<ContainerDisassembler>(ContainerDisassembler(playerInventory, disassembler)) {
  val progress = addWidget(ProgressBar(18, 65))

  override fun drawSecondaryForegroundLayer(mouseX: Int, mouseY: Int) {
    fontRenderer.drawString(
      Localization.localizeImmediately(disassembler.name),
      8, 6, 0x404040)
  }

  override fun drawGuiContainerBackgroundLayer(dt: Float, mouseX: Int, mouseY: Int) {
    GlStateManager.color(1f, 1f, 1f)
    Textures.bind(Textures.GUI.Disassembler)
    drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize)
    progress.level = inventoryContainer.disassemblyProgress() / 100.0
    drawWidgets()
  }
}
