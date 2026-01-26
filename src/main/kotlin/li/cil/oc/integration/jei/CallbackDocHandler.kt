package li.cil.oc.integration.jei

import com.google.common.base.Strings
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.server.machine.Callbacks
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
import net.minecraft.util.text.TextFormatting
import javax.annotation.Nonnull

object CallbackDocHandler {

    private val DocPattern = Regex("""(?s)^function(\(.*?\).*?) -- (.*)$""")

    private val VexPattern = Regex("""(?s)^function(\(.*?\).*?); (.*)$""")

    fun getRecipes(registry: IModRegistry): List<CallbackDocRecipe> {
        return registry.ingredientRegistry.getIngredients(ItemStack::class.java)
            .mapNotNull { stack ->
                val callbacks = (Driver.environmentsFor(stack) ?: emptySet<Class<*>>())
                    .flatMap(::getCallbacks)
                    .toMutableList()

                if (callbacks.isNotEmpty()) {
                    val pages = mutableListOf<String>()
                    val sortedCallbacks = callbacks.sorted().toTypedArray()
                    var lastPage = ""

                    for (doc in sortedCallbacks) {
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
                } else {
                    null
                }
            }
            .flatten()
    }

    private fun getCallbacks(env: Class<*>?): Sequence<String> {
        if (env == null) return emptySequence()

        return Callbacks.fromClass(env).asSequence().map { (name, callback) ->
            val doc = callback.annotation.doc
            if (Strings.isNullOrEmpty(doc)) {
                name
            } else {
                val docMatch = DocPattern.matchEntire(doc)
                val vexMatch = VexPattern.matchEntire(doc)
                val (signature, documentation) = when {
                    docMatch != null -> {
                        val (head, tail) = docMatch.destructured
                        Pair(name + head, tail)
                    }
                    vexMatch != null -> {
                        val (head, tail) = vexMatch.destructured
                        Pair(name + head, tail)
                    }
                    else -> Pair(name, doc)
                }
                wrap(signature, 160).joinToString("\n") { TextFormatting.BLACK.toString() + it } +
                    TextFormatting.RESET + "\n" +
                    wrap(documentation, 152).joinToString("\n") { "  $it" }
            }
        }
    }

    private fun wrap(line: String, width: Int): List<String> {
        return Minecraft.getMinecraft().fontRenderer.listFormattedStringToWidth(line, width)
    }

    object CallbackDocRecipeHandler : IRecipeWrapperFactory<CallbackDocRecipe> {
        override fun getRecipeWrapper(recipe: CallbackDocRecipe): CallbackDocRecipe = recipe
    }

    class CallbackDocRecipe(val stack: ItemStack, val page: String) : BlankRecipeWrapper() {

        override fun getIngredients(ingredients: IIngredients) {
            ingredients.setInputs(ItemStack::class.java, listOf(stack))
        }

        override fun drawInfo(@Nonnull minecraft: Minecraft, recipeWidth: Int, recipeHeight: Int, mouseX: Int, mouseY: Int) {
            page.lines().forEachIndexed { line, text ->
                minecraft.fontRenderer.drawString(text, 4f, 4f + line * (minecraft.fontRenderer.FONT_HEIGHT + 1), 0x333333, false)
            }
        }
    }

    object CallbackDocRecipeCategory : IRecipeCategory<CallbackDocRecipe> {
        const val recipeWidth: Int = 160
        const val recipeHeight: Int = 125
        private var background: IDrawable? = null
        private var icon: IDrawable? = null

        fun initialize(guiHelper: IGuiHelper) {
            background = guiHelper.createBlankDrawable(recipeWidth, recipeHeight)
            icon = DrawableAnimatedIcon(
                ResourceLocation(Settings.resourceDomain, "textures/items/tablet_on.png"),
                0, 0, 16, 16, 16, 32,
                guiHelper.createTickTimer(20, 1, true),
                0, 16
            )
        }

        override fun getIcon(): IDrawable = icon!!

        override fun getBackground(): IDrawable = background!!

        override fun setRecipe(recipeLayout: IRecipeLayout, recipeWrapper: CallbackDocRecipe, ingredients: IIngredients) {
        }

        override fun getTitle(): String = "OpenComputers API"

        override fun getUid(): String = "oc.api"

        override fun getModName(): String = OpenComputers.Name
    }
}
