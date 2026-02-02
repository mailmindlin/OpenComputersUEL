package li.cil.oc.client.gui

import li.cil.oc.client.Textures
import li.cil.oc.client.gui.traits.LockedHotbar
import li.cil.oc.common.Tier
import li.cil.oc.common.container.Database as ContainerDatabase
import li.cil.oc.common.inventory.DatabaseInventory
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.player.InventoryPlayer

class Database(playerInventory: InventoryPlayer, val databaseInventory: DatabaseInventory) : DynamicGuiContainer<ContainerDatabase>(ContainerDatabase(playerInventory, databaseInventory)), LockedHotbar {
  init {
    ySize = 256
  }

  override val lockedStack get() = databaseInventory.container

  override fun drawSecondaryForegroundLayer(mouseX: Int, mouseY: Int) {}

  override fun drawGuiContainerBackgroundLayer(dt: Float, mouseX: Int, mouseY: Int) {
    GlStateManager.color(1f, 1f, 1f, 1f)
    Textures.bind(Textures.GUI.Database)
    drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize)

    if (databaseInventory.tier > Tier.One) {
      Textures.bind(Textures.GUI.Database1)
      drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize)
    }

    if (databaseInventory.tier > Tier.Two) {
      Textures.bind(Textures.GUI.Database2)
      drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize)
    }
  }

  override fun checkHotbarKeys(keyCode: Int): Boolean = super<LockedHotbar>.checkHotbarKeys(keyCode)
}
