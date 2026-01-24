package li.cil.oc.common.recipe

import li.cil.oc.util.Color
import li.cil.oc.util.ItemColorizer
import net.minecraft.block.Block
import net.minecraft.inventory.InventoryCrafting
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.registries.IForgeRegistryEntry

/**
 * @author asie, Vexatos
 */
class ColorizeRecipe : IForgeRegistryEntry.Impl<net.minecraft.item.crafting.IRecipe>, ContainerItemAwareRecipe {
    val targetItem: Item
    val sourceItems: Array<Item>

    constructor(target: Item, source: Array<Item>? = null) {
        this.targetItem = target
        this.sourceItems = source ?: arrayOf(target)
    }

    constructor(target: Block, source: Array<Item>) : this(Item.getItemFromBlock(target), source)

    constructor(target: Block) : this(target, null)

    override fun matches(crafting: InventoryCrafting, world: World): Boolean {
        val stacks = (0 until crafting.sizeInventory).mapNotNull { i ->
            val stack = crafting.getStackInSlot(i)
            if (!stack.isEmpty) stack else null
        }
        val targets = stacks.filter { stack -> sourceItems.contains(stack.item) || stack.item == targetItem }
        val other = stacks.filterNot { targets.contains(it) }
        return targets.size == 1 && other.isNotEmpty() && other.all { Color.isDye(it) }
    }

    override fun getCraftingResult(crafting: InventoryCrafting): ItemStack {
        var targetStack: ItemStack = ItemStack.EMPTY
        val color = intArrayOf(0, 0, 0)
        var colorCount = 0
        var maximum = 0

        val stacks = (0 until crafting.sizeInventory).mapNotNull { i ->
            val stack = crafting.getStackInSlot(i)
            if (!stack.isEmpty) stack else null
        }

        for (stack in stacks) {
            if (sourceItems.contains(stack.item) || stack.item == targetItem) {
                targetStack = stack.copy()
                targetStack.count = 1
            } else {
                val dye = Color.findDye(stack)
                if (dye.isEmpty()) {
                    return ItemStack.EMPTY
                }

                val itemColor = Color.byOreName(dye.get()).colorComponentValues
                val red = (itemColor[0] * 255.0F).toInt()
                val green = (itemColor[1] * 255.0F).toInt()
                val blue = (itemColor[2] * 255.0F).toInt()
                maximum += maxOf(red, maxOf(green, blue))
                color[0] += red
                color[1] += green
                color[2] += blue
                colorCount += 1
            }
        }

        if (targetStack.isEmpty) return ItemStack.EMPTY

        if (targetItem == targetStack.item) {
            if (ItemColorizer.hasColor(targetStack)) {
                val itemColor = ItemColorizer.getColor(targetStack)
                val red = (itemColor shr 16 and 255).toFloat() / 255.0F
                val green = (itemColor shr 8 and 255).toFloat() / 255.0F
                val blue = (itemColor and 255).toFloat() / 255.0F
                maximum = (maximum.toFloat() + maxOf(red, maxOf(green, blue)) * 255.0F).toInt()
                color[0] = (color[0].toFloat() + red * 255.0F).toInt()
                color[1] = (color[1].toFloat() + green * 255.0F).toInt()
                color[2] = (color[2].toFloat() + blue * 255.0F).toInt()
                colorCount += 1
            }
        } else if (sourceItems.contains(targetStack.item)) {
            targetStack = ItemStack(targetItem, targetStack.count, targetStack.itemDamage)
        }

        var red = color[0] / colorCount
        var green = color[1] / colorCount
        var blue = color[2] / colorCount
        val max = maximum.toFloat() / colorCount.toFloat()
        val div = maxOf(red, maxOf(green, blue)).toFloat()
        red = (red.toFloat() * max / div).toInt()
        green = (green.toFloat() * max / div).toInt()
        blue = (blue.toFloat() * max / div).toInt()
        ItemColorizer.setColor(targetStack, (red shl 16) or (green shl 8) or blue)
        return targetStack
    }

    override fun getMinimumRecipeSize(): Int = 2

    override fun getRecipeOutput(): ItemStack = ItemStack.EMPTY
}
