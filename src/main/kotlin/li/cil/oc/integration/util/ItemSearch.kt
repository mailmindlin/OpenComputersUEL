package li.cil.oc.integration.util

import li.cil.oc.util.StackOption
import li.cil.oc.util.asStackOption
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.item.ItemStack

object ItemSearch {
    private val focusedInput = mutableSetOf<() -> Boolean>()
    internal val stackFocusing = mutableSetOf<(GuiContainer, Int, Int) -> ItemStack?>()

    fun isInputFocused(): Boolean {
        for (f in focusedInput) {
            if (f()) return true
        }
        return false
    }

    fun hoveredStack(container: GuiContainer, mouseX: Int, mouseY: Int): StackOption {
        for (f in stackFocusing) {
            val result = f(container, mouseX, mouseY)
            if (result != null) {
                return result.asStackOption()
            }
        }
        return StackOption.empty()
    }
}
