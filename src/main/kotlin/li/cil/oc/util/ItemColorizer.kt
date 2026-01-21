package li.cil.oc.util

import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

/**
 * @author asie, Vexatos
 */
object ItemColorizer {
    /**
     * Return whether the specified armor ItemStack has a color.
     */
    @JvmStatic
    fun hasColor(stack: ItemStack): Boolean {
        return stack.hasTagCompound() &&
            stack.tagCompound!!.hasKey("display") &&
            stack.tagCompound!!.getCompoundTag("display").hasKey("color")
    }

    /**
     * Return the color for the specified armor ItemStack.
     */
    @JvmStatic
    fun getColor(stack: ItemStack): Int {
        val tag = stack.tagCompound ?: return -1
        val displayTag = tag.getCompoundTag("display")
        return if (displayTag.hasKey("color")) displayTag.getInteger("color") else -1
    }

    @JvmStatic
    fun removeColor(stack: ItemStack) {
        val tag = stack.tagCompound ?: return
        val displayTag = tag.getCompoundTag("display")
        if (displayTag.hasKey("color")) {
            displayTag.removeTag("color")
        }
    }

    @JvmStatic
    fun setColor(stack: ItemStack, color: Int) {
        var tag = stack.tagCompound
        if (tag == null) {
            tag = NBTTagCompound()
            stack.tagCompound = tag
        }
        val displayTag = tag.getCompoundTag("display")
        if (!tag.hasKey("display")) {
            tag.setTag("display", displayTag)
        }
        displayTag.setInteger("color", color)
    }
}
