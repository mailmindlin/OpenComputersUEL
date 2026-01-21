package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.CommonSlot as CommonSlot
import li.cil.oc.Tier
import li.cil.oc.common.container.ComponentSlot
import li.cil.oc.common.container.Player
import li.cil.oc.integration.Mods
import li.cil.oc.integration.jei.ModJEI
import li.cil.oc.integration.util.ItemSearch
import li.cil.oc.util.RenderState
import li.cil.oc.util.StackOption
import net.minecraft.client.gui.Gui
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.inventory.Container
import net.minecraft.inventory.Slot
import net.minecraftforge.fml.common.Optional
import org.lwjgl.opengl.GL11

abstract class DynamicGuiContainer<C : Container>(container: C) : CustomGuiContainer<C>(container) {
    protected var hoveredSlot: Slot? = null

    protected var hoveredStackNEI: StackOption = StackOption.EmptyStack

    protected open fun drawSecondaryForegroundLayer(mouseX: Int, mouseY: Int) {
        fontRenderer.drawString(
            Localization.localizeImmediately("container.inventory"),
            8, ySize - 96 + 2, 0x404040
        )
    }

    override fun drawGuiContainerForegroundLayer(mouseX: Int, mouseY: Int) {
        RenderState.pushAttrib()

        drawSecondaryForegroundLayer(mouseX, mouseY)

        for (slot in 0 until inventorySlots.inventorySlots.size) {
            drawSlotHighlight(inventorySlots.inventorySlots[slot])
        }

        RenderState.popAttrib()
    }

    protected open fun drawSecondaryBackgroundLayer() {}

    override fun drawGuiContainerBackgroundLayer(dt: Float, mouseX: Int, mouseY: Int) {
        GlStateManager.color(1f, 1f, 1f, 1f)
        Textures.bind(Textures.GUI.Background)
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize)
        drawSecondaryBackgroundLayer()

        RenderState.makeItBlend()
        GlStateManager.disableLighting()

