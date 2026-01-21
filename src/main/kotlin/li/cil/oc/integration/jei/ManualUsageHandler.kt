package li.cil.oc.integration.jei

import li.cil.oc.Localization
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import mezz.jei.api.IGuiHelper
import mezz.jei.api.IModRegistry
import mezz.jei.api.gui.IDrawable
import mezz.jei.api.gui.IRecipeLayout
import mezz.jei.api.ingredients.IIngredients
import mezz.jei.api.recipe.BlankRecipeWrapper
import mezz.jei.api.recipe.IRecipeCategory
import mezz.jei.api.recipe.IRecipeWrapperFactory
import net.minecraft.client.Minecraft
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.client.config.GuiButtonExt
import javax.annotation.Nonnull

object ManualUsageHandler {

    fun getRecipes(registry: IModRegistry): List<ManualUsageRecipe> {
        return registry.ingredientRegistry.getIngredients(ItemStack::class.java)
            .mapNotNull { stack ->
                val path = api.Manual.pathFor(stack)
                if (path is String) {
                    ManualUsageRecipe(stack, path)
                } else {
                    null
                }
            }
    }

    object ManualUsageRecipeHandler : IRecipeWrapperFactory<ManualUsageRecipe> {
        override fun getRecipeWrapper(recipe: ManualUsageRecipe): ManualUsageRecipe = recipe
    }

    class ManualUsageRecipe(val stack: ItemStack, val path: String) : BlankRecipeWrapper() {
        val button: GuiButtonExt by lazy {
            GuiButtonExt(0, (160 - 100) / 2, 10, 100, 20, Localization.localizeImmediately("nei.usage.oc.Manual"))
        }

        override fun getIngredients(ingredients: IIngredients) {
            ingredients.setInputs(ItemStack::class.java, listOf(stack))
        }

        override fun drawInfo(@Nonnull minecraft: Minecraft, recipeWidth: Int, recipeHeight: Int, mouseX: Int, mouseY: Int) {
            button.displayString = Localization.localizeImmediately("nei.usage.oc.Manual")
            button.x = (recipeWidth - button.width) / 2
            button.y = button.height / 2
            button.drawButton(minecraft, mouseX, mouseY, 1f)
        }

        override fun handleClick(@Nonnull minecraft: Minecraft, mouseX: Int, mouseY: Int, mouseButton: Int): Boolean {
            return if (button.mousePressed(minecraft, mouseX, mouseY)) {
                minecraft.player.closeScreen()
                api.Manual.openFor(minecraft.player)
                api.Manual.navigate(path)
                true
            } else {
                false
            }
        }
    }

    object ManualUsageRecipeCategory : IRecipeCategory<ManualUsageRecipe> {
        const val recipeWidth: Int = 160
        const val recipeHeight: Int = 125
        private var background: IDrawable? = null
        private var icon: IDrawable? = null

        fun initialize(guiHelper: IGuiHelper) {
            background = guiHelper.createBlankDrawable(recipeWidth, recipeHeight)
            icon = guiHelper.createDrawable(
                ResourceLocation(Settings.resourceDomain, "textures/items/manual.png"),
                0, 0, 16, 16, 16, 16
            )
        }

        override fun getBackground(): IDrawable = background!!

        override fun getIcon(): IDrawable = icon!!

        override fun setRecipe(recipeLayout: IRecipeLayout, recipeWrapper: ManualUsageRecipe, ingredients: IIngredients) {
        }

        override fun getTitle(): String = "OpenComputers Manual"

        override fun getUid(): String = "oc.manual"

        override fun getModName(): String = OpenComputers.Name
    }
}
