package li.cil.oc.common.recipe

import li.cil.oc.Settings
import li.cil.oc.common.Loot
import li.cil.oc.integration.util.Wrench
import net.minecraft.inventory.InventoryCrafting
import net.minecraft.item.ItemStack
import net.minecraft.item.crafting.IRecipe
import net.minecraft.util.NonNullList
import net.minecraft.world.World
import net.minecraftforge.registries.IForgeRegistryEntry

class LootDiskCyclingRecipe : IForgeRegistryEntry.Impl<IRecipe>(), IRecipe {
    override fun matches(crafting: InventoryCrafting, world: World): Boolean {
        val stacks = collectStacks(crafting)
        return stacks.size == 2 && stacks.any { Loot.isLootDisk(it) } && stacks.any { Wrench.isWrench(it) }
    }

    override fun getCraftingResult(crafting: InventoryCrafting): ItemStack {
        val lootDiskStacks = Loot.disksForCycling()
        val lootDisk = collectStacks(crafting).find { Loot.isLootDisk(it) }
        return if (lootDisk != null && lootDiskStacks.isNotEmpty()) {
            val lootFactoryName = getLootFactoryName(lootDisk)
            val oldIndex = lootDiskStacks.indexOfFirst { s -> getLootFactoryName(s) == lootFactoryName }
            val newIndex = (oldIndex + 1) % lootDiskStacks.size
            lootDiskStacks[newIndex].copy()
        } else {
            ItemStack.EMPTY
        }
    }

    fun getLootFactoryName(stack: ItemStack): String = stack.tagCompound!!.getString(Settings.namespace + "lootFactory")

    fun collectStacks(crafting: InventoryCrafting): List<ItemStack> =
        (0 until crafting.sizeInventory).mapNotNull { i ->
            val stack = crafting.getStackInSlot(i)
            if (!stack.isEmpty) stack else null
        }

    override fun canFit(width: Int, height: Int): Boolean = width * height >= 2

    override fun getRecipeOutput(): ItemStack = ItemStack.EMPTY

    override fun getRemainingItems(crafting: InventoryCrafting): NonNullList<ItemStack> {
        val result = NonNullList.withSize<ItemStack>(crafting.sizeInventory, ItemStack.EMPTY)
        for (slot in 0 until crafting.sizeInventory) {
            val stack = crafting.getStackInSlot(slot)
            if (Wrench.isWrench(stack)) {
                result[slot] = stack.copy()
                stack.count = 0
            }
        }
        return result
    }
}
