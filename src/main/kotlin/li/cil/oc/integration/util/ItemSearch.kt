package li.cil.oc.integration.util

import li.cil.oc.util.StackOption
import net.minecraft.client.gui.inventory.GuiContainer

object ItemSearch {

    val focusedInput = mutableSetOf<() -> Boolean>()
    val stackFocusing = mutableSetOf<(GuiContainer, Int, Int) -> StackOption>()

    fun isInputFocused(): Boolean {
        for (f in focusedInput) {
            if (f()) return true
        }
        return false
    }

    fun hoveredStack(container: GuiContainer, mouseX: Int, mouseY: Int): StackOption {
        for (f in stackFocusing) {
            val result = f(container, mouseX, mouseY)
            if (result.isDefined) {
                return result
            }
        }
        return StackOption.EmptyStack
    }
}
