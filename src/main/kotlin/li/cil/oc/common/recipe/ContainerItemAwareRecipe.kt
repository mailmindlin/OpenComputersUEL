package li.cil.oc.common.recipe

import net.minecraft.inventory.InventoryCrafting
import net.minecraft.item.ItemStack
import net.minecraft.item.crafting.IRecipe
import net.minecraft.util.NonNullList
import net.minecraftforge.common.ForgeHooks
import net.minecraftforge.registries.IForgeRegistryEntry

interface ContainerItemAwareRecipe : IRecipe {
    override fun getRemainingItems(inv: InventoryCrafting): NonNullList<ItemStack> = ForgeHooks.defaultRecipeGetRemainingItems(inv)

    fun getMinimumRecipeSize(): Int

    override fun canFit(width: Int, height: Int): Boolean = width * height >= getMinimumRecipeSize()
}
