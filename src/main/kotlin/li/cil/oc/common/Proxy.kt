package li.cil.oc.common

import com.google.common.base.Strings
import li.cil.oc.Constants
import li.cil.oc.CreativeTab
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.*
import li.cil.oc.common.capabilities.Capabilities
import li.cil.oc.common.entity.Drone
import li.cil.oc.common.init.Blocks
import li.cil.oc.common.init.Items
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.item.DiamondChip
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.recipe.Recipes
import li.cil.oc.integration.Mods
import li.cil.oc.server.driver.Registry
import li.cil.oc.server.fs.FileSystem
import li.cil.oc.server.machine.Machine
import li.cil.oc.server.machine.luac.LuaStateFactory
import li.cil.oc.server.machine.luac.NativeLua52Architecture
import li.cil.oc.server.machine.luac.NativeLua53Architecture
import li.cil.oc.server.machine.luac.NativeLua54Architecture
import li.cil.oc.server.machine.luaj.LuaJLuaArchitecture
import li.cil.oc.server.nanomachines.Nanomachines
import li.cil.oc.server.network.Network
import net.minecraft.block.Block
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.RegistryEvent.MissingMappings
import net.minecraftforge.fml.common.FMLLog
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.network.NetworkRegistry
import net.minecraftforge.fml.common.registry.EntityRegistry
import net.minecraftforge.oredict.OreDictionary
import java.io.File

open class Proxy {
    open fun preInit(e: FMLPreInitializationEvent) {
        checkForBrokenJavaVersion()

        Settings.load(File(e.modConfigurationDirectory, "opencomputers${File.separator}settings.conf"))

        MinecraftForge.EVENT_BUS.register(this)

        OpenComputers.log.debug("Initializing blocks and items.")

        Blocks.init()
        Items.init()

        OpenComputers.log.debug("Initializing additional OreDict entries.")

        OreDictionary.registerOre("craftingPiston", net.minecraft.init.Blocks.PISTON)
        OreDictionary.registerOre("craftingPiston", net.minecraft.init.Blocks.STICKY_PISTON)
        OreDictionary.registerOre("torchRedstoneActive", net.minecraft.init.Blocks.REDSTONE_TORCH)
        OreDictionary.registerOre("materialEnderPearl", net.minecraft.init.Items.ENDER_PEARL)

        // Make mods that use old wireless card name not have broken recipes
        OreDictionary.registerOre("oc:wlanCard", Items.get(Constants.ItemName.WirelessNetworkCardTier2).createItemStack(1))

        tryRegisterNugget<DiamondChip>(Constants.ItemName.DiamondChip, "chipDiamond", net.minecraft.init.Items.DIAMOND, "gemDiamond")

        // Avoid issues with Extra Utilities registering colored obsidian as `obsidian`
        // oredict entry, but not normal obsidian, breaking some recipes.
        OreDictionary.registerOre("obsidian", net.minecraft.init.Blocks.OBSIDIAN)

        // To still allow using normal endstone for crafting drones.
        OreDictionary.registerOre("oc:stoneEndstone", net.minecraft.init.Blocks.END_STONE)

        OpenComputers.log.info("Initializing OpenComputers API.")

        api.CreativeTab.instance = CreativeTab
        api.API.driver = Registry
        api.API.fileSystem = FileSystem
        api.API.items = Items
        api.API.machine = Machine
        api.API.nanomachines = Nanomachines
        api.API.network = Network

        api.API.config = Settings.get.config

        if (LuaStateFactory.isAvailable) {
            if (LuaStateFactory.include53) {
                api.Machine.add(NativeLua53Architecture::class.java)
            }
            if (LuaStateFactory.include54) {
                api.Machine.add(NativeLua54Architecture::class.java)
            }
            if (LuaStateFactory.include52) {
                api.Machine.add(NativeLua52Architecture::class.java)
            }
        }
        if (LuaStateFactory.includeLuaJ) {
            api.Machine.add(LuaJLuaArchitecture::class.java)
        }

        api.Machine.LuaArchitecture =
            if (Settings.get.forceLuaJ) LuaJLuaArchitecture::class.java
            else api.Machine.architectures().first()
    }

