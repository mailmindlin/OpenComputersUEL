package li.cil.oc.common.recipe

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigIncludeContext
import com.typesafe.config.ConfigIncluder
import com.typesafe.config.ConfigIncluderFile
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigSyntax
import com.typesafe.config.ConfigValueType
import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.common.Loot
import li.cil.oc.common.block.SimpleBlock
import li.cil.oc.common.init.Items
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.item.data.PrintData
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.SimpleItem
import li.cil.oc.integration.util.ItemBlacklist
import li.cil.oc.util.Color
import net.minecraft.block.Block
import net.minecraft.item.Item
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.item.crafting.IRecipe
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.ResourceLocation
import net.minecraft.util.registry.RegistryNamespaced
import net.minecraftforge.fluids.FluidRegistry
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.oredict.OreDictionary
import net.minecraftforge.oredict.RecipeSorter
import net.minecraftforge.oredict.RecipeSorter.Category
import net.minecraftforge.registries.GameData
import net.minecraftforge.registries.IForgeRegistryEntry
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.FileReader

object Recipes {
    @JvmField
    val list: MutableMap<ItemStack, String> = linkedMapOf()

    @JvmField
    val oreDictEntries: MutableMap<String, ItemStack> = linkedMapOf()

    @JvmField
    var hadErrors = false

    @JvmField
    val recipeHandlers: MutableMap<String, (ItemStack, Config) -> Unit> = linkedMapOf()

    @JvmStatic
    fun registerRecipeHandler(name: String, recipe: (ItemStack, Config) -> Unit) {
        recipeHandlers[name] = recipe
    }

    @JvmStatic
    fun addBlock(instance: Block, name: String, vararg oreDict: String): Block {
        Items.registerBlock(instance, name)
        addRecipe(if (instance is SimpleBlock) instance.createItemStack() else ItemStack(instance), name)
        register(if (instance is SimpleBlock) instance.createItemStack() else ItemStack(instance), *oreDict)
        return instance
    }

    @JvmStatic
    fun <T : Delegate> addSubItem(delegate: T, name: String, vararg oreDict: String): T {
        Items.registerItem(delegate, name)
        addRecipe(delegate.createItemStack(), name)
        register(delegate.createItemStack(), *oreDict)
        return delegate
    }

    @JvmStatic
    fun addItem(instance: Item, name: String, vararg oreDict: String): Item {
        Items.registerItem(instance, name)
        addRecipe(if (instance is SimpleItem) instance.createItemStack() else ItemStack(instance), name)
        register(if (instance is SimpleItem) instance.createItemStack() else ItemStack(instance), *oreDict)
        return instance
    }

    @JvmStatic
    fun <T : Delegate> addSubItem(delegate: T, name: String, registerRecipe: Boolean, vararg oreDict: String): T {
        Items.registerItem(delegate, name)
        if (registerRecipe) {
            addRecipe(delegate.createItemStack(), name)
            register(delegate.createItemStack(), *oreDict)
        } else {
            ItemBlacklist.hide(delegate)
        }
        return delegate
    }

    @JvmStatic
    fun addStack(stack: ItemStack, name: String, vararg oreDict: String): ItemStack {
        Items.registerStack(stack, name)
        addRecipe(stack, name)
        register(stack, *oreDict)
        return stack
    }

    @JvmStatic
    fun addRecipe(stack: ItemStack, name: String) {
        list[stack] = name
    }

    private fun register(item: ItemStack, vararg names: String) {
        for (name in names) {
            oreDictEntries[name] = item
        }
    }

