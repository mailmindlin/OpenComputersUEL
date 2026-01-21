package li.cil.oc.client.gui

import java.text.DecimalFormat
import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.common.container.Relay as ContainerRelay
import li.cil.oc.common.tileentity.Relay as TileEntityRelay
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.entity.player.InventoryPlayer
import org.lwjgl.opengl.GL11
import org.lwjgl.util.Rectangle

class Relay(
    playerInventory: InventoryPlayer,
    val relay: TileEntityRelay
) : DynamicGuiContainer<ContainerRelay>(ContainerRelay(playerInventory, relay)) {

    private val format = DecimalFormat("#.##hz")

    val tabPosition = Rectangle(xSize, 10, 23, 26)

    override fun drawSecondaryBackgroundLayer() {
        super.drawSecondaryBackgroundLayer()

        // Tab background.
        GlStateManager.color(1f, 1f, 1f, 1f)
        Minecraft.getMinecraft().textureManager.bindTexture(Textures.GUI.UpgradeTab)
        val x = windowX + tabPosition.x
        val y = windowY + tabPosition.y
        val w = tabPosition.width
        val h = tabPosition.height
        val t = Tessellator.getInstance()
        val r = t.buffer
        r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)
        r.pos(x.toDouble(), (y + h).toDouble(), zLevel.toDouble()).tex(0.0, 1.0).endVertex()
        r.pos((x + w).toDouble(), (y + h).toDouble(), zLevel.toDouble()).tex(1.0, 1.0).endVertex()
        r.pos((x + w).toDouble(), y.toDouble(), zLevel.toDouble()).tex(1.0, 0.0).endVertex()
        r.pos(x.toDouble(), y.toDouble(), zLevel.toDouble()).tex(0.0, 0.0).endVertex()
        t.draw()
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, button: Int) {
        // So MC doesn't throw away the item in the upgrade slot when we're trying to pick it up...
        val originalWidth = xSize
        try {
            xSize += tabPosition.width
            super.mouseClicked(mouseX, mouseY, button)
        } finally {
            xSize = originalWidth
        }
    }

    override fun mouseReleased(mouseX: Int, mouseY: Int, button: Int) {
        // So MC doesn't throw away the item in the upgrade slot when we're trying to pick it up...
        val originalWidth = xSize
        try {
            xSize += tabPosition.width
            super.mouseReleased(mouseX, mouseY, button)
        } finally {
            xSize = originalWidth
        }
    }

    override fun drawSecondaryForegroundLayer(mouseX: Int, mouseY: Int) {
        super.drawSecondaryForegroundLayer(mouseX, mouseY)
        fontRenderer.drawString(
            Localization.localizeImmediately(relay.name),
            8, 6, 0x404040
        )

        fontRenderer.drawString(
            Localization.Switch.TransferRate(),
            14, 20, 0x404040
        )
        fontRenderer.drawString(
            Localization.Switch.PacketsPerCycle(),
            14, 39, 0x404040
        )
        fontRenderer.drawString(
            Localization.Switch.QueueSize(),
            14, 58, 0x404040
        )

        fontRenderer.drawString(
            format.format(20f / inventoryContainer.relayDelay()),
            108, 20, 0x404040
        )
        fontRenderer.drawString(
            "${inventoryContainer.packetsPerCycleAvg()} / ${inventoryContainer.relayAmount()}",
            108, 39,
            thresholdBasedColor(
                inventoryContainer.packetsPerCycleAvg(),
                kotlin.math.ceil(inventoryContainer.relayAmount() / 2f).toInt(),
                inventoryContainer.relayAmount()
            )
        )
        fontRenderer.drawString(
            "${inventoryContainer.queueSize()} / ${inventoryContainer.maxQueueSize()}",
            108, 58,
            thresholdBasedColor(
                inventoryContainer.queueSize(),
                inventoryContainer.maxQueueSize() / 2,
                inventoryContainer.maxQueueSize()
            )
        )
    }

    private fun thresholdBasedColor(value: Int, yellow: Int, red: Int): Int {
        return when {
            value < yellow -> 0x009900
            value < red -> 0x999900
            else -> 0x990000
        }
    }
}
