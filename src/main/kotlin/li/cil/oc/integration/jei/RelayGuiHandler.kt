package li.cil.oc.integration.jei

import li.cil.oc.client.gui.Relay
import mezz.jei.api.gui.IAdvancedGuiHandler
import java.awt.Rectangle

object RelayGuiHandler : IAdvancedGuiHandler<Relay> {

    override fun getGuiContainerClass(): Class<Relay> = Relay::class.java

    override fun getGuiExtraAreas(gui: Relay): List<Rectangle> {
        return listOf(
            Rectangle(
                gui.windowX + gui.tabPosition.x,
                gui.windowY + gui.tabPosition.y,
                gui.tabPosition.width,
                gui.tabPosition.height
            )
        )
    }

    override fun getIngredientUnderMouse(guiContainer: Relay, mouseX: Int, mouseY: Int): Any? = null
}
