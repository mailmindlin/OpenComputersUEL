package li.cil.oc.integration.jei

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.server.machine.Callbacks
import mezz.jei.api.IGuiHelper
import mezz.jei.api.IModRegistry
import mezz.jei.api.gui.IDrawable
import mezz.jei.api.gui.IRecipeLayout
import mezz.jei.api.ingredients.IIngredients
import mezz.jei.api.ingredients.VanillaTypes
import mezz.jei.api.recipe.IRecipeCategory
import mezz.jei.api.recipe.IRecipeWrapper
import mezz.jei.api.recipe.IRecipeWrapperFactory
import net.minecraft.client.Minecraft
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraft.util.text.TextFormatting
import javax.annotation.Nonnull

/** Component callback recipes for JEI */
internal object CallbackDocHandler {
    fun getRecipes(registry: IModRegistry): List<CallbackDocRecipe> {
        return registry.ingredientRegistry.getAllIngredients(VanillaTypes.ITEM)
            .flatMap { stack: ItemStack ->
                val callbacks = (Driver.environmentsFor(stack) ?: emptySet<Class<*>>())
                    .flatMapTo(mutableListOf(), ::getCallbacks)
                if (callbacks.isEmpty())
                    return emptyList()

                val pages = mutableListOf<String>()
                callbacks.sort()
                var lastPage = ""

                for (doc in callbacks) {
                    lastPage = if (lastPage.lines().count() + 2 + doc.lines().count() > 12) {
                        // We've potentially got some pretty long documentation here, split it up first
                        lastPage.lines().chunked(12).forEach { chunk ->
                            pages.add(chunk.joinToString("\n"))
                        }
                        doc
                    } else if (lastPage.isNotEmpty()) {
                        "$lastPage\n\n$doc"
                    } else {
                        doc
                    }
                }
                // The last page may be too long as well.
                lastPage.lines().chunked(12).forEach { chunk ->
                    pages.add(chunk.joinToString("\n"))
                }

                pages.map { page -> CallbackDocRecipe(stack, page) }
            }
    }

    private val DocPattern = Regex("""(?s)^function(\(.*?\).*?) -- (.*)$""")
    private val VexPattern = Regex("""(?s)^function(\(.*?\).*?); (.*)$""")
    /** Parse signature from docstring */
    private fun splitDoc(name: String, doc: String): Pair<String, String> {
        val match = DocPattern.matchEntire(doc) ?: VexPattern.matchEntire(doc) ?: return Pair(name, doc);
        val (head, tail) = match.destructured
        return Pair(name + head, tail)
    }

    /** Get callback names for class (formatted for JEI) */
    private fun getCallbacks(env: Class<*>?): Sequence<String> {
        if (env == null) return emptySequence()

        return Callbacks.fromClass(env)
            .asSequence()
            .map { (name, callback) ->
                val doc = callback.annotation.doc
                if (doc.isNullOrEmpty())
                    return@map name
                val (signatureRaw, documentationRaw) = splitDoc(name, doc)
                val signature = wrap(signatureRaw, 160) { TextFormatting.BLACK.toString() + it }
                val documentation = wrap(documentationRaw, 152) { "  $it" }

                "$signature${TextFormatting.RESET}\n$documentation"
            }
    }

    private fun wrap(line: String, width: Int, fmtLine: (String) -> String): String {
        return Minecraft.getMinecraft().fontRenderer.listFormattedStringToWidth(line, width)
            .joinToString("\n", transform = fmtLine)
    }

    object CallbackDocRecipeHandler : IRecipeWrapperFactory<CallbackDocRecipe> {
        override fun getRecipeWrapper(recipe: CallbackDocRecipe): CallbackDocRecipe = recipe
    }

    class CallbackDocRecipe(val stack: ItemStack, val page: String) : IRecipeWrapper {
        override fun getIngredients(ingredients: IIngredients) {
            ingredients.setInputs(VanillaTypes.ITEM, listOf(stack))
        }

        override fun drawInfo(@Nonnull minecraft: Minecraft, recipeWidth: Int, recipeHeight: Int, mouseX: Int, mouseY: Int) {
            page.lines().forEachIndexed { line, text ->
                minecraft.fontRenderer.drawString(text, 4f, 4f + line * (minecraft.fontRenderer.FONT_HEIGHT + 1), 0x333333, false)
            }
        }
    }

    object CallbackDocRecipeCategory : IRecipeCategory<CallbackDocRecipe> {
        private const val RECIPE_WIDTH: Int = 160
        private const val RECIPE_HEIGHT: Int = 125
        private var background: IDrawable? = null
        private var icon: IDrawable? = null

        fun initialize(guiHelper: IGuiHelper) {
            background = guiHelper.createBlankDrawable(RECIPE_WIDTH, RECIPE_HEIGHT)
            icon = DrawableAnimatedIcon(
                ResourceLocation(Settings.resourceDomain, "textures/items/tablet_on.png"),
                0, 0, 16, 16, 16, 32,
                guiHelper.createTickTimer(20, 1, true),
                0, 16
            )
        }

        override fun getIcon(): IDrawable = icon!!

        override fun getBackground(): IDrawable = background!!

        override fun setRecipe(recipeLayout: IRecipeLayout, recipeWrapper: CallbackDocRecipe, ingredients: IIngredients) {}

        override fun getTitle(): String = "OpenComputers API"

        override fun getUid(): String = "oc.api"

        override fun getModName(): String = OpenComputers.Name
    }
}
