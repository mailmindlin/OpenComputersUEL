package li.cil.oc.common.recipe

import net.minecraft.inventory.InventoryCrafting
import net.minecraft.item.ItemStack
import net.minecraftforge.oredict.ShapedOreRecipe

class ExtendedShapedOreRecipe(result: ItemStack, vararg ingredients: Any) : ShapedOreRecipe(null, result, *ingredients) {
    override fun getCraftingResult(inventory: InventoryCrafting): ItemStack =
        ExtendedRecipe.addNBTToResult(this, super.getCraftingResult(inventory), inventory)
}
