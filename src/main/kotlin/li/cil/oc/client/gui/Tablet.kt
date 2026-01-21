package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.common.container.Tablet as ContainerTablet
import li.cil.oc.common.item.TabletWrapper
import net.minecraft.entity.player.InventoryPlayer

class Tablet(
    playerInventory: InventoryPlayer,
    val tablet: TabletWrapper
) : DynamicGuiContainer<ContainerTablet>(ContainerTablet(playerInventory, tablet)), li.cil.oc.client.gui.traits.LockedHotbar {

    override val lockedStack get() = tablet.stack

    override fun drawSecondaryForegroundLayer(mouseX: Int, mouseY: Int) {
        super.drawSecondaryForegroundLayer(mouseX, mouseY)
        fontRenderer.drawString(
            Localization.localizeImmediately(tablet.name),
            8, 6, 0x404040
        )
    }
}