        drawInventorySlots()
    }

    protected fun drawInventorySlots() {
        GlStateManager.pushMatrix()
        GlStateManager.translate(guiLeft.toFloat(), guiTop.toFloat(), 0f)
        GlStateManager.disableDepth()
        for (slot in 0 until inventorySlots.inventorySlots.size) {
            drawSlotInventory(inventorySlots.inventorySlots[slot])
        }
        GlStateManager.enableDepth()
        GlStateManager.popMatrix()
        RenderState.makeItBlend()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, dt: Float) {
        hoveredSlot = inventorySlots.inventorySlots
            .filterIsInstance<Slot>()
            .firstOrNull { slot ->
                isPointInRegion(slot.xPos, slot.yPos, 16, 16, mouseX, mouseY)
            }
        hoveredStackNEI = ItemSearch.hoveredStack(this, mouseX, mouseY)

        super.drawScreen(mouseX, mouseY, dt)

        if (Mods.JustEnoughItems.isModAvailable) {
            drawJEIHighlights()
        }
    }

    protected open fun drawSlotInventory(slot: Slot) {
        GlStateManager.enableBlend()
        when {
            slot is ComponentSlot && (slot.slot == CommonSlot.None || slot.tier == Tier.None) -> {
                if (!slot.hasStack && slot.xPos >= 0 && slot.yPos >= 0 && slot.tierIcon != null) {
                    drawDisabledSlot(slot)
                }
            }
            else -> {
                zLevel += 1f
                if (!isInPlayerInventory(slot)) {
                    drawSlotBackground(slot.xPos - 1, slot.yPos - 1)
                }
                if (!slot.hasStack) {
                    if (slot is ComponentSlot) {
                        if (slot.tierIcon != null) {
                            Textures.bind(slot.tierIcon)
                            Gui.drawModalRectWithCustomSizedTexture(slot.xPos, slot.yPos, 0f, 0f, 16, 16, 16f, 16f)
                        }
                        if (slot.hasBackground) {
                            Textures.bind(slot.backgroundLocation)
                            Gui.drawModalRectWithCustomSizedTexture(slot.xPos, slot.yPos, 0f, 0f, 16, 16, 16f, 16f)
                        }
                    }
                    zLevel -= 1f
                }
            }
        }
        GlStateManager.disableBlend()
    }

    protected open fun drawSlotHighlight(slot: Slot) {
        if (mc.player.inventory.itemStack.isEmpty) {
            when {
                slot is ComponentSlot && (slot.slot == CommonSlot.None || slot.tier == Tier.None) -> {
                    // Ignore
                }
                else -> {
                    val currentIsInPlayerInventory = isInPlayerInventory(slot)
                    val drawHighlight = when (val hovered = hoveredSlot) {
                        null -> when (val stack = hoveredStackNEI) {
                            is StackOption.SomeStack -> !currentIsInPlayerInventory && isSelectiveSlot(slot) && slot.isItemValid(stack.stack)
                            else -> false
                        }
                        else -> {
                            val hoveredIsInPlayerInventory = isInPlayerInventory(hovered)
                            (currentIsInPlayerInventory != hoveredIsInPlayerInventory) &&
                                    ((currentIsInPlayerInventory && slot.hasStack && isSelectiveSlot(hovered) && hovered.isItemValid(slot.stack)) ||
                                            (hoveredIsInPlayerInventory && hovered.hasStack && isSelectiveSlot(slot) && slot.isItemValid(hovered.stack)))
                        }
                    }
                    if (drawHighlight) {
                        zLevel += 100f
                        drawGradientRect(
                            slot.xPos, slot.yPos,
                            slot.xPos + 16, slot.yPos + 16,
                            0x80FFFFFF.toInt(), 0x80FFFFFF.toInt()
                        )
                        zLevel -= 100f
                    }
                }
            }
        }
    }

    private fun isSelectiveSlot(slot: Slot): Boolean = when (slot) {
        is ComponentSlot -> slot.slot != CommonSlot.Any && slot.slot != CommonSlot.Tool
        else -> false
    }

    protected open fun drawDisabledSlot(slot: ComponentSlot) {
        GlStateManager.color(1f, 1f, 1f, 1f)
        Textures.bind(slot.tierIcon)
        Gui.drawModalRectWithCustomSizedTexture(slot.xPos, slot.yPos, 0f, 0f, 16, 16, 16f, 16f)
    }

    protected open fun drawSlotBackground(x: Int, y: Int) {
        GlStateManager.color(1f, 1f, 1f, 1f)
        Textures.bind(Textures.GUI.Slot)
        val t = Tessellator.getInstance()
        val r = t.buffer
        r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)
        r.pos((x).toDouble(), (y + 18).toDouble(), (zLevel + 1).toDouble()).tex(0.0, 1.0).endVertex()
        r.pos((x + 18).toDouble(), (y + 18).toDouble(), (zLevel + 1).toDouble()).tex(1.0, 1.0).endVertex()
        r.pos((x + 18).toDouble(), y.toDouble(), (zLevel + 1).toDouble()).tex(1.0, 0.0).endVertex()
        r.pos(x.toDouble(), y.toDouble(), (zLevel + 1).toDouble()).tex(0.0, 0.0).endVertex()
        t.draw()
    }

    private fun isInPlayerInventory(slot: Slot): Boolean = when (val c = inventoryContainer) {
        is Player -> slot.inventory == c.playerInventory
        else -> false
    }

    override fun onGuiClosed() {
        super.onGuiClosed()
        if (Mods.JustEnoughItems.isModAvailable) {
            resetJEIHighlights()
        }
    }

    @Optional.Method(modid = Mods.IDs.JustEnoughItems)
    private fun drawJEIHighlights() {
        ModJEI.runtime?.let { runtime ->
            val overlay = runtime.itemListOverlay
            when (val hovered = hoveredSlot) {
                null -> overlay.highlightStacks(emptyList())
                else -> {
                    if (!isInPlayerInventory(hovered) && isSelectiveSlot(hovered)) {
                        overlay.highlightStacks(overlay.visibleStacks.filter { hovered.isItemValid(it) })
                    } else {
                        overlay.highlightStacks(emptyList())
                    }
                }
            }
        }
    }

    @Optional.Method(modid = Mods.IDs.JustEnoughItems)
    private fun resetJEIHighlights() {
        ModJEI.runtime?.itemListOverlay?.highlightStacks(emptyList())
    }
}
