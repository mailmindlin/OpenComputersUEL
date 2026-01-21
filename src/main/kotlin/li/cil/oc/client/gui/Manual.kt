package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.client.gui.traits.Window
import li.cil.oc.client.renderer.markdown.Document
import li.cil.oc.client.renderer.markdown.segment.InteractiveSegment
import li.cil.oc.client.renderer.markdown.segment.Segment
import li.cil.oc.client.Manual as ManualAPI
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.util.ResourceLocation
import org.lwjgl.input.Mouse

class Manual : GuiScreen(), Window {
    companion object {
        const val documentMaxWidth = 230
        const val documentMaxHeight = 176
        const val scrollPosX = 244
        const val scrollPosY = 6
        const val scrollWidth = 6
        const val scrollHeight = 180
        const val tabPosX = -23
        const val tabPosY = 7
        const val tabWidth = 23
        const val tabHeight = 26
        const val maxTabsPerSide = 7
    }

    override val windowWidth: Int
        get() = 256
    override val windowHeight: Int
        get() = 192

    override val backgroundImage: ResourceLocation
        get() = Textures.GUI.Manual

    // Implement variables from Window trait
    override var guiLeft: Int = 0
    override var guiTop: Int = 0
    override var xSize: Int = 0
    override var ySize: Int = 0

    var isDragging = false
    var document: Segment? = null
    var documentHeight = 0
    var currentSegment: InteractiveSegment? = null
    protected var scrollButton: ImageButton? = null

    private val canScroll: Boolean
        get() = maxOffset > 0

    private val offset: Int
        get() = ManualAPI.history.top.offset

    private val maxOffset: Int
        get() = documentHeight - documentMaxHeight

    fun resolveLink(path: String, current: String): String =
        if (path.startsWith("/")) path
        else {
            val splitAt = current.lastIndexOf('/')
            if (splitAt >= 0) current.substring(0, splitAt) + "/" + path
            else path
        }

    fun refreshPage() {
        val content = ManualAPI.contentFor(ManualAPI.history.top.path)
            ?: listOf("Document not found: ${ManualAPI.history.top.path}")
        document = Document.parse(content)
        documentHeight = Document.height(document, documentMaxWidth, fontRenderer)
        scrollTo(offset)
    }

    fun pushPage(path: String) {
        if (path != ManualAPI.history.top.path) {
            ManualAPI.history.push(ManualAPI.History(path))
            refreshPage()
        }
    }

    fun popPage() {
        if (ManualAPI.history.size > 1) {
            ManualAPI.history.pop()
            refreshPage()
        } else {
            Minecraft.getMinecraft().player.closeScreen()
        }
    }

    override fun actionPerformed(button: GuiButton) {
        if (button.id >= 0 && button.id < ManualAPI.tabs.size) {
            ManualAPI.navigate(ManualAPI.tabs[button.id].path)
        }
    }

