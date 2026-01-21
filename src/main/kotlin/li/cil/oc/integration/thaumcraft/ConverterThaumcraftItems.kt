package li.cil.oc.integration.thaumcraft

import li.cil.oc.api.driver.Converter
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.util.Constants.NBT

object ConverterThaumcraftItems : Converter {
    override fun convert(value: Any?, output: MutableMap<Any, Any>) {
        if (value !is ItemStack) return

        val name = Item.REGISTRY.getNameForObject(value.item).toString()

        // Handle essentia/vis contents for Thaumcraft jars, phials, and crystals
        if ((name == "thaumcraft:jar_normal") ||
            (name == "thaumcraft:jar_void") ||
            (name == "thaumcraft:phial") ||
            (name == "thaumcraft:crystal_essence")) {

            if (value.hasTagCompound() &&
                value.tagCompound!!.hasKey("Aspects", NBT.TAG_LIST)) {

                val aspects = mutableListOf<MutableMap<String, Any>>()
                val nbtAspects = value.tagCompound!!.getTagList("Aspects", NBT.TAG_COMPOUND)

                for (i in 0 until nbtAspects.tagCount()) {
                    val nbtAspect = nbtAspects.getCompoundTagAt(i)
                    val key = nbtAspect.getString("key")
                    val amount = nbtAspect.getInteger("amount")
                    val aspect = mutableMapOf<String, Any>(
                        "aspect" to key,
                        "amount" to amount
                    )
                    aspects.add(aspect)
                }
                output["aspects"] = aspects
            }

            if (value.hasTagCompound() &&
                value.tagCompound!!.hasKey("AspectFilter", NBT.TAG_STRING)) {
                output["aspectFilter"] = value.tagCompound!!.getString("AspectFilter")
            }
        }
    }
}
