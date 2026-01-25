package li.cil.oc.integration.minecraft

import li.cil.oc.Settings
import li.cil.oc.api.driver.Converter
import li.cil.oc.integration.Mods
import li.cil.oc.util.ItemUtils
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraft.nbt.NBTTagString
import net.minecraftforge.common.util.Constants.NBT
import net.minecraftforge.oredict.OreDictionary

object ConverterItemStack : Converter {
    fun getTagValue(tag: NBTTagCompound, key: String): Any? = when (tag.getTagId(key).toInt()) {
        NBT.TAG_INT -> tag.getInteger(key)
        NBT.TAG_STRING -> tag.getString(key)
        NBT.TAG_BYTE -> tag.getByte(key)
        NBT.TAG_COMPOUND -> tag.getCompoundTag(key)
        NBT.TAG_LIST -> tag.getTagList(key, NBT.TAG_STRING)
        else -> null
    }

    fun <R> withTag(tag: NBTTagCompound, key: String, tagId: Int, f: (Any?) -> R): R? {
        return if (tag.hasKey(key, tagId)) {
            val value = getTagValue(tag, key)
            if (value != null) f(value) else null
        } else null
    }

    fun <R> withCompound(tag: NBTTagCompound, key: String, f: (NBTTagCompound) -> R): R? {
        return withTag(tag, key, NBT.TAG_COMPOUND) { value ->
            when (value) {
                is NBTTagCompound -> f(value)
                else -> null
            }
        }
    }

    fun withList(tag: NBTTagCompound, key: String, f: (NBTTagList) -> Any?): Any? {
        return withTag(tag, key, NBT.TAG_STRING) { value ->
            when (value) {
                is NBTTagList -> f(value)
                else -> null
            }
        }
    }

    override fun convert(value: Any?, output: MutableMap<Any?, Any?>) {
        when (value) {
            is ItemStack -> {
                if (Settings.get.insertIdsInConverters) {
                    output["id"] = Item.getIdFromItem(value.item)
                    output["oreNames"] = OreDictionary.getOreIDs(value).map { OreDictionary.getOreName(it) }
                }
                output["damage"] = value.itemDamage
                output["maxDamage"] = value.maxDamage
                output["size"] = value.count
                output["maxSize"] = value.maxStackSize
                output["hasTag"] = value.hasTagCompound()
                output["name"] = Item.REGISTRY.getNameForObject(value.item)
                output["label"] = value.displayName

                // custom mod tags
                if (value.hasTagCompound()) {
                    val tags = value.tagCompound!!

                    // Lore tags
                    withCompound(tags, "display") { display ->
                        withList(display, "Lore") { lore ->
                            output["lore"] = (0 until lore.tagCount()).joinToString("\n") { i ->
                                (lore.get(i) as? NBTTagString)?.string ?: ""
                            }
                            Unit
                        }
                    }

                    // IC2 reactor items custom damage
                    withTag(tags, "advDmg", NBT.TAG_INT) { dmg ->
                        output["customDamage"] = dmg
                    }

                    // draconic upgrades
                    if (Mods.DraconicEvolution.isModAvailable) {
                        withCompound(tags, "DEUpgrades") { de ->
                            output["DEUpgrades"] = de
                        }
                        for (n in 0..14) {
                            val profileName = "Profile_$n"
                            val profile = getTagValue(tags, profileName)
                            if (profile is NBTTagCompound) {
                                output[profileName] = profile
                            }
                        }
                    }

                    withTag(tags, "Energy", NBT.TAG_INT) { value ->
                        output["Energy"] = value
                    }

                    if (Settings.get.allowItemStackNBTTags) {
                        output["tag"] = ItemUtils.saveTag(value.tagCompound!!)
                    }
                }

                val enchantments = mutableListOf<Map<String, Any>>()
                EnchantmentHelper.getEnchantments(value).forEach { (enchantment, level) ->
                    val map = mutableMapOf<String, Any>(
                        "name" to enchantment.name,
                        "label" to enchantment.getTranslatedName(level),
                        "level" to level
                    )
                    enchantments.add(map)
                }
                if (enchantments.isNotEmpty()) {
                    output["enchantments"] = enchantments
                }
            }
        }
    }
}
