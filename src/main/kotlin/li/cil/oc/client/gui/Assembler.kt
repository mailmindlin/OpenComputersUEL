package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.client.gui.widget.ProgressBar
import li.cil.oc.client.PacketSender as ClientPacketSender
import li.cil.oc.common.container
import li.cil.oc.common.container.ComponentSlot
import li.cil.oc.common.template.AssemblerTemplates
import li.cil.oc.common.tileentity
import li.cil.oc.util.RenderState
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.inventory.Slot
import net.minecraft.util.text.ITextComponent

class Assembler(
    playerInventory: InventoryPlayer,
    val assembler: tileentity.Assembler
) : DynamicGuiContainer<container.Assembler>(container.Assembler(playerInventory, assembler)) {

    init {
        xSize = 176
        ySize = 192

        for (slot in inventorySlots.inventorySlots) {
            if (slot is ComponentSlot) {
                slot.changeListener = this::onSlotChanged
            }
        }
    }

    private fun onSlotChanged(slot: Slot) {
        runButton?.enabled = canBuild
        runButton?.toggled = !canBuild
        info = validate()
    }

    var info: Triple<Boolean, ITextComponent?, Array<ITextComponent>>? = null

    protected var runButton: ImageButton? = null

    private val progress = addWidget(ProgressBar(28, 92))

    private fun validate(): Triple<Boolean, ITextComponent?, Array<ITextComponent>>? {
        return AssemblerTemplates.select(inventoryContainer.getSlot(0).stack)
            ?.validate(inventoryContainer.otherInventory)
    }

    private val canBuild: Boolean
        get() = !inventoryContainer.isAssembling && (validate()?.first ?: false)

    override fun actionPerformed(button: GuiButton) {
        if (button.id == 0 && canBuild) {
            ClientPacketSender.sendRobotAssemblerStart(assembler)
        }
    }

    override fun initGui() {
        super.initGui()
        runButton = ImageButton(
            0, guiLeft + 7, guiTop + 89, 18, 18,
            Textures.GUI.ButtonRun,
            canToggle = true
        )
        add(buttonList, runButton!!)
    }

    override fun drawSecondaryForegroundLayer(mouseX: Int, mouseY: Int) {
        RenderState.pushAttrib()
        if (!inventoryContainer.isAssembling) {
            val message = when {
                !inventoryContainer.getSlot(0).hasStack -> Localization.Assembler.InsertTemplate
                else -> when (val i = info) {
                    null -> if (inventoryContainer.getSlot(0).hasStack) Localization.Assembler.CollectResult else ""
                    else -> i.second?.unformattedText ?: ""
                }
            }
            fontRenderer.drawString(message, 30, 94, 0x404040)
            if (runButton?.isMouseOver == true) {
                val tooltip = mutableListOf<String>()
                tooltip.add(Localization.Assembler.Run)
                info?.let { (valid, _, warnings) ->
                    if (valid && warnings.isNotEmpty()) {
                        tooltip.addAll(warnings.map { it.unformattedText })
                    }
                }
                copiedDrawHoveringText(tooltip, mouseX - guiLeft, mouseY - guiTop, fontRenderer)
            }
        } else if (isPointInRegion(progress.x, progress.y, progress.width, progress.height, mouseX, mouseY)) {
            val tooltip = mutableListOf<String>()
            val timeRemaining = formatTime(inventoryContainer.assemblyRemainingTime)
            tooltip.add(Localization.Assembler.Progress(inventoryContainer.assemblyProgress, timeRemaining))
            copiedDrawHoveringText(tooltip, mouseX - guiLeft, mouseY - guiTop, fontRenderer)
        }
        RenderState.popAttrib()
    }

    private fun formatTime(seconds: Int): String {
        // Assembly times should not / rarely exceed one hour, so this is good enough.
        return if (seconds < 60) {
            "0:%02d".format(seconds)
        } else {
            "%d:%02d".format(seconds / 60, seconds % 60)
        }
    }

    override fun drawGuiContainerBackgroundLayer(dt: Float, mouseX: Int, mouseY: Int) {
        GlStateManager.color(1f, 1f, 1f) // Required under Linux.
        Textures.bind(Textures.GUI.RobotAssembler)
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize)
        progress.level = if (inventoryContainer.isAssembling) {
            inventoryContainer.assemblyProgress / 100.0
        } else {
            0.0
        }
        drawWidgets()
        drawInventorySlots()
    }

    override fun drawDisabledSlot(slot: ComponentSlot) {}
}
