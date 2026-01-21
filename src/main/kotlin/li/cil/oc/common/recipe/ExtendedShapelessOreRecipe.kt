package li.cil.oc.common.recipe

import net.minecraft.inventory.InventoryCrafting
import net.minecraft.item.ItemStack
import net.minecraftforge.oredict.ShapelessOreRecipe

open class ExtendedShapelessOreRecipe(result: ItemStack, vararg ingredients: Any) : ShapelessOreRecipe(null, result, *ingredients) {
    override fun getCraftingResult(inventory: InventoryCrafting): ItemStack =
        ExtendedRecipe.addNBTToResult(this, super.getCraftingResult(inventory), inventory)
}