    @JvmStatic
    fun init() {
        RecipeSorter.register(Settings.namespace + "extshaped", ExtendedShapedOreRecipe::class.java, Category.SHAPED, "after:forge:shapedore")
        RecipeSorter.register(Settings.namespace + "extshapeless", ExtendedShapelessOreRecipe::class.java, Category.SHAPELESS, "after:forge:shapelessore")
        RecipeSorter.register(Settings.namespace + "colorizer", ColorizeRecipe::class.java, Category.SHAPELESS, "after:forge:shapelessore")
        RecipeSorter.register(Settings.namespace + "decolorizer", DecolorizeRecipe::class.java, Category.SHAPELESS, "after:oc:colorizer")
        RecipeSorter.register(Settings.namespace + "lootcycler", LootDiskCyclingRecipe::class.java, Category.SHAPELESS, "after:forge:shapelessore")

        for ((name, stack) in oreDictEntries) {
            if (!OreDictionary.getOres(name).contains(stack)) {
                OreDictionary.registerOre(name, stack)
            }
        }
        oreDictEntries.clear()

        try {
            val recipeSets = arrayOf("default", "hardmode", "gregtech", "peaceful")
            val recipeDirectory = File(Loader.instance().configDir.toString() + File.separator + "opencomputers")
            val userRecipes = File(recipeDirectory, "user.recipes")
            userRecipes.parentFile.mkdirs()
            if (!userRecipes.exists()) {
                FileUtils.copyURLToFile(Recipes::class.java.getResource("/assets/opencomputers/recipes/user.recipes"), userRecipes)
            }
            for (recipeSet in recipeSets) {
                FileUtils.copyURLToFile(Recipes::class.java.getResource("/assets/opencomputers/recipes/$recipeSet.recipes"), File(recipeDirectory, "$recipeSet.recipes"))
            }

            var config: ConfigParseOptions? = null
            config = ConfigParseOptions.defaults()
                .setSyntax(ConfigSyntax.CONF)
                .setIncluder(object : ConfigIncluder, ConfigIncluderFile {
                    var fallback: ConfigIncluder? = null

                    override fun withFallback(fallback: ConfigIncluder): ConfigIncluder {
                        this.fallback = fallback
                        return this
                    }

                    override fun include(context: ConfigIncludeContext, what: String): ConfigObject =
                        fallback!!.include(context, what)

                    override fun includeFile(context: ConfigIncludeContext, what: File): ConfigObject {
                        val input = if (what.isAbsolute) FileReader(what) else FileReader(File(userRecipes.parentFile, what.path))
                        val result = ConfigFactory.parseReader(input, config!!)
                        input.close()
                        return result.root()
                    }
                })

            val recipes = ConfigFactory.parseFile(userRecipes, config)

            // Register all known recipes.
            for ((stack, name) in list) {
                if (recipes.hasPath(name)) {
                    val value = recipes.getValue(name)
                    when (value.valueType()) {
                        ConfigValueType.OBJECT -> addRecipe(stack, recipes.getConfig(name), "'$name'")
                        ConfigValueType.BOOLEAN -> {
                            // Explicitly disabled, keep in NEI if true.
                            if (!(value.unwrapped() as Boolean)) {
                                hide(stack)
                            }
                        }
                        else -> {
                            OpenComputers.log.error("Failed adding recipe for '$name', you will not be able to craft this item. The error was: Invalid value for recipe.")
                            hadErrors = true
                        }
                    }
                } else {
                    OpenComputers.log.warn("No recipe for '$name', you will not be able to craft this item. To suppress this warning, disable the recipe (assign `false` to it).")
                    hadErrors = true
                }
            }

            // Register all unknown recipes. Well. Loot disk recipes.
            if (recipes.hasPath("lootdisks")) {
                try {
                    val lootRecipes = recipes.getConfigList("lootdisks")
                    val lootStacks = Loot.globalDisks.map { it.first }
                    for (recipe in lootRecipes) {
                        val name = recipe.getString("name")
                        val stack = lootStacks.find { s -> s.tagCompound?.getString(Settings.namespace + "lootFactory") == name }
                        if (stack != null) {
                            addRecipe(stack, recipe, "loot disk '$name'")
                        } else {
                            OpenComputers.log.warn("Failed adding recipe for loot disk '$name': No such global loot disk.")
                            hadErrors = true
                        }
                    }
                } catch (t: Throwable) {
                    OpenComputers.log.warn("Failed parsing loot disk recipes.", t)
                    hadErrors = true
                }
            }

            if (recipes.hasPath("generic")) {
                try {
                    val genericRecipes = recipes.getConfigList("generic")
                    for (recipe in genericRecipes) {
                        val result = recipe.getValue("result").unwrapped()
                        val stack = parseIngredient(result)
                        if (stack is ItemStack) {
                            addRecipe(stack, recipe, "'$result'")
                        } else {
                            OpenComputers.log.warn("Failed adding generic recipe for '$result': Invalid output (make sure it's not an OreDictionary name).")
                            hadErrors = true
                        }
                    }
                } catch (t: Throwable) {
                    OpenComputers.log.warn("Failed parsing generic recipes.", t)
                    hadErrors = true
                }
            }

            // Recrafting operations.
            val cable = Constants.BlockInfo.Cable
            val chamelium = Constants.ItemInfo.Chamelium
            val chameliumBlock = Constants.BlockInfo.ChameliumBlock
            val drone = Constants.ItemInfo.Drone
            val eeprom = Constants.ItemInfo.EEPROM
            val floppy = Constants.ItemInfo.Floppy
            val hoverBoots = Constants.ItemInfo.HoverBoots
            val mcu = Constants.BlockInfo.Microcontroller
            val navigationUpgrade = Constants.ItemInfo.NavigationUpgrade
            val print = Constants.BlockInfo.Print
            val relay = Constants.BlockInfo.Relay
            val robot = Constants.BlockInfo.Robot
            val tablet = Constants.ItemInfo.Tablet
            val linkedCard = Constants.ItemInfo.LinkedCard

            // Navigation upgrade recrafting.
            addRecipe(ExtendedShapelessOreRecipe(
                navigationUpgrade.createItemStack(1),
                navigationUpgrade.createItemStack(1), ItemStack(net.minecraft.init.Items.FILLED_MAP, 1, OreDictionary.WILDCARD_VALUE)))

            // Floppy disk coloring.
            for (dye in Color.dyes) {
                val result = floppy.createItemStack(1)
                val tag = NBTTagCompound()
                tag.setInteger(Settings.namespace + "color", Color.dyes.indexOf(dye))
                result.tagCompound = tag
                addRecipe(ExtendedShapelessOreRecipe(result, floppy.createItemStack(1), dye))
            }

            // Microcontroller recrafting.
            addRecipe(ExtendedShapelessOreRecipe(
                mcu.createItemStack(1),
                mcu.createItemStack(1), eeprom.createItemStack(1)))

            // Drone recrafting.
            addRecipe(ExtendedFuzzyShapelessRecipe(
                drone.createItemStack(1),
                drone.createItemStack(1), eeprom.createItemStack(1)))

            // EEPROM copying via crafting.
            addRecipe(ExtendedShapelessOreRecipe(
                eeprom.createItemStack(2),
                eeprom.createItemStack(1), eeprom.createItemStack(1)))

            // Robot recrafting.
            addRecipe(ExtendedFuzzyShapelessRecipe(
                robot.createItemStack(1),
                robot.createItemStack(1), eeprom.createItemStack(1)))

            // Tablet recrafting.
            addRecipe(ExtendedShapelessOreRecipe(
                tablet.createItemStack(1),
                tablet.createItemStack(1), eeprom.createItemStack(1)))

            // Chamelium block splitting.
            addRecipe(ExtendedShapelessOreRecipe(
                chamelium.createItemStack(9),
                chameliumBlock.createItemStack(1)))

            // Chamelium dying.
            for ((meta, dye) in Color.dyes.withIndex()) {
                val result = chameliumBlock.createItemStack(1)
                result.itemDamage = meta
                val input = chameliumBlock.createItemStack(1)
                input.itemDamage = OreDictionary.WILDCARD_VALUE
                addRecipe(ExtendedShapelessOreRecipe(result, input, dye))
            }

            // Print beaconification.
            val beaconPrint = print.createItemStack(1)

            run {
                val printData = PrintData(beaconPrint)
                printData.isBeaconBase = true
                printData.save(beaconPrint)
            }

            for (block in arrayOf(
                net.minecraft.init.Blocks.IRON_BLOCK,
                net.minecraft.init.Blocks.GOLD_BLOCK,
                net.minecraft.init.Blocks.EMERALD_BLOCK,
                net.minecraft.init.Blocks.DIAMOND_BLOCK
            )) {
                addRecipe(ExtendedShapelessOreRecipe(beaconPrint, print.createItemStack(1), ItemStack(block)))
            }

            // Floppy disk formatting.
            addRecipe(ExtendedShapelessOreRecipe(floppy.createItemStack(1), floppy.createItemStack(1)))

            // Hard disk formatting.
            val hdds = arrayOf(
                Constants.ItemInfo.HDDTier1,
                Constants.ItemInfo.HDDTier2,
                Constants.ItemInfo.HDDTier3
            )
            for (hdd in hdds) {
                addRecipe(ExtendedShapelessOreRecipe(hdd.createItemStack(1), hdd.createItemStack(1)))
            }

            // EEPROM formatting.
            addRecipe(ExtendedShapelessOreRecipe(eeprom.createItemStack(1), eeprom.createItemStack(1)))

            // Print light value increments.
            val lightPrint = print.createItemStack(1)

            run {
                val printData = PrintData(lightPrint)
                printData.lightLevel = 1
                printData.save(lightPrint)
            }

            addRecipe(ExtendedShapelessOreRecipe(
                lightPrint,
                print.createItemStack(1), ItemStack(net.minecraft.init.Items.GLOWSTONE_DUST)))

            run {
                val printData = PrintData(lightPrint)
                printData.lightLevel = 4
                printData.save(lightPrint)
            }

            addRecipe(ExtendedShapelessOreRecipe(
                lightPrint,
                print.createItemStack(1), ItemStack(net.minecraft.init.Blocks.GLOWSTONE)))

            // Hover Boot dyeing
            addRecipe(ColorizeRecipe(hoverBoots.item()), "colorizeBoots")
            addRecipe(DecolorizeRecipe(hoverBoots.item()), "decolorizeBoots")

            // Cable dyeing
            addRecipe(ColorizeRecipe(cable.block()), "colorizeCable")
            addRecipe(DecolorizeRecipe(cable.block()), "decolorizeCable")

            // Loot disk cycling.
            if (Settings.get.lootRecrafting) {
                addRecipe(LootDiskCyclingRecipe(), "lootCycling")
            }

            // link card copying via crafting.
            addRecipe(ExtendedShapelessOreRecipe(
                linkedCard.createItemStack(2),
                linkedCard.createItemStack(1), linkedCard.createItemStack(1)))

        } catch (e: Throwable) {
            OpenComputers.log.error("Error parsing recipes, you may not be able to craft any items from this mod!", e)
        }
        list.clear()
    }

