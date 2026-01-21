package li.cil.oc.client.gui

import li.cil.oc.client.PacketSender
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity.Waypoint as TileEntityWaypoint
import li.cil.oc.util.OldScaledResolution
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiTextField
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import org.lwjgl.input.Keyboard

class Waypoint(val waypoint: TileEntityWaypoint) : GuiScreen() {
  var guiLeft = 0
  var guiTop = 0
  var xSize = 0
  var ySize = 0

  var textField: GuiTextField? = null

  override fun updateScreen() {
    super.updateScreen()
    if (mc.player.getDistanceSq(waypoint.x + 0.5, waypoint.y + 0.5, waypoint.z + 0.5) > 64) {
      mc.player.closeScreen()
    }
  }

  override fun doesGuiPauseGame(): Boolean = false

  override fun initGui() {
    super.initGui()

    val screenSize = ScaledResolution(mc)
    val guiSize = OldScaledResolution(mc, 176, 24)
    val midX = screenSize.scaledWidth / 2
    val midY = screenSize.scaledHeight / 2
    guiLeft = midX - guiSize.scaledWidth / 2
    guiTop = midY - guiSize.scaledHeight / 2
    xSize = guiSize.scaledWidth
    ySize = guiSize.scaledHeight

    textField = GuiTextField(0, fontRenderer, guiLeft + 7, guiTop + 8, 164 - 12, 12)
    textField?.setMaxStringLength(32)
    textField?.setEnableBackgroundDrawing(false)
    textField?.setCanLoseFocus(false)
    textField?.setFocused(true)
    textField?.setTextColor(0xFFFFFF)
    textField?.text = waypoint.label

    Keyboard.enableRepeatEvents(true)
  }

  override fun onGuiClosed() {
    super.onGuiClosed()
    Keyboard.enableRepeatEvents(false)
  }

  override fun keyTyped(char: Char, code: Int) {
    if (textField?.textboxKeyTyped(char, code) != true) {
      if (code == Keyboard.KEY_RETURN) {
        val label = textField?.text?.take(32) ?: ""
        if (label != waypoint.label) {
          waypoint.label = label
          PacketSender.sendWaypointLabel(waypoint)
          mc.player.closeScreen()
        }
      }
      else super.keyTyped(char, code)
    }
  }

  override fun drawScreen(mouseX: Int, mouseY: Int, dt: Float) {
    super.drawScreen(mouseX, mouseY, dt)
    GlStateManager.color(1f, 1f, 1f) // Required under Linux.
    mc.renderEngine.bindTexture(Textures.GUI.Waypoint)
    drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize)
    textField?.drawTextBox()
  }
}
