package li.cil.oc.integration.jei

import li.cil.oc.common.EventHandler
import mezz.jei.api.IJeiRuntime
import mezz.jei.api.ingredients.IIngredientRegistry
import net.minecraft.item.ItemStack

object ModJEI {
    var runtime: IJeiRuntime? = null

    var ingredientRegistry: IIngredientRegistry? = null

    private val disksForRuntime: MutableList<ItemStack> = mutableListOf()

    private var scheduled: Boolean = false

    fun addDiskAtRuntime(stack: ItemStack) {
        ingredientRegistry?.let { registry ->
            if (!registry.getIngredients(ItemStack::class.java).any { ItemStack.areItemStacksEqual(it, stack) }) {
                disksForRuntime.add(stack)
                if (!scheduled) {
                    EventHandler.scheduleClient {
                        ingredientRegistry?.addIngredientsAtRuntime(ItemStack::class.java, disksForRuntime)
                        disksForRuntime.clear()
                        scheduled = false
                    }
                    scheduled = true
                }
            }
        }
    }
}
