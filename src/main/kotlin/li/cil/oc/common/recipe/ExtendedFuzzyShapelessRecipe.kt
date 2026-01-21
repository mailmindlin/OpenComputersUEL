package li.cil.oc.common.recipe

import net.minecraft.inventory.InventoryCrafting
import net.minecraft.item.ItemStack
import net.minecraft.world.World

class ExtendedFuzzyShapelessRecipe(result: ItemStack, vararg ingredients: Any) : ExtendedShapelessOreRecipe(result, *ingredients) {
    override fun matches(inv: InventoryCrafting, world: World): Boolean {
        val requiredItems = ingredients.map { any -> any as ItemStack }.toMutableList()
        for (i in 0 until inv.sizeInventory) {
            val itemStack = inv.getStackInSlot(i)
            if (!itemStack.isEmpty) {
                val index = requiredItems.indexOfFirst { req ->
                    if (req.item != itemStack.item) return false
                    req.itemDamage == itemStack.itemDamage
                }
                if (index >= 0) {
                    requiredItems.removeAt(index)
                }
            }
        }
        return requiredItems.isEmpty()
    }
}
