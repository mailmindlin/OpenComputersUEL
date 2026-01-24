package li.cil.oc.client.renderer.block

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.common.init.Items
import li.cil.oc.common.item.CustomModel
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.item.traits.Delegate
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.client.renderer.ItemMeshDefinition
import net.minecraft.client.renderer.block.model.IBakedModel
import net.minecraft.client.renderer.block.model.ModelBakery
import net.minecraft.client.renderer.block.model.ModelResourceLocation
import net.minecraft.client.renderer.block.statemap.StateMapperBase
import net.minecraft.item.Item
import net.minecraft.util.ResourceLocation
import net.minecraft.util.registry.RegistrySimple
import net.minecraftforge.client.event.ModelBakeEvent
import net.minecraftforge.client.event.ModelRegistryEvent
import net.minecraftforge.client.model.ModelLoader
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object ModelInitialization {
    val CableBlockLocation = ModelResourceLocation("${Settings.resourceDomain}:${Constants.BlockName.Cable}", "normal")
    val CableItemLocation = ModelResourceLocation("${Settings.resourceDomain}:${Constants.BlockName.Cable}", "inventory")
    val NetSplitterBlockLocation = ModelResourceLocation("${Settings.resourceDomain}:${Constants.BlockName.NetSplitter}", "normal")
    val NetSplitterItemLocation = ModelResourceLocation("${Settings.resourceDomain}:${Constants.BlockName.NetSplitter}", "inventory")
    val PrintBlockLocation = ModelResourceLocation("${Settings.resourceDomain}:${Constants.BlockName.Print}", "normal")
    val PrintItemLocation = ModelResourceLocation("${Settings.resourceDomain}:${Constants.BlockName.Print}", "inventory")
    val RobotBlockLocation = ModelResourceLocation("${Settings.resourceDomain}:${Constants.BlockName.Robot}", "normal")
    val RobotItemLocation = ModelResourceLocation("${Settings.resourceDomain}:${Constants.BlockName.Robot}", "inventory")
    val RobotAfterimageBlockLocation = ModelResourceLocation("${Settings.resourceDomain}:${Constants.BlockName.RobotAfterimage}", "normal")
    val RobotAfterimageItemLocation = ModelResourceLocation("${Settings.resourceDomain}:${Constants.BlockName.RobotAfterimage}", "inventory")
    val RackBlockLocation = ModelResourceLocation("${Settings.resourceDomain}:${Constants.BlockName.Rack}", "normal")

    private val meshableItems = mutableListOf<Item>()
    private val itemDelegates = mutableListOf<Pair<String, Delegate>>()
    /** Note: type is `CustomModel + Delegate` */
    private val itemDelegatesCustom = mutableListOf<CustomModel>()

    fun preInit() {
        MinecraftForge.EVENT_BUS.register(this)

        registerModel(Constants.BlockName.Cable, CableBlockLocation, CableItemLocation)
        registerModel(Constants.BlockName.NetSplitter, NetSplitterBlockLocation, NetSplitterItemLocation)
        registerModel(Constants.BlockName.Print, PrintBlockLocation, PrintItemLocation)
        registerModel(Constants.BlockName.Robot, RobotBlockLocation, RobotItemLocation)
        registerModel(Constants.BlockName.RobotAfterimage, RobotAfterimageBlockLocation, RobotAfterimageItemLocation)
    }

    @SubscribeEvent
    @Suppress("unused")
    fun onModelRegistration(event: ModelRegistryEvent) {
        registerItems()
        registerSubItems()
        registerSubItemsCustom()
    }

    // -----------------------------------------------------------------------

    fun registerModel(instance: Delegate, id: String) {
        if (instance is CustomModel) {
            itemDelegatesCustom.add(instance)
        } else {
            itemDelegates.add(id to instance)
        }
    }

    fun registerModel(instance: Item, id: String) {
        meshableItems.add(instance)
    }

    fun registerModel(instance: Block, id: String) {
        val item = Item.getItemFromBlock(instance)
        registerModel(item, id)
    }

    // -----------------------------------------------------------------------

    private fun registerModel(blockName: String, blockLocation: ModelResourceLocation, itemLocation: ModelResourceLocation) {
        val descriptor = Items.get(blockName)!!
        val block = descriptor.block()
        val stack = descriptor.createItemStack(1)

        ModelLoader.setCustomModelResourceLocation(stack.item, stack.metadata, itemLocation)
        ModelLoader.setCustomStateMapper(block, object : StateMapperBase() {
            override fun getModelResourceLocation(state: IBlockState): ModelResourceLocation = blockLocation
        })
    }

    private fun registerItems() {
        val meshDefinition = ItemMeshDefinition { stack ->
            val descriptor = Items.get(stack)
            if (descriptor != null) {
                val location = "${Settings.resourceDomain}:${descriptor.name()}"
                ModelResourceLocation(location, "inventory")
            } else {
                null
            }
        }

        for (item in meshableItems) {
            ModelLoader.setCustomMeshDefinition(item, meshDefinition)
        }
        meshableItems.clear()
    }

    private fun registerSubItems() {
        for ((id, item) in itemDelegates) {
            val location = "${Settings.resourceDomain}:$id"
            ModelLoader.setCustomModelResourceLocation(item.parent, item.itemId, ModelResourceLocation(location, "inventory"))
            ModelBakery.registerItemVariants(item.parent, ResourceLocation(location))
        }
        itemDelegates.clear()
    }

    private fun registerSubItemsCustom() {
        for (item in itemDelegatesCustom) {
            ModelLoader.setCustomMeshDefinition((item as Delegate).parent) { stack ->
                val subItem = Delegator.subItem(stack)
                if (subItem is CustomModel) {
                    subItem.getModelLocation(stack)
                } else {
                    null
                }
            }
            item.registerModelLocations()
        }
    }

    // -----------------------------------------------------------------------

    @SubscribeEvent
    @Suppress("unused")
    fun onModelBake(e: ModelBakeEvent) {
        val registry = e.modelRegistry as RegistrySimple<ModelResourceLocation, IBakedModel>

        registry.putObject(CableBlockLocation, CableModel)
        registry.putObject(CableItemLocation, CableModel)
        registry.putObject(NetSplitterBlockLocation, NetSplitterModel)
        registry.putObject(NetSplitterItemLocation, NetSplitterModel)
        registry.putObject(PrintBlockLocation, PrintModel)
        registry.putObject(PrintItemLocation, PrintModel)
        registry.putObject(RobotBlockLocation, RobotModel)
        registry.putObject(RobotItemLocation, RobotModel)
        registry.putObject(RobotAfterimageBlockLocation, NullModel)
        registry.putObject(RobotAfterimageItemLocation, NullModel)

        for (item in itemDelegatesCustom) {
            item.bakeModels(e)
        }

        val modelOverrides = mapOf<String, (IBakedModel) -> IBakedModel>(
            Constants.BlockName.ScreenTier1 to { ScreenModel },
            Constants.BlockName.ScreenTier2 to { ScreenModel },
            Constants.BlockName.ScreenTier3 to { ScreenModel },
            Constants.BlockName.Rack to { parent -> ServerRackModel(parent) }
        )

        for (location in registry.keys.filterIsInstance<ModelResourceLocation>()) {
            val parent = registry.getObject(location)
            if (parent is IBakedModel) {
                for ((name, model) in modelOverrides) {
                    val pattern = "^${Settings.resourceDomain}:$name#.*"
                    if (location.toString().matches(Regex(pattern))) {
                        registry.putObject(location, model(parent))
                    }
                }
            }
        }
    }
}
