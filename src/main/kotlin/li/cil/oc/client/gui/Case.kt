package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.client.PacketSender as ClientPacketSender
import li.cil.oc.common.container
import li.cil.oc.common.tileentity
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.player.InventoryPlayer

class Case(playerInventory: InventoryPlayer, val computer: tileentity.Case) : DynamicGuiContainer(container.Case(playerInventory, computer)) {
  protected var powerButton: ImageButton? = null

  override fun actionPerformed(button: GuiButton) {
    if (button.id == 0) {
      ClientPacketSender.sendComputerPower(computer, !computer.isRunning)
    }
  }

  override fun drawScreen(mouseX: Int, mouseY: Int, dt: Float) {
    powerButton?.toggled = computer.isRunning
    super.drawScreen(mouseX, mouseY, dt)
  }

  override fun initGui() {
    super.initGui()
    powerButton = ImageButton(0, guiLeft + 70, guiTop + 33, 18, 18, Textures.GUI.ButtonPower, canToggle = true)
    buttonList.add(powerButton)
  }

  override fun drawSecondaryForegroundLayer(mouseX: Int, mouseY: Int) {
    super.drawSecondaryForegroundLayer(mouseX, mouseY)
    fontRenderer.drawString(
      Localization.localizeImmediately(computer.name),
      8, 6, 0x404040)
    if (powerButton?.isMouseOver == true) {
      val tooltip = java.util.ArrayList<String>()
      tooltip.addAll(if (computer.isRunning) Localization.Computer.TurnOff.lines.toList() else Localization.Computer.TurnOn.lines.toList())
      copiedDrawHoveringText(tooltip, mouseX - guiLeft, mouseY - guiTop, fontRenderer)
    }
  }

  override fun drawSecondaryBackgroundLayer() {
    GlStateManager.color(1f, 1f, 1f)
    Textures.bind(Textures.GUI.Computer)
    drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize)
  }
}