    override fun initGui() {
        super.initGui()

        for ((i, tab) in ManualAPI.tabs.withIndex()) {
            if (i < maxTabsPerSide) {
                val x = guiLeft + tabPosX
                val y = guiTop + tabPosY + i * (tabHeight - 1)
                add(buttonList, ImageButton(i, x, y, tabWidth, tabHeight, Textures.GUI.ManualTab))
            }
        }

        scrollButton = ImageButton(-1, guiLeft + scrollPosX, guiTop + scrollPosY, 6, 13, Textures.GUI.ButtonScroll)
        add(buttonList, scrollButton!!)

        refreshPage()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, dt: Float) {
        super.drawScreen(mouseX, mouseY, dt)

        scrollButton?.enabled = canScroll
        scrollButton?.hoverOverride = isDragging

        for ((i, tab) in ManualAPI.tabs.withIndex()) {
            if (i < maxTabsPerSide) {
                val button = buttonList[i] as ImageButton
                GlStateManager.pushMatrix()
                GlStateManager.translate((button.x + 5).toFloat(), (button.y + 5).toFloat(), zLevel)
                tab.renderer.render()
                GlStateManager.popMatrix()
            }
        }

        currentSegment = Document.render(document, guiLeft + 8, guiTop + 8, documentMaxWidth, documentMaxHeight, offset, fontRenderer, mouseX, mouseY)

        if (!isDragging) {
            currentSegment?.tooltip?.let { text ->
                if (text.isNotEmpty()) {
                    drawHoveringText(Localization.localizeImmediately(text).lines().toList(), mouseX, mouseY, fontRenderer)
                }
            }
        }

        if (!isDragging) {
            for ((i, tab) in ManualAPI.tabs.withIndex()) {
                if (i < maxTabsPerSide) {
                    val button = buttonList[i] as ImageButton
                    if (mouseX > button.x && mouseX < button.x + tabWidth && mouseY > button.y && mouseY < button.y + tabHeight) {
                        tab.tooltip?.let { text ->
                            drawHoveringText(Localization.localizeImmediately(text).lines().toList(), mouseX, mouseY, fontRenderer)
                        }
                    }
                }
            }
        }

        if (canScroll && (isCoordinateOverScrollBar(mouseX - guiLeft, mouseY - guiTop) || isDragging)) {
            drawHoveringText(listOf("${100 * offset / maxOffset}%"), guiLeft + scrollPosX + scrollWidth, scrollButton!!.y + scrollButton!!.height + 1, fontRenderer)
        }
    }

    override fun keyTyped(char: Char, code: Int) {
        when (code) {
            mc.gameSettings.keyBindJump.keyCode -> popPage()
            mc.gameSettings.keyBindInventory.keyCode -> mc.player.closeScreen()
            else -> super.keyTyped(char, code)
        }
    }

    override fun handleMouseInput() {
        super.handleMouseInput()
        if (Mouse.hasWheel() && Mouse.getEventDWheel() != 0) {
            if (Mouse.getEventDWheel().toDouble().sign < 0) scrollDown()
            else scrollUp()
        }
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, button: Int) {
        super.mouseClicked(mouseX, mouseY, button)

        when {
            canScroll && button == 0 && isCoordinateOverScrollBar(mouseX - guiLeft, mouseY - guiTop) -> {
                isDragging = true
                scrollMouse(mouseY)
            }
            button == 0 -> currentSegment?.onMouseClick(mouseX, mouseY)
            button == 1 -> popPage()
        }
    }

    override fun mouseClickMove(mouseX: Int, mouseY: Int, lastButtonClicked: Int, timeSinceMouseClick: Long) {
        super.mouseClickMove(mouseX, mouseY, lastButtonClicked, timeSinceMouseClick)
        if (isDragging) {
            scrollMouse(mouseY)
        }
    }

    override fun mouseReleased(mouseX: Int, mouseY: Int, button: Int) {
        super.mouseReleased(mouseX, mouseY, button)
        if (button == 0) {
            isDragging = false
        }
    }

    private fun scrollMouse(mouseY: Int) {
        scrollTo(((mouseY - guiTop - scrollPosY - 6.5) * maxOffset / (scrollHeight - 13.0)).toInt())
    }

    private fun scrollUp() = scrollTo(offset - Document.lineHeight(fontRenderer) * 3)

    private fun scrollDown() = scrollTo(offset + Document.lineHeight(fontRenderer) * 3)

    private fun scrollTo(row: Int) {
        ManualAPI.history.top.offset = row.coerceIn(0, maxOffset)
        val yMin = guiTop + scrollPosY
        scrollButton!!.y = if (maxOffset > 0) {
            yMin + (scrollHeight - 13) * offset / maxOffset
        } else {
            yMin
        }
    }

    private fun isCoordinateOverScrollBar(x: Int, y: Int) =
        x > scrollPosX && x < scrollPosX + scrollWidth &&
                y >= scrollPosY && y < scrollPosY + scrollHeight
}
