package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.client.gui.widget.ProgressBar
import li.cil.oc.common.container.Printer as ContainerPrinter
import li.cil.oc.common.container.ComponentSlot
import li.cil.oc.common.tileentity.Printer as TileEntityPrinter
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.player.InventoryPlayer

class Printer(
    playerInventory: InventoryPlayer,
    val printer: TileEntityPrinter
) : DynamicGuiContainer<ContainerPrinter>(ContainerPrinter(playerInventory, printer)) {

    init {
        xSize = 176
        ySize = 166
    }

    private val materialBar = addWidget(object : ProgressBar(40, 21) {
        override val width = 62
        override val height = 12
        override val barTexture get() = Textures.GUI.PrinterMaterial
    })

    private val inkBar = addWidget(object : ProgressBar(40, 53) {
        override val width = 62
        override val height = 12
        override val barTexture get() = Textures.GUI.PrinterInk
    })

    private val progressBar = addWidget(object : ProgressBar(105, 20) {
        override val width = 46
        override val height = 46
        override val barTexture get() = Textures.GUI.PrinterProgress
    })

    override fun initGui() {
        super.initGui()
    }

    override fun drawSecondaryForegroundLayer(mouseX: Int, mouseY: Int) {
        super.drawSecondaryForegroundLayer(mouseX, mouseY)
        fontRenderer.drawString(
            Localization.localizeImmediately(printer.name),
            8, 6, 0x404040
        )
        RenderState.pushAttrib()
        if (isPointInRegion(materialBar.x, materialBar.y, materialBar.width, materialBar.height, mouseX, mouseY)) {
            val tooltip = mutableListOf<String>()
            tooltip.add("${inventoryContainer.amountMaterial()}/${printer.maxAmountMaterial}")
            copiedDrawHoveringText(tooltip, mouseX - guiLeft, mouseY - guiTop, fontRenderer)
        }
        if (isPointInRegion(inkBar.x, inkBar.y, inkBar.width, inkBar.height, mouseX, mouseY)) {
            val tooltip = mutableListOf<String>()
            tooltip.add("${inventoryContainer.amountInk()}/${printer.maxAmountInk}")
            copiedDrawHoveringText(tooltip, mouseX - guiLeft, mouseY - guiTop, fontRenderer)
        }
        RenderState.popAttrib()
    }

    override fun drawGuiContainerBackgroundLayer(dt: Float, mouseX: Int, mouseY: Int) {
        GlStateManager.color(1f, 1f, 1f)
        Textures.bind(Textures.GUI.Printer)
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize)
        materialBar.level = inventoryContainer.amountMaterial() / printer.maxAmountMaterial.toDouble()
        inkBar.level = inventoryContainer.amountInk() / printer.maxAmountInk.toDouble()
        progressBar.level = inventoryContainer.progress()
        drawWidgets()
        drawInventorySlots()
    }

    override fun drawDisabledSlot(slot: ComponentSlot) {}
}