    private fun addRecipe(output: ItemStack, recipe: Config, name: String) {
        try {
            val recipeType = tryGetType(recipe)
            val handler = recipeHandlers[recipeType]
            if (handler != null) {
                handler(output, recipe)
            } else {
                OpenComputers.log.error("Failed adding recipe for $name, you will not be able to craft this item. The error was: Invalid recipe type '$recipeType'.")
                hadErrors = true
            }
        } catch (e: RecipeException) {
            OpenComputers.log.error("Failed adding recipe for $name, you will not be able to craft this item.", e)
            hadErrors = true
        }
    }

    @JvmStatic
    fun tryGetCount(recipe: Config): Int = if (recipe.hasPath("output")) recipe.getInt("output") else 1

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun parseIngredient(entry: Any?): Any {
        return when (entry) {
            is Map<*, *> -> {
                val map = entry as Map<Any, Any>
                when {
                    map.containsKey("oreDict") -> {
                        when (val value = map["oreDict"]) {
                            is String -> value
                            else -> throw RecipeException("Invalid name in recipe (not a string: $value).")
                        }
                    }
                    map.containsKey("item") -> {
                        when (val name = map["item"]) {
                            is String -> {
                                findItem(name)?.let { item ->
                                    ItemStack(item, 1, tryGetId(map))
                                } ?: throw RecipeException("No item found with name '$name'.")
                            }
                            is Number -> ItemStack(validateItemId(name), 1, tryGetId(map))
                            else -> throw RecipeException("Invalid item name in recipe (not a string: $name).")
                        }
                    }
                    map.containsKey("block") -> {
                        when (val name = map["block"]) {
                            is String -> {
                                findBlock(name)?.let { block ->
                                    ItemStack(block, 1, tryGetId(map))
                                } ?: throw RecipeException("No block found with name '$name'.")
                            }
                            is Number -> ItemStack(validateBlockId(name), 1, tryGetId(map))
                            else -> throw RecipeException("Invalid block name (not a string: $name).")
                        }
                    }
                    else -> throw RecipeException("Invalid ingredient type (no oreDict, item or block entry).")
                }
            }
            is String -> {
                when {
                    entry.isBlank() -> ItemStack.EMPTY
                    OreDictionary.getOres(entry)?.isNotEmpty() == true -> entry
                    else -> {
                        findItem(entry)?.let { item ->
                            ItemStack(item, 1, 0)
                        } ?: findBlock(entry)?.let { block ->
                            ItemStack(block, 1, 0)
                        } ?: throw RecipeException("No ore dictionary entry, item or block found for ingredient with name '$entry'.")
                    }
                }
            }
            else -> throw RecipeException("Invalid ingredient type (not a map or string): $entry")
        }
    }

