package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.client.gui.widget.ProgressBar
import li.cil.oc.client.renderer.TextBufferRenderCache
import li.cil.oc.client.renderer.font.TextBufferRenderData
import li.cil.oc.client.PacketSender as ClientPacketSender
import li.cil.oc.common.container.Drone as ContainerDrone
import li.cil.oc.common.entity.Drone as EntityDrone
import li.cil.oc.util.PackedColor
import li.cil.oc.util.RenderState
import li.cil.oc.util.TextBuffer
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.entity.player.InventoryPlayer
import org.lwjgl.opengl.GL11

class Drone(
    playerInventory: InventoryPlayer,
    val drone: EntityDrone
) : DynamicGuiContainer<ContainerDrone>(ContainerDrone(playerInventory, drone)), li.cil.oc.client.gui.traits.DisplayBuffer {

    init {
        xSize = 176
        ySize = 148
    }

    protected var powerButton: ImageButton? = null

    private val buffer = TextBuffer(20, 2, PackedColor.SingleBitFormat(0x33FF33))
    private val bufferRenderer = object : TextBufferRenderData {
        private var _dirty = true

        override var dirty: Boolean
            get() = _dirty
            set(value) {
                _dirty = value
            }

        override val data get() = buffer

        override fun viewport(): Pair<Int, Int> = buffer.size
    }

    override val bufferX = 9
    override val bufferY = 9
    override val bufferColumns = 80
    override val bufferRows = 16

    private val inventoryX = 97
    private val inventoryY = 7

    private val power = addWidget(ProgressBar(28, 48))

    private val selectionSize = 20
    private val selectionsStates = 17
    private val selectionStepV = 1 / selectionsStates.toDouble()

    override fun actionPerformed(button: GuiButton) {
        if (button.id == 0) {
            ClientPacketSender.sendDronePower(drone, !drone.isRunning)
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, dt: Float) {
        powerButton?.toggled = drone.isRunning
        bufferRenderer.dirty = drone.statusText.lines().withIndex().any { (i, line) ->
            buffer.set(0, i, line, vertical = false)
        }
        super.drawScreen(mouseX, mouseY, dt)
    }

    override fun initGui() {
        super.initGui()
        powerButton = ImageButton(
            0, guiLeft + 7, guiTop + 45, 18, 18,
            Textures.GUI.ButtonPower,
            canToggle = true
        )
        add(buttonList, powerButton!!)
    }

    override fun drawBuffer() {
        GlStateManager.translate(bufferX.toFloat(), bufferY.toFloat(), 0f)
        RenderState.disableEntityLighting()
        RenderState.makeItBlend()
        GlStateManager.scale(scale, scale, 1.0)
        RenderState.pushAttrib()
        GlStateManager.depthMask(false)
        GlStateManager.color(0.5f, 0.5f, 1f)
        TextBufferRenderCache.render(bufferRenderer)
        RenderState.popAttrib()
    }

    override fun changeSize(w: Double, h: Double, recompile: Boolean): Double = 2.0

    override fun drawSecondaryForegroundLayer(mouseX: Int, mouseY: Int) {
        drawBufferLayer()
        RenderState.pushAttrib()
        if (isPointInRegion(power.x, power.y, power.width, power.height, mouseX, mouseY)) {
            val tooltip = mutableListOf<String>()
            val format = Localization.Computer.Power() + ": %d%% (%d/%d)"
            tooltip.add(
                format.format(
                    drone.globalBuffer * 100 / maxOf(drone.globalBufferSize, 1),
                    drone.globalBuffer,
                    drone.globalBufferSize
                )
            )
            copiedDrawHoveringText(tooltip, mouseX - guiLeft, mouseY - guiTop, fontRenderer)
        }
        if (powerButton?.isMouseOver == true) {
            val tooltip = mutableListOf<String>()
            val lines = if (drone.isRunning) Localization.Computer.TurnOff().lines() else Localization.Computer.TurnOn().lines()
            tooltip.addAll(lines.toList())
            copiedDrawHoveringText(tooltip, mouseX - guiLeft, mouseY - guiTop, fontRenderer)
        }
        RenderState.popAttrib()
    }

    override fun drawGuiContainerBackgroundLayer(dt: Float, mouseX: Int, mouseY: Int) {
        GlStateManager.color(1f, 1f, 1f)
        Textures.bind(Textures.GUI.Drone)
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize)
        power.level = drone.globalBuffer.toDouble() / maxOf(drone.globalBufferSize.toDouble(), 1.0)
        drawWidgets()
        if (drone.mainInventory.sizeInventory > 0) {
            drawSelection()
        }

        drawInventorySlots()
    }

    // No custom slots, we just extend DynamicGuiContainer for the highlighting.
    override fun drawSlotBackground(x: Int, y: Int) {}

    private fun drawSelection() {
        val slot = drone.selectedSlot
        if (slot >= 0 && slot < 16) {
            RenderState.makeItBlend()
            Textures.bind(Textures.GUI.RobotSelection)
            val now = System.currentTimeMillis() / 1000.0
            val offsetV = ((now - now.toInt()) * selectionsStates).toInt() * selectionStepV
            val x = guiLeft + inventoryX - 1 + (slot % 4) * (selectionSize - 2)
            val y = guiTop + inventoryY - 1 + (slot / 4) * (selectionSize - 2)

            val t = Tessellator.getInstance()
            val r = t.buffer
            r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)
            r.pos(x.toDouble(), y.toDouble(), zLevel.toDouble()).tex(0.0, offsetV).endVertex()
            r.pos(x.toDouble(), (y + selectionSize).toDouble(), zLevel.toDouble()).tex(0.0, offsetV + selectionStepV).endVertex()
            r.pos((x + selectionSize).toDouble(), (y + selectionSize).toDouble(), zLevel.toDouble()).tex(1.0, offsetV + selectionStepV).endVertex()
            r.pos((x + selectionSize).toDouble(), y.toDouble(), zLevel.toDouble()).tex(1.0, offsetV).endVertex()
            t.draw()
        }
    }
}
