package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.client.PacketSender as ClientPacketSender
import li.cil.oc.common.container.Server as ContainerServer
import li.cil.oc.common.inventory.ServerInventory
import li.cil.oc.common.tileentity.Rack as TileEntityRack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.player.InventoryPlayer

class Server(
    playerInventory: InventoryPlayer,
    serverInventory: ServerInventory,
    val rack: TileEntityRack? = null,
    val slot: Int = 0
) : DynamicGuiContainer<ContainerServer>(ContainerServer(playerInventory, serverInventory)), li.cil.oc.client.gui.traits.LockedHotbar {

    protected var powerButton: ImageButton? = null

    override val lockedStack get() = serverInventory.container

    override fun actionPerformed(button: GuiButton) {
        if (button.id == 0) {
            rack?.let { t ->
                ClientPacketSender.sendServerPower(t, slot, !inventoryContainer.isRunning)
            }
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, dt: Float) {
        // Close GUI if item is removed from rack.
        rack?.let { t ->
            if (t.getStackInSlot(slot) != serverInventory.container) {
                Minecraft.getMinecraft().displayGuiScreen(null)
                return
            }
        }

        powerButton?.visible = !inventoryContainer.isItem
        powerButton?.toggled = inventoryContainer.isRunning
        super.drawScreen(mouseX, mouseY, dt)
    }

    override fun initGui() {
        super.initGui()
        powerButton = ImageButton(
            0, guiLeft + 48, guiTop + 33, 18, 18,
            Textures.GUI.ButtonPower,
            canToggle = true
        )
        add(buttonList, powerButton!!)
    }

    override fun drawSecondaryForegroundLayer(mouseX: Int, mouseY: Int) {
        super.drawSecondaryForegroundLayer(mouseX, mouseY)
        fontRenderer.drawString(
            Localization.localizeImmediately(serverInventory.name),
            8, 6, 0x404040
        )
        if (powerButton?.isMouseOver == true) {
            val tooltip = mutableListOf<String>()
            val lines = if (inventoryContainer.isRunning) Localization.Computer.TurnOff().lines() else Localization.Computer.TurnOn().lines()
            tooltip.addAll(lines.toList())
            copiedDrawHoveringText(tooltip, mouseX - guiLeft, mouseY - guiTop, fontRenderer)
        }
    }

    override fun drawSecondaryBackgroundLayer() {
        GlStateManager.color(1f, 1f, 1f)
        Textures.bind(Textures.GUI.Server)
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize)
    }
}
