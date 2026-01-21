package li.cil.oc.util

import net.minecraft.item.EnumDyeColor
import net.minecraft.item.ItemStack
import net.minecraftforge.oredict.OreDictionary

object Color {
    @JvmField
    val rgbValues: Map<EnumDyeColor, UInt> = mapOf(
        EnumDyeColor.BLACK to 0x444444u, // 0x1E1B1B
        EnumDyeColor.RED to 0xB3312Cu,
        EnumDyeColor.GREEN to 0x339911u, // 0x3B511A
        EnumDyeColor.BROWN to 0x51301Au,
        EnumDyeColor.BLUE to 0x6666FFu, // 0x253192
        EnumDyeColor.PURPLE to 0x7B2FBEu,
        EnumDyeColor.CYAN to 0x66FFFFu, // 0x287697
        EnumDyeColor.SILVER to 0xABABABu,
        EnumDyeColor.GRAY to 0x666666u, // 0x434343
        EnumDyeColor.PINK to 0xD88198u,
        EnumDyeColor.LIME to 0x66FF66u, // 0x41CD34
        EnumDyeColor.YELLOW to 0xFFFF66u, // 0xDECF2A
        EnumDyeColor.LIGHT_BLUE to 0xAAAAFFu, // 0x6689D3
        EnumDyeColor.MAGENTA to 0xC354CDu,
        EnumDyeColor.ORANGE to 0xEB8844u,
        EnumDyeColor.WHITE to 0xF0F0F0u
    )

    @JvmField
    val dyes: Array<String> = arrayOf(
        "dyeBlack",
        "dyeRed",
        "dyeGreen",
        "dyeBrown",
        "dyeBlue",
        "dyePurple",
        "dyeCyan",
        "dyeLightGray",
        "dyeGray",
        "dyePink",
        "dyeLime",
        "dyeYellow",
        "dyeLightBlue",
        "dyeMagenta",
        "dyeOrange",
        "dyeWhite"
    )

    @JvmField
    val byOreName: Map<String, EnumDyeColor> = mapOf(
        "dyeBlack" to EnumDyeColor.BLACK,
        "dyeRed" to EnumDyeColor.RED,
        "dyeGreen" to EnumDyeColor.GREEN,
        "dyeBrown" to EnumDyeColor.BROWN,
        "dyeBlue" to EnumDyeColor.BLUE,
        "dyePurple" to EnumDyeColor.PURPLE,
        "dyeCyan" to EnumDyeColor.CYAN,
        "dyeLightGray" to EnumDyeColor.SILVER,
        "dyeGray" to EnumDyeColor.GRAY,
        "dyePink" to EnumDyeColor.PINK,
        "dyeLime" to EnumDyeColor.LIME,
        "dyeYellow" to EnumDyeColor.YELLOW,
        "dyeLightBlue" to EnumDyeColor.LIGHT_BLUE,
        "dyeMagenta" to EnumDyeColor.MAGENTA,
        "dyeOrange" to EnumDyeColor.ORANGE,
        "dyeWhite" to EnumDyeColor.WHITE
    )

    @JvmField
    val byTier: Array<EnumDyeColor> = arrayOf(
        EnumDyeColor.SILVER,
        EnumDyeColor.YELLOW,
        EnumDyeColor.CYAN,
        EnumDyeColor.MAGENTA
    )

    @JvmStatic
    fun byMeta(meta: EnumDyeColor): EnumDyeColor = byOreName[dyes[meta.dyeDamage]]!!

    @JvmStatic
    fun findDye(stack: ItemStack): String? {
        return byOreName.keys.find { oreName ->
            OreDictionary.getOres(oreName).any { oreStack ->
                OreDictionary.itemMatches(stack, oreStack, false)
            }
        }
    }

    @JvmStatic
    fun isDye(stack: ItemStack): Boolean = findDye(stack) != null

    @JvmStatic
    fun dyeColor(stack: ItemStack): EnumDyeColor {
        val dye = findDye(stack)
        return if (dye != null) byOreName[dye]!! else EnumDyeColor.MAGENTA
    }

    fun rgbValues(silver: EnumDyeColor): UInt = this.rgbValues[silver]!!
}
