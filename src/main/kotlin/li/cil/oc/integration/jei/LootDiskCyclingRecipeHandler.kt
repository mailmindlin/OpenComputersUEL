package li.cil.oc.integration.jei

import li.cil.oc.Constants
import li.cil.oc.api.Items
import li.cil.oc.common.Loot
import li.cil.oc.common.recipe.LootDiskCyclingRecipe
import mezz.jei.api.ingredients.IIngredients
import mezz.jei.api.ingredients.VanillaTypes
import mezz.jei.api.recipe.BlankRecipeWrapper
import mezz.jei.api.recipe.IIngredientType
import mezz.jei.api.recipe.IRecipeWrapper
import mezz.jei.api.recipe.IRecipeWrapperFactory
import net.minecraft.item.ItemStack

object LootDiskCyclingRecipeHandler : IRecipeWrapperFactory<LootDiskCyclingRecipe> {
    override fun getRecipeWrapper(recipe: LootDiskCyclingRecipe): IRecipeWrapper {
        return LootDiskCyclingRecipeWrapper(recipe)
    }

    class LootDiskCyclingRecipeWrapper(val recipe: LootDiskCyclingRecipe) : IRecipeWrapper {
        private fun getInputs(): List<List<ItemStack>> {
            return listOf(
                Loot.disksForCycling().toList(),
                listOf(Items.get(Constants.ItemName.Wrench).createItemStack(1))
            )
        }

        private fun getOutputs(): List<ItemStack> = Loot.disksForCycling().toList()

        override fun getIngredients(ingredients: IIngredients) {
            ingredients.setInputLists(VanillaTypes.ITEM, getInputs())
            ingredients.setOutputs(VanillaTypes.ITEM, getOutputs())
        }
    }
}
