package li.cil.oc.util

import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.Items
import li.cil.oc.common.Tier
import net.minecraft.block.Block
import net.minecraft.item.Item
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemBucket
import net.minecraft.item.ItemStack
import net.minecraft.item.crafting.CraftingManager
import net.minecraft.item.crafting.IRecipe
import net.minecraft.item.crafting.Ingredient
import net.minecraft.item.crafting.ShapedRecipes
import net.minecraft.item.crafting.ShapelessRecipes
import net.minecraft.nbt.CompressedStreamTools
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.oredict.ShapedOreRecipe
import net.minecraftforge.oredict.ShapelessOreRecipe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random

object ItemUtils {
    @JvmStatic
    fun getDisplayName(nbt: NBTTagCompound): String? {
        if (nbt.hasKey("display")) {
            val displayNbt = nbt.getCompoundTag("display")
            if (displayNbt.hasKey("Name")) {
                return displayNbt.getString("Name")
            }
        }
        return null
    }

    @JvmStatic
    fun setDisplayName(nbt: NBTTagCompound, name: String) {
        if (!nbt.hasKey("display")) {
            nbt.setTag("display", NBTTagCompound())
        }
        nbt.getCompoundTag("display").setString("Name", name)
    }

    @JvmStatic
    fun caseTier(stack: ItemStack): Int {
        val descriptor = Items.get(stack)
        return when (descriptor) {
            Constants.BlockInfo.CaseTier1 -> Tier.One
            Constants.BlockInfo.CaseTier2 -> Tier.Two
            Constants.BlockInfo.CaseTier3 -> Tier.Three
            Constants.BlockInfo.CaseCreative -> Tier.Four
            Constants.ItemInfo.MicrocontrollerCaseTier1 -> Tier.One
            Constants.ItemInfo.MicrocontrollerCaseTier2 -> Tier.Two
            Constants.ItemInfo.MicrocontrollerCaseCreative -> Tier.Four
            Constants.ItemInfo.DroneCaseTier1 -> Tier.One
            Constants.ItemInfo.DroneCaseTier2 -> Tier.Two
            Constants.ItemInfo.DroneCaseCreative -> Tier.Four
            Constants.ItemInfo.ServerTier1 -> Tier.One
            Constants.ItemInfo.ServerTier2 -> Tier.Two
            Constants.ItemInfo.ServerTier3 -> Tier.Three
            Constants.ItemInfo.ServerCreative -> Tier.Four
            Constants.ItemInfo.TabletCaseTier1 -> Tier.One
            Constants.ItemInfo.TabletCaseTier2 -> Tier.Two
            Constants.ItemInfo.TabletCaseCreative -> Tier.Four
            else -> Tier.None
        }
    }

    @JvmStatic
    fun caseNameWithTierSuffix(name: String, tier: Int): String =
        name + if (tier == Tier.Four) "creative" else (tier + 1).toString()

    @JvmStatic
    fun loadTag(data: ByteArray): NBTTagCompound {
        val bais = ByteArrayInputStream(data)
        return CompressedStreamTools.readCompressed(bais)
    }

    @JvmStatic
    fun saveStack(stack: ItemStack): ByteArray {
        val tag = NBTTagCompound()
        stack.writeToNBT(tag)
        return saveTag(tag)
    }

    @JvmStatic
    fun saveTag(tag: NBTTagCompound): ByteArray {
        val baos = ByteArrayOutputStream()
        CompressedStreamTools.writeCompressed(tag, baos)
        return baos.toByteArray()
    }

    @JvmStatic
    fun getIngredients(stack: ItemStack): Array<ItemStack> {
        try {
            fun getFilteredInputs(inputs: Iterable<ItemStack>, outputSize: Int): Pair<Array<ItemStack>, Int> {
                val filtered = inputs.filter { input ->
                    !input.isEmpty &&
                        input.count / outputSize > 0 &&
                        // Strip out buckets, because those are returned when crafting, and
                        // we have no way of returning the fluid only (and I can't be arsed
                        // to make it output fluids into fluiducts or such, sorry).
                        input.item !is ItemBucket
                }.toTypedArray()
                return Pair(filtered, outputSize)
            }

            fun getOutputSize(recipe: IRecipe): Int = recipe.recipeOutput.count

            fun isInputBlacklisted(itemStack: ItemStack): Boolean {
                val item = itemStack.item
                return when (item) {
                    is ItemBlock -> Settings.get.disassemblerInputBlacklist.contains(Block.REGISTRY.getNameForObject(item.block) as String)
                    is Item -> Settings.get.disassemblerInputBlacklist.contains(Item.REGISTRY.getNameForObject(item) as String)
                    else -> false
                }
            }

            val result = CraftingManager.REGISTRY
                .filter { recipe -> !recipe.recipeOutput.isEmpty && recipe.recipeOutput.isItemEqual(stack) }
                .mapNotNull { recipe ->
                    when (recipe) {
                        is ShapedRecipes -> getFilteredInputs(resolveOreDictEntries(recipe.recipeItems), getOutputSize(recipe))
                        is ShapelessRecipes -> getFilteredInputs(resolveOreDictEntries(recipe.recipeItems), getOutputSize(recipe))
                        is ShapedOreRecipe -> getFilteredInputs(resolveOreDictEntries(recipe.ingredients), getOutputSize(recipe))
                        is ShapelessOreRecipe -> getFilteredInputs(resolveOreDictEntries(recipe.ingredients), getOutputSize(recipe))
                        else -> null
                    }
                }
                .firstOrNull { (inputs, _) -> !inputs.any { isInputBlacklisted(it) } }
                ?: return emptyArray()

            val (ingredients, count) = result

            // Avoid positive feedback loops.
            if (ingredients.any { ingredient -> ingredient.isItemEqual(stack) }) {
                return emptyArray()
            }

            // Merge equal items for size division by output size.
            val merged = mutableListOf<ItemStack>()
            for (ingredient in ingredients) {
                val existing = merged.find { it.isItemEqual(ingredient) }
                if (existing != null) {
                    existing.grow(ingredient.count)
                } else {
                    merged.add(ingredient.copy())
                }
            }
            merged.forEach { s -> s.count = s.count / count }

            // Split items up again to 'disassemble them individually'.
            val distinct = mutableListOf<ItemStack>()
            for (ingredient in merged) {
                val size = maxOf(ingredient.count, 1)
                ingredient.count = 1
                for (i in 0 until size) {
                    distinct.add(ingredient.copy())
                }
            }
            return distinct.toTypedArray()
        } catch (t: Throwable) {
            OpenComputers.log.warn("Whoops, something went wrong when trying to figure out an item's parts.", t)
            return emptyArray()
        }
    }

    private val rng by lazy { Random() }

    private fun resolveOreDictEntries(entries: Iterable<Ingredient>): List<ItemStack> {
        return entries.mapNotNull { ing ->
            val stacks = ing.matchingStacks
            if (stacks.isNotEmpty()) {
                stacks[rng.nextInt(stacks.size)]
            } else {
                null
            }
        }
    }
}
