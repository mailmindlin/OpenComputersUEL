package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.client.PacketSender as ClientPacketSender
import li.cil.oc.common.container.Rack as ContainerRack
import li.cil.oc.common.tileentity.Rack as TileEntityRack
import li.cil.oc.util.RenderState
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.util.EnumFacing
import org.lwjgl.opengl.GL11

class Rack(playerInventory: InventoryPlayer, val rack: TileEntityRack) :
    DynamicGuiContainer<ContainerRack>(ContainerRack(playerInventory, rack)) {

    init {
        ySize = 210
    }

    companion object {
        val busMasterBlankUVs = intArrayOf(195, 14, 3, 5)
        val busMasterPresentUVs = intArrayOf(194, 20, 5, 5)
        val busSlaveBlankUVs = intArrayOf(195, 1, 3, 4)
        val busSlavePresentUVs = intArrayOf(194, 6, 5, 4)

        val connectorMasterUVs = intArrayOf(194, 26, 1, 3)
        val connectorSlaveUVs = intArrayOf(194, 11, 1, 2)

        val hoverMasterSize = intArrayOf(3, 3)
        val hoverSlaveSize = intArrayOf(3, 2)

        val wireMasterUVs = arrayOf(
            intArrayOf(186, 16, 6, 3),
            intArrayOf(186, 20, 6, 3),
            intArrayOf(186, 24, 6, 3),
            intArrayOf(186, 28, 6, 3),
            intArrayOf(186, 32, 6, 3)
        )
        val wireSlaveUVs = arrayOf(
            intArrayOf(186, 1, 6, 2),
            intArrayOf(186, 4, 6, 2),
            intArrayOf(186, 7, 6, 2),
            intArrayOf(186, 10, 6, 2),
            intArrayOf(186, 13, 6, 2)
        )

        val busStart = arrayOf(
            intArrayOf(45, 22),
            intArrayOf(56, 22),
            intArrayOf(67, 22),
            intArrayOf(78, 22),
            intArrayOf(89, 22)
        )

        const val busGap = 3

        val connectorStart = arrayOf(
            intArrayOf(37, 23),
            intArrayOf(37, 43),
            intArrayOf(37, 63),
            intArrayOf(37, 83)
        )

        const val connectorGap = 2

        val relayModeUVs = intArrayOf(195, 30, 4, 2)

        val wireRelay = arrayOf(
            intArrayOf(50, 104),
            intArrayOf(61, 104),
            intArrayOf(72, 104),
            intArrayOf(83, 104)
        )
    }

    private val busToSide = EnumFacing.values().filter { it != EnumFacing.SOUTH }.toTypedArray()
    private val sideToBus = busToSide.withIndex().associate { (index, side) -> side to index }

    var relayButton: ImageButton? = null

    // bus -> mountable -> connectable
    var wireButtons = Array(rack.sizeInventory) {
        Array(4) {
            arrayOfNulls<ImageButton>(5)
        }
    }

    private fun sideName(side: EnumFacing) = when (side) {
        EnumFacing.UP -> Localization.Rack.Top()
        EnumFacing.DOWN -> Localization.Rack.Bottom()
        EnumFacing.WEST -> Localization.Rack.Right()
        EnumFacing.EAST -> Localization.Rack.Left()
        EnumFacing.NORTH -> Localization.Rack.Back()
        else -> Localization.Rack.None()
    }

    private fun encodeButtonId(mountable: Int, connectable: Int, bus: Int): Int {
        // +1 to offset for relay button
        return 1 + mountable * 4 * 5 + connectable * 5 + bus
    }

    private data class ButtonPosition(val mountable: Int, val connectable: Int, val bus: Int)

    private fun decodeButtonId(buttonId: Int): ButtonPosition {
        // -1 to offset for relay button
        val bus = (buttonId - 1) % 5
        val connectable = ((buttonId - 1) / 5) % 4
        val mountable = (buttonId - 1) / 5 / 4
        return ButtonPosition(mountable, connectable, bus)
    }

    override fun actionPerformed(button: GuiButton) {
        if (button.id == 0) {
            ClientPacketSender.sendRackRelayState(rack, !rack.isRelayEnabled)
        } else {
            val (mountable, connectable, bus) = decodeButtonId(button.id)
            if (rack.nodeMapping[mountable][connectable] != null && rack.nodeMapping[mountable][connectable] == busToSide[bus]) {
                ClientPacketSender.sendRackMountableMapping(rack, mountable, connectable, null)
            } else {
                ClientPacketSender.sendRackMountableMapping(rack, mountable, connectable, busToSide[bus])
            }
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, dt: Float) {
        for (bus in 0 until 5) {
            for (mountable in 0 until rack.sizeInventory) {
                val presence = inventoryContainer.nodePresence[mountable]
                for (connectable in 0 until 4) {
                    wireButtons[mountable][connectable][bus]?.visible = presence[connectable]
                }
            }
        }
        relayButton?.displayString = if (rack.isRelayEnabled) Localization.Rack.RelayEnabled() else Localization.Rack.RelayDisabled()
        super.drawScreen(mouseX, mouseY, dt)
    }

    override fun initGui() {
        super.initGui()

        relayButton = ImageButton(0, guiLeft + 101, guiTop + 96, 65, 18, Textures.GUI.ButtonRelay, Localization.Rack.RelayDisabled(), textIndent = 18)
        add(buttonList, relayButton!!)

        val (mw, mh) = hoverMasterSize
        val (sw, sh) = hoverSlaveSize
        val mbh = busMasterBlankUVs[3]
        val sbh = busSlaveBlankUVs[3]

        for (bus in 0 until 5) {
            for (mountable in 0 until rack.sizeInventory) {
                val offset = mountable * (mbh + sbh * 3 + busGap)
                val (bx, by) = busStart[bus]

                run {
                    val button = ImageButton(encodeButtonId(mountable, 0, bus), guiLeft + bx, guiTop + by + offset + 1, mw, mh)
                    add(buttonList, button)
                    wireButtons[mountable][0][bus] = button
                }

                for (connectable in 0 until 3) {
                    val button = ImageButton(encodeButtonId(mountable, connectable + 1, bus), guiLeft + bx, guiTop + by + offset + 1 + mbh + sbh * connectable, sw, sh)
                    add(buttonList, button)
                    wireButtons[mountable][connectable + 1][bus] = button
                }
            }
        }
    }

    override fun drawSecondaryForegroundLayer(mouseX: Int, mouseY: Int) {
        super.drawSecondaryForegroundLayer(mouseX, mouseY)
        RenderState.pushAttrib() // Prevents NEI render glitch.

        fontRenderer.drawString(
            Localization.localizeImmediately(rack.name),
            8, 6, 0x404040
        )

        GlStateManager.color(1f, 1f, 1f)
        mc.renderEngine.bindTexture(Textures.GUI.Rack)

        if (rack.isRelayEnabled) {
            val (left, top, w, h) = relayModeUVs
            for ((x, y) in wireRelay) {
                drawRect(x, y, w, h, left, top)
            }
        }

        val (mcx, mcy, mcw, mch) = connectorMasterUVs
        val (mbx, mby, mbw, mbh) = busMasterBlankUVs
        val (mpx, mpy, mpw, mph) = busMasterPresentUVs
        val (scx, scy, scw, sch) = connectorSlaveUVs
        val (sbx, sby, sbw, sbh) = busSlaveBlankUVs
        val (spx, spy, spw, sph) = busSlavePresentUVs

        for (mountable in 0 until rack.sizeInventory) {
            val presence = inventoryContainer.nodePresence[mountable]

            // Draw connectable indicators next to item slots.
            val (cx, cy) = connectorStart[mountable]
            if (presence[0]) {
                drawRect(cx, cy, mcw, mch, mcx, mcy)
                rack.nodeMapping[mountable][0]?.let { side ->
                    val bus = sideToBus[side]!!
                    val (mwx, mwy, mww, mwh) = wireMasterUVs[bus]
                    for (i in 0..bus) {
                        val xOffset = mcw + i * (mpw + mww)
                        drawRect(cx + xOffset, cy, mww, mwh, mwx, mwy)
                    }
                }
                for (connectable in 1 until 4) {
                    rack.nodeMapping[mountable][connectable]?.let { side ->
                        val bus = sideToBus[side]!!
                        val (swx, swy, sww, swh) = wireSlaveUVs[bus]
                        val yOffset = (mch + connectorGap) + (sch + connectorGap) * (connectable - 1)
                        for (i in 0..bus) {
                            val xOffset = scw + i * (spw + sww)
                            drawRect(cx + xOffset, cy + yOffset, sww, swh, swx, swy)
                        }
                    }
                }
            }
            for (connectable in 1 until 4) {
                if (presence[connectable]) {
                    val yOffset = (mch + connectorGap) + (sch + connectorGap) * (connectable - 1)
                    drawRect(cx, cy + yOffset, scw, sch, scx, scy)
                }
            }

            // Draw connection points on buses.
            val yOffset = mountable * (mbh + sbh * 3 + busGap)
            for (bus in 0 until 5) {
                val (bx, by) = busStart[bus]
                if (presence[0]) {
                    drawRect(bx - 1, by + yOffset, mpw, mph, mpx, mpy)
                } else {
                    drawRect(bx, by + yOffset, mbw, mbh, mbx, mby)
                }
                for (connectable in 0 until 3) {
                    if (presence[connectable + 1]) {
                        drawRect(bx - 1, by + yOffset + mph + sph * connectable, spw, sph, spx, spy)
                    } else {
                        drawRect(bx, by + yOffset + mbh + sbh * connectable, sbw, sbh, sbx, sby)
                    }
                }
            }
        }

        for (bus in 0 until 5) {
            val x = 122
            val y = 20 + bus * 11

            fontRenderer.drawString(
                Localization.localizeImmediately(sideName(busToSide[bus])),
                x, y, 0x404040
            )
        }

        if (mouseX >= guiLeft + 122 && mouseY >= guiTop + 20 && mouseX < guiLeft + 158 && mouseY < guiTop + 20 + 5 * 11) {
            val tooltip = Localization.Rack.OrientationTooltip().lines().toMutableList()
            copiedDrawHoveringText(tooltip, mouseX - guiLeft, mouseY - guiTop, fontRenderer)
        }

        if (relayButton?.isMouseOver == true) {
            val tooltip = Localization.Rack.RelayModeTooltip().lines().toMutableList()
            copiedDrawHoveringText(tooltip, mouseX - guiLeft, mouseY - guiTop, fontRenderer)
        }

        RenderState.popAttrib()
    }

    override fun drawSecondaryBackgroundLayer() {
        GlStateManager.color(1f, 1f, 1f) // Required under Linux.
        mc.renderEngine.bindTexture(Textures.GUI.Rack)
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize)
    }

    private fun drawRect(x: Int, y: Int, w: Int, h: Int, u: Int, v: Int) {
        val u0 = u / 256f
        val v0 = v / 256f
        val u1 = u0 + w / 256f
        val v1 = v0 + h / 256f
        val t = Tessellator.getInstance()
        val r = t.buffer
        r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)
        r.pos(x.toDouble(), y.toDouble(), windowZ.toDouble()).tex(u0.toDouble(), v0.toDouble()).endVertex()
        r.pos(x.toDouble(), (y + h).toDouble(), windowZ.toDouble()).tex(u0.toDouble(), v1.toDouble()).endVertex()
        r.pos((x + w).toDouble(), (y + h).toDouble(), windowZ.toDouble()).tex(u1.toDouble(), v1.toDouble()).endVertex()
        r.pos((x + w).toDouble(), y.toDouble(), windowZ.toDouble()).tex(u1.toDouble(), v0.toDouble()).endVertex()
        t.draw()
    }
}
