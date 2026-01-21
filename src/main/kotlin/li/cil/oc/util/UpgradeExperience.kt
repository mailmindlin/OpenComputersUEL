package li.cil.oc.util

import li.cil.oc.Settings
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object UpgradeExperience {
    @JvmField
    val XpTag: String = Settings.namespace + "xp"

    @JvmStatic
    fun getExperience(nbt: NBTTagCompound): Double = max(nbt.getDouble(XpTag), 0.0)

    @JvmStatic
    fun getExperience(stack: ItemStack): Double =
        if (!stack.hasTagCompound()) 0.0 else getExperience(stack.tagCompound!!)

    @JvmStatic
    fun setExperience(nbt: NBTTagCompound, experience: Double) {
        nbt.setDouble(XpTag, experience)
    }

    @JvmStatic
    fun xpForLevel(level: Int): Double =
        if (level == 0) 0.0
        else Settings.get.baseXpToLevel + (level * Settings.get.constantXpGrowth).toDouble().pow(Settings.get.exponentialXpGrowth)

    @JvmStatic
    fun calculateExperienceLevel(level: Int, experience: Double): Double {
        val xpNeeded = xpForLevel(level + 1) - xpForLevel(level)
        val xpProgress = max(0.0, experience - xpForLevel(level))
        return level + xpProgress / xpNeeded
    }

    @JvmStatic
    fun calculateLevelFromExperience(experience: Double): Int =
        min(
            ((experience - Settings.get.baseXpToLevel).pow(1.0 / Settings.get.exponentialXpGrowth) / Settings.get.constantXpGrowth).toInt(),
            30
        )
}