    open fun init(e: FMLInitializationEvent) {
        OpenComputers.channel = NetworkRegistry.INSTANCE.newEventDrivenChannel("OpenComputers")
        OpenComputers.channel.register(li.cil.oc.server.PacketHandler)

        Loot.init()
        Achievement.init()

        EntityRegistry.registerModEntity(ResourceLocation(Settings.resourceDomain, "drone"), Drone::class.java, "Drone", 0, OpenComputers, 80, 1, true)

        OpenComputers.log.debug("Initializing mod integration.")
        Mods.init()

        OpenComputers.log.debug("Initializing recipes.")
        Recipes.init()

        OpenComputers.log.info("Initializing capabilities.")
        Capabilities.init()

        api.API.isPowerEnabled = !Settings.get.ignorePower
    }

    open fun postInit(e: FMLPostInitializationEvent) {
        // Don't allow driver registration after this point, to avoid issues.
        Registry.locked = true
    }

    inline fun <reified TItem : Delegate> tryRegisterNugget(nuggetItemName: String, nuggetOredictName: String, ingotItem: Item, ingotOredictName: String) {
        val nugget = Items.get(nuggetItemName).createItemStack(1)

        registerExclusive(nuggetOredictName, nugget)

        val subItem = Delegator.subItem(nugget)
        if (subItem is TItem) {
            if (OreDictionary.getOres(nuggetOredictName).any { nugget.isItemEqual(it) }) {
                Recipes.addSubItem(subItem, nuggetItemName)
                Recipes.addItem(ingotItem, ingotOredictName)
            } else {
                subItem.showInItemList = false
            }
        }
    }

    open fun registerModel(instance: Delegate, id: String) {}

    open fun registerModel(instance: Item, id: String) {}

    open fun registerModel(instance: Block, id: String) {}

    private fun registerExclusive(name: String, vararg items: ItemStack) {
        if (OreDictionary.getOres(name).isEmpty()) {
            for (item in items) {
                OreDictionary.registerOre(name, item)
            }
        }
    }

    // Yes, this could be boiled down even further, but I like to keep it
    // explicit like this, because it makes it a) clearer, b) easier to
    // extend, in case that should ever be needed.

    // Example usage: OpenComputers.ID + ":rack" -> "serverRack"
    private val blockRenames = mapOf(
        "${OpenComputers.ID}:serverRack" to Constants.BlockName.Rack // Yay, full circle >_>
    )

    // Example usage: OpenComputers.ID + ":tabletCase" -> "tabletCase1"
    private val itemRenames = mapOf(
        "${OpenComputers.ID}:dataCard" to Constants.ItemName.DataCardTier1,
        "${OpenComputers.ID}:serverRack" to Constants.BlockName.Rack,
        "${OpenComputers.ID}:wlanCard" to Constants.ItemName.WirelessNetworkCardTier2
    )

    @SubscribeEvent
    fun missingBlockMappings(e: MissingMappings<Block>) {
        for (missing in e.mappings) {
            val name = blockRenames[missing.key.path]
            if (name != null) {
                if (Strings.isNullOrEmpty(name)) {
                    missing.ignore()
                } else {
                    missing.remap(Block.REGISTRY.getObject(ResourceLocation(OpenComputers.ID, name)))
                }
            } else {
                missing.warn()
            }
        }
    }

    @SubscribeEvent
    fun missingItemMappings(e: MissingMappings<Item>) {
        for (missing in e.mappings) {
            val name = itemRenames[missing.key.path]
            if (name != null) {
                if (Strings.isNullOrEmpty(name)) {
                    missing.ignore()
                } else {
                    missing.remap(Item.REGISTRY.getObject(ResourceLocation(OpenComputers.ID, name)))
                }
            } else {
                missing.warn()
            }
        }
    }

    // OK, seriously now, I've gotten one too many bug reports because of this Java version being broken.

    private val BrokenJavaVersions = setOf("1.6.0_65, Apple Inc.")

    val isBrokenJavaVersion: Boolean
        get() {
            val javaVersion = "${System.getProperty("java.version")}, ${System.getProperty("java.vendor")}"
            return BrokenJavaVersions.contains(javaVersion)
        }

    fun checkForBrokenJavaVersion() {
        if (isBrokenJavaVersion) {
            FMLLog.bigWarning("You're using a broken Java version! Please update now, or remove OpenComputers. DO NOT REPORT THIS! UPDATE YOUR JAVA!")
            throw Exception("You're using a broken Java version! Please update now, or remove OpenComputers. DO NOT REPORT THIS! UPDATE YOUR JAVA!")
        }
    }
}
