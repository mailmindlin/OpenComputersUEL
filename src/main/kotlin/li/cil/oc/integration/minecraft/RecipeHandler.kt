package li.cil.oc.integration.minecraft

import com.typesafe.config.Config
import li.cil.oc.common.recipe.ExtendedShapedOreRecipe
import li.cil.oc.common.recipe.ExtendedShapelessOreRecipe
import li.cil.oc.common.recipe.Recipes
import li.cil.oc.common.recipe.Recipes.RecipeException
import net.minecraft.item.ItemStack
import net.minecraft.item.crafting.FurnaceRecipes
import net.minecraftforge.oredict.OreDictionary

object RecipeHandler {
    fun init() {
        Recipes.registerRecipeHandler("shaped", ::addShapedRecipe)
        Recipes.registerRecipeHandler("shapeless", ::addShapelessRecipe)
        Recipes.registerRecipeHandler("furnace", ::addFurnaceRecipe)
    }

    fun addShapedRecipe(output: ItemStack, recipe: Config) {
        val rows = recipe.getList("input").unwrapped().map { row ->
            when (row) {
                is List<*> -> row.map { Recipes.parseIngredient(it) }
                else -> throw RecipeException("Invalid row entry for shaped recipe (not a list: $row).")
            }
        }
        output.count = Recipes.tryGetCount(recipe)

        var number = -1
        val shape = mutableListOf<String>()
        val input = mutableListOf<Any>()

        for (row in rows) {
            val (pattern, ingredients) = row.fold(StringBuilder() to emptyList<Any>()) { acc, ingredient ->
                val (pattern, ingredients) = acc
                when (ingredient) {
                    is ItemStack, is String -> {
                        number += 1
                        val char = ('a' + number)
                        pattern.append(char) to (ingredients + char + ingredient)
                    }
                    else -> pattern.append(' ') to ingredients
                }
            }
            shape.add(pattern.toString())
            input.addAll(ingredients)
        }

        if (input.isNotEmpty() && output.count > 0) {
            Recipes.addRecipe(ExtendedShapedOreRecipe(output, *(shape + input).toTypedArray()))
        }
    }

    fun addShapelessRecipe(output: ItemStack, recipe: Config) {
        val input = when (val value = recipe.getValue("input").unwrapped()) {
            is List<*> -> value.map { Recipes.parseIngredient(it) }
            else -> listOf(Recipes.parseIngredient(value))
        }
        output.count = Recipes.tryGetCount(recipe)

        if (input.isNotEmpty() && output.count > 0) {
            Recipes.addRecipe(ExtendedShapelessOreRecipe(output, *input.toTypedArray()))
        }
    }

    fun addFurnaceRecipe(output: ItemStack, recipe: Config) {
        val input = Recipes.parseIngredient(recipe.getValue("input").unwrapped())
        output.count = Recipes.tryGetCount(recipe)

        when (input) {
            is ItemStack -> FurnaceRecipes.instance().addSmeltingRecipe(input, output, 0f)
            is String -> {
                for (stack in OreDictionary.getOres(input)) {
                    FurnaceRecipes.instance().addSmeltingRecipe(stack, output, 0f)
                }
            }
        }
    }
}
