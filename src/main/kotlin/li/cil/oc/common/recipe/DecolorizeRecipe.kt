package li.cil.oc.common.recipe

import li.cil.oc.util.ItemColorizer
import net.minecraft.block.Block
import net.minecraft.init.Items
import net.minecraft.inventory.InventoryCrafting
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.registries.IForgeRegistryEntry

/**
 * @author Vexatos
 */
class DecolorizeRecipe : IForgeRegistryEntry.Impl<net.minecraft.item.crafting.IRecipe>, ContainerItemAwareRecipe {
    val targetItem: Item

    constructor(target: Item) {
        this.targetItem = target
    }

    constructor(target: Block) : this(Item.getItemFromBlock(target))

    override fun matches(crafting: InventoryCrafting, world: World): Boolean {
        val stacks = (0 until crafting.sizeInventory).mapNotNull { i ->
            val stack = crafting.getStackInSlot(i)
            if (!stack.isEmpty) stack else null
        }
        val targets = stacks.filter { stack -> stack.item == targetItem }
        val other = stacks.filterNot { targets.contains(it) }
        return targets.size == 1 && other.size == 1 && other.all { it.item == Items.WATER_BUCKET }
    }

    override fun getCraftingResult(crafting: InventoryCrafting): ItemStack {
        var targetStack: ItemStack = ItemStack.EMPTY

        val stacks = (0 until crafting.sizeInventory).mapNotNull { i ->
            val stack = crafting.getStackInSlot(i)
            if (!stack.isEmpty) stack else null
        }

        for (stack in stacks) {
            if (stack.item == targetItem) {
                targetStack = stack.copy()
                targetStack.count = 1
            } else if (stack.item != Items.WATER_BUCKET) {
                return ItemStack.EMPTY
            }
        }

        if (targetStack.isEmpty) return ItemStack.EMPTY

        ItemColorizer.removeColor(targetStack)
        return targetStack
    }

    override fun getMinimumRecipeSize(): Int = 2

    override fun getRecipeOutput(): ItemStack = ItemStack.EMPTY
}
