package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.api.internal.TextBuffer
import li.cil.oc.client.Textures
import li.cil.oc.client.gui.widget.ProgressBar
import li.cil.oc.client.renderer.TextBufferRenderCache
import li.cil.oc.client.renderer.gui.BufferRenderer
import li.cil.oc.client.PacketSender as ClientPacketSender
import li.cil.oc.common.container.Robot as ContainerRobot
import li.cil.oc.common.tileentity.Robot as TileEntityRobot
import li.cil.oc.integration.opencomputers.DriverKeyboard
import li.cil.oc.util.RenderState
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.entity.player.InventoryPlayer
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11
import kotlin.math.min
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sign

class Robot(
    playerInventory: InventoryPlayer,
    val robot: TileEntityRobot
) : DynamicGuiContainer<ContainerRobot>(ContainerRobot(playerInventory, robot)), li.cil.oc.client.gui.traits.InputBuffer {

    override val buffer: TextBuffer? = robot.components
        .filterNotNull()
        .filterIsInstance<TextBuffer>()
        .firstOrNull()

    override val hasKeyboard: Boolean = robot.info.components
        .map { Driver.driverFor(it, robot.javaClass) }
        .contains(DriverKeyboard)

    private val withScreenHeight = 256
    private val noScreenHeight = 108

    private val deltaY = if (buffer != null) 0 else withScreenHeight - noScreenHeight

    init {
        xSize = 256
        ySize = 256 - deltaY
    }

    protected var powerButton: ImageButton? = null
    protected var scrollButton: ImageButton? = null

    // Scroll offset for robot inventory.
    private var inventoryOffset = 0
    private var isDragging = false

    private val canScroll get() = robot.inventorySize > 16
    private val maxOffset get() = robot.inventorySize / 4 - 4

    private val slotSize = 18

    private val maxBufferWidth = 240.0
    private val maxBufferHeight = 140.0

    private val bufferRenderWidth: Double
        get() = (TextBufferRenderCache.renderer.charRenderWidth * Settings.screenResolutionsByTier[0].width).toDouble().coerceAtMost(maxBufferWidth)

    private val bufferRenderHeight: Double
        get() = (TextBufferRenderCache.renderer.charRenderHeight * Settings.screenResolutionsByTier[0].height).toDouble().coerceAtMost(maxBufferHeight)

    override val bufferX: Int
        get() = (8 + (maxBufferWidth - bufferRenderWidth) / 2).toInt()

    override val bufferY: Int
        get() = (8 + (maxBufferHeight - bufferRenderHeight) / 2).toInt()

    private val inventoryX = 169
    private val inventoryY = 155 - deltaY

    private val scrollX = inventoryX + slotSize * 4 + 2
    private val scrollY = inventoryY
    private val scrollWidth = 8
    private val scrollHeight = 94

    private val power = addWidget(ProgressBar(26, 156 - deltaY))

    private val selectionSize = 20
    private val selectionsStates = 17
    private val selectionStepV = 1 / selectionsStates.toDouble()

    override fun actionPerformed(button: GuiButton) {
        if (button.id == 0) {
            ClientPacketSender.sendComputerPower(robot, !robot.isRunning)
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, dt: Float) {
        powerButton?.toggled = robot.isRunning
        scrollButton?.enabled = canScroll
        scrollButton?.hoverOverride = isDragging
        if (robot.inventorySize < 16 + inventoryOffset * 4) {
            scrollTo(0)
        }
        super.drawScreen(mouseX, mouseY, dt)
    }

    override fun initGui() {
        super.initGui()
        powerButton = ImageButton(
            0, guiLeft + 5, guiTop + 153 - deltaY, 18, 18,
            Textures.GUI.ButtonPower,
            canToggle = true
        )
        scrollButton = ImageButton(
            1, guiLeft + scrollX + 1, guiTop + scrollY + 1, 6, 13,
            Textures.GUI.ButtonScroll
        )
        add(buttonList, powerButton!!)
        add(buttonList, scrollButton!!)
    }

    override fun drawBuffer() {
        buffer?.let { buf ->
            GlStateManager.translate(bufferX.toFloat(), bufferY.toFloat(), 0f)
            RenderState.disableEntityLighting()
            GlStateManager.pushMatrix()
            GlStateManager.translate(-3f, -3f, 0f)
            GlStateManager.color(1f, 1f, 1f, 1f)
            BufferRenderer.drawBackground()
            GlStateManager.popMatrix()
            RenderState.makeItBlend()
            val scaleX = bufferRenderWidth / buf.renderWidth()
            val scaleY = bufferRenderHeight / buf.renderHeight()
            val scale = min(scaleX, scaleY)
            if (scaleX > scale) {
                GlStateManager.translate((buf.renderWidth() * (scaleX - scale) / 2).toFloat(), 0f, 0f)
            } else if (scaleY > scale) {
                GlStateManager.translate(0f, (buf.renderHeight() * (scaleY - scale) / 2).toFloat(), 0f)
            }
            GlStateManager.scale(scale, scale, scale)
            GlStateManager.scale(this.scale, this.scale, 1.0)
            BufferRenderer.drawText(buf)
        }
    }

    override fun drawSecondaryForegroundLayer(mouseX: Int, mouseY: Int) {
        drawBufferLayer()
        RenderState.pushAttrib()
        if (isPointInRegion(power.x, power.y, power.width, power.height, mouseX, mouseY)) {
            val tooltip = mutableListOf<String>()
            val format = Localization.Computer.Power() + ": %d%% (%d/%d)"
            tooltip.add(
                format.format(
                    ((robot.globalBuffer / robot.globalBufferSize) * 100).toInt(),
                    robot.globalBuffer.toInt(),
                    robot.globalBufferSize.toInt()
                )
            )
            copiedDrawHoveringText(tooltip, mouseX - guiLeft, mouseY - guiTop, fontRenderer)
        }
        if (powerButton?.isMouseOver == true) {
            val tooltip = mutableListOf<String>()
            val lines = if (robot.isRunning) Localization.Computer.TurnOff().lines() else Localization.Computer.TurnOn().lines()
            tooltip.addAll(lines.toList())
            copiedDrawHoveringText(tooltip, mouseX - guiLeft, mouseY - guiTop, fontRenderer)
        }
        RenderState.popAttrib()
    }

    override fun drawGuiContainerBackgroundLayer(dt: Float, mouseX: Int, mouseY: Int) {
        GlStateManager.color(1f, 1f, 1f)
        if (buffer != null) {
            Textures.bind(Textures.GUI.Robot)
        } else {
            Textures.bind(Textures.GUI.RobotNoScreen)
        }
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize)
        power.level = robot.globalBuffer / robot.globalBufferSize
        drawWidgets()
        if (robot.inventorySize > 0) {
            drawSelection()
        }

        drawInventorySlots()
    }

    // No custom slots, we just extend DynamicGuiContainer for the highlighting.
    override fun drawSlotBackground(x: Int, y: Int) {}

    override fun keyTyped(char: Char, code: Int) {
        if (code == Keyboard.KEY_ESCAPE) {
            super.keyTyped(char, code)
        }
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, button: Int) {
        super.mouseClicked(mouseX, mouseY, button)
        if (canScroll && button == 0 && isCoordinateOverScrollBar(mouseX - guiLeft, mouseY - guiTop)) {
            isDragging = true
            scrollMouse(mouseY)
        }
    }

    override fun mouseReleased(mouseX: Int, mouseY: Int, button: Int) {
        super.mouseReleased(mouseX, mouseY, button)
        if (button == 0) {
            isDragging = false
        }
    }

    override fun mouseClickMove(mouseX: Int, mouseY: Int, lastButtonClicked: Int, timeSinceMouseClick: Long) {
        super.mouseClickMove(mouseX, mouseY, lastButtonClicked, timeSinceMouseClick)
        if (isDragging) {
            scrollMouse(mouseY)
        }
    }

    private fun scrollMouse(mouseY: Int) {
        scrollTo(round((mouseY - guiTop - scrollY + 1 - 6.5) * maxOffset / (scrollHeight - 13.0)).toInt())
    }

    override fun handleMouseInput() {
        super.handleMouseInput()
        if (Mouse.hasWheel() && Mouse.getEventDWheel() != 0) {
            val mouseX = Mouse.getEventX() * width / mc.displayWidth - guiLeft
            val mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1 - guiTop
            if (isCoordinateOverInventory(mouseX, mouseY) || isCoordinateOverScrollBar(mouseX, mouseY)) {
                if (Mouse.getEventDWheel().toDouble().sign < 0) scrollDown()
                else scrollUp()
            }
        }
    }

    private fun isCoordinateOverInventory(x: Int, y: Int): Boolean =
        x >= inventoryX && x < inventoryX + slotSize * 4 &&
                y >= inventoryY && y < inventoryY + slotSize * 4

    private fun isCoordinateOverScrollBar(x: Int, y: Int): Boolean =
        x > scrollX && x < scrollX + scrollWidth &&
                y >= scrollY && y < scrollY + scrollHeight

    private fun scrollUp() = scrollTo(inventoryOffset - 1)

    private fun scrollDown() = scrollTo(inventoryOffset + 1)

    private fun scrollTo(row: Int) {
        inventoryOffset = max(0, min(maxOffset, row))
        for (index in 4 until 68) {
            val slot = inventorySlots.getSlot(index)
            val displayIndex = index - inventoryOffset * 4 - 4
            if (displayIndex >= 0 && displayIndex < 16) {
                slot.xPos = 1 + inventoryX + (displayIndex % 4) * slotSize
                slot.yPos = 1 + inventoryY + (displayIndex / 4) * slotSize
            } else {
                // Hide the rest!
                slot.xPos = -10000
                slot.yPos = -10000
            }
        }
        val yMin = guiTop + scrollY + 1
        scrollButton?.y = if (maxOffset > 0) {
            yMin + (scrollHeight - 15) * inventoryOffset / maxOffset
        } else {
            yMin
        }
    }

    override fun changeSize(w: Double, h: Double, recompile: Boolean): Double {
        val bw = w * TextBufferRenderCache.renderer.charRenderWidth
        val bh = h * TextBufferRenderCache.renderer.charRenderHeight
        val scaleX = min(bufferRenderWidth / bw, 1.0)
        val scaleY = min(bufferRenderHeight / bh, 1.0)
        if (recompile) {
            BufferRenderer.compileBackground(bufferRenderWidth.toInt(), bufferRenderHeight.toInt(), forRobot = true)
        }
        return min(scaleX, scaleY)
    }

    private fun drawSelection() {
        val slot = robot.selectedSlot - inventoryOffset * 4
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