    @JvmStatic
    fun parseFluidIngredient(entry: Config): FluidStack? {
        val fluid = FluidRegistry.getFluid(entry.getString("name"))
        val amount = if (entry.hasPath("amount")) entry.getInt("amount") else 1000
        return FluidStack(fluid, amount)
    }

    private fun findItem(name: String): Item? {
        return getObjectWithoutFallback(Item.REGISTRY, name)
            ?: Item.REGISTRY.find { item ->
                item.translationKey == name ||
                        item.translationKey == "item.$name" ||
                        Item.REGISTRY.getNameForObject(item).toString() == name
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun findBlock(name: String): Block? {
        return getObjectWithoutFallback(Block.REGISTRY as RegistryNamespaced<ResourceLocation, Block>, name)
            ?: Block.REGISTRY.find { block ->
                block.translationKey == name ||
                        block.translationKey == "tile.$name" ||
                        Block.REGISTRY.getNameForObject(block).toString() == name
            }
    }

    private fun <V> getObjectWithoutFallback(registry: RegistryNamespaced<ResourceLocation, V>, key: String): V? {
        val loc = ResourceLocation(key)
        return if (registry.containsKey(loc)) registry.getObject(loc) else null
    }

    private fun tryGetType(recipe: Config): String = if (recipe.hasPath("type")) recipe.getString("type") else "shaped"

    private fun tryGetId(ingredient: Map<Any, Any>): Int {
        return if (ingredient.containsKey("subID")) {
            when (val id = ingredient["subID"]) {
                is Number -> id.toInt()
                "any" -> OreDictionary.WILDCARD_VALUE
                is String -> id.toInt()
                else -> 0
            }
        } else 0
    }

    private fun validateBlockId(id: Number): Block {
        val index = id.toInt()
        return Block.getBlockById(index) ?: throw RecipeException("Invalid block ID: $index")
    }

    private fun validateItemId(id: Number): Item {
        val index = id.toInt()
        return Item.getItemById(index) ?: throw RecipeException("Invalid item ID: $index")
    }

    private fun hide(value: ItemStack) {
        Delegator.subItem(value)?.let { stack ->
            stack.showInItemList = false
        }
        (value.item as? SimpleItem)?.setCreativeTab(null)
        (value.item as? ItemBlock)?.block?.let { block ->
            if (block is SimpleBlock) {
                block.setCreativeTab(null)
                ItemBlacklist.hide(block)
            }
        }
    }

    class RecipeException(message: String) : RuntimeException(message)

    @JvmStatic
    fun addRecipe(recipe: IForgeRegistryEntry.Impl<IRecipe>, group: String): IRecipe =
        GameData.register_impl(recipe.setRegistryName(Settings.resourceDomain, group))

    private var recipeCounter: Int = 0

    @JvmStatic
    fun addRecipe(recipe: IForgeRegistryEntry.Impl<IRecipe>) {
        when (recipe) {
            is IRecipe -> {
                val output = recipe.recipeOutput
                if (!output.isEmpty) {
                    // Who cares about recipe names?
                    addRecipe(recipe, output.item.registryName!!.path + recipeCounter.toString())
                    recipeCounter++
                } else {
                    throw IllegalArgumentException("invalid recipe name: ${recipe.recipeOutput}")
                }
            }
            else -> throw IllegalArgumentException("invalid recipe name: $recipe")
        }
    }
}
