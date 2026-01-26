package li.cil.oc.integration.jei

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Items
import li.cil.oc.common.recipe.LootDiskCyclingRecipe
import li.cil.oc.integration.jei.CallbackDocHandler.CallbackDocRecipe
import li.cil.oc.integration.jei.ManualUsageHandler.ManualUsageRecipe
import li.cil.oc.integration.util.ItemBlacklist
import li.cil.oc.integration.util.ItemSearch
import li.cil.oc.util.StackOption
import li.cil.oc.util.notEmpty
import mezz.jei.api.IJeiRuntime
import mezz.jei.api.IModPlugin
import mezz.jei.api.IModRegistry
import mezz.jei.api.ISubtypeRegistry
import mezz.jei.api.ISubtypeRegistry.ISubtypeInterpreter
import mezz.jei.api.JEIPlugin
import mezz.jei.api.ingredients.IModIngredientRegistration
import mezz.jei.api.recipe.IRecipeCategoryRegistration
import mezz.jei.api.recipe.VanillaRecipeCategoryUid
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

@JEIPlugin
class ModPluginOpenComputers : IModPlugin {
    override fun registerCategories(registry: IRecipeCategoryRegistration) {
        registry.addRecipeCategories(ManualUsageHandler.ManualUsageRecipeCategory)
        registry.addRecipeCategories(CallbackDocHandler.CallbackDocRecipeCategory)
    }

    override fun register(registry: IModRegistry) {
        if (Settings.get.lootRecrafting) {
            registry.handleRecipes(classOf<LootDiskCyclingRecipe>(), LootDiskCyclingRecipeHandler, VanillaRecipeCategoryUid.CRAFTING)
        }

        ItemBlacklist.hiddenItems.forEach { getter ->
            registry.jeiHelpers.ingredientBlacklist.addIngredientToBlacklist(getter())
        }

        // This could go into the Description category, but Manual should always be in front of the Callback doc.
        ManualUsageHandler.ManualUsageRecipeCategory.initialize(registry.jeiHelpers.guiHelper)
        CallbackDocHandler.CallbackDocRecipeCategory.initialize(registry.jeiHelpers.guiHelper)

        registry.handleRecipes(classOf<ManualUsageRecipe>(), ManualUsageHandler.ManualUsageRecipeHandler, ManualUsageHandler.ManualUsageRecipeCategory.uid)
        registry.handleRecipes(classOf<CallbackDocRecipe>(), CallbackDocHandler.CallbackDocRecipeHandler, CallbackDocHandler.CallbackDocRecipeCategory.uid)

        registry.addRecipes(ManualUsageHandler.getRecipes(registry), ManualUsageHandler.ManualUsageRecipeCategory.uid)
        registry.addRecipes(CallbackDocHandler.getRecipes(registry), CallbackDocHandler.CallbackDocRecipeCategory.uid)

        registry.addAdvancedGuiHandlers(RelayGuiHandler)

        ModJEI.ingredientRegistry = registry.ingredientRegistry
    }

    private var stackUnderMouse: ((GuiContainer, Int, Int) -> ItemStack?)? = null

    override fun onRuntimeAvailable(jeiRuntime: IJeiRuntime) {
        if (stackUnderMouse == null) {
            ItemSearch.stackFocusing.add { container, mouseX, mouseY ->
                stackUnderMouse?.invoke(container, mouseX, mouseY)
            }
        }
        stackUnderMouse = { _, _, _ -> jeiRuntime.itemListOverlay.stackUnderMouse.notEmpty() }

        ModJEI.runtime = jeiRuntime
    }

    override fun registerIngredients(registry: IModIngredientRegistration) {
    }

    override fun registerItemSubtypes(subtypeRegistry: ISubtypeRegistry) {
        fun useNBT(vararg names: String) {
            names.mapNotNull { name ->
                val info = Items.get(name)!!
                Item.getItemFromBlock(info.block()) ?: info.item()
            }.distinct().forEach {
                subtypeRegistry.useNbtForSubtypes(it)
            }
        }

        // Only the preconfigured blocks and items have to be here.
        useNBT(
            Constants.BlockName.Microcontroller,
            Constants.BlockName.Robot,

            Constants.ItemName.Drone,
            Constants.ItemName.Tablet
        )

        subtypeRegistry.registerSubtypeInterpreter(Constants.ItemInfo.Floppy.item(), object : ISubtypeInterpreter {
            override fun apply(stack: ItemStack): String? {
                if (!stack.hasTagCompound()) return null
                val compound: NBTTagCompound = stack.tagCompound ?: return null
                val data = NBTTagCompound()
                // Separate loot disks from normal floppies
                if (compound.hasKey(Settings.namespace + "lootFactory")) {
                    data.setTag(Settings.namespace + "lootFactory", compound.getTag(Settings.namespace + "lootFactory"))
                }
                return if (data.isEmpty) null else data.toString()
            }
        })
    }

    private inline fun <reified T> classOf(): Class<T> = T::class.java
}
