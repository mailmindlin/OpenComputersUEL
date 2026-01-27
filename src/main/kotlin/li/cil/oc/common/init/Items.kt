package li.cil.oc.common.init

import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.detail.ItemAPI
import li.cil.oc.api.detail.ItemInfo
import li.cil.oc.api.fs.FileSystem
import li.cil.oc.common.Loot
import li.cil.oc.common.Tier
import li.cil.oc.common.block.SimpleBlock
import li.cil.oc.common.item.*
import li.cil.oc.common.item.Analyzer as ItemAnalyzer
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.item.data.DroneData
import li.cil.oc.common.item.data.HoverBootsData
import li.cil.oc.common.item.data.MicrocontrollerData
import li.cil.oc.common.item.data.RobotData
import li.cil.oc.common.item.data.TabletData
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.SimpleItem
import li.cil.oc.common.recipe.Recipes
import li.cil.oc.server.machine.luac.LuaStateFactory
import net.minecraft.block.Block
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.item.EnumDyeColor
import net.minecraft.item.Item
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.NonNullList
import net.minecraft.util.ResourceLocation
import net.minecraftforge.registries.GameData
import java.util.concurrent.Callable

object Items : ItemAPI {
    @JvmField
    val descriptors: MutableMap<String, ItemInfo> = mutableMapOf()

    @JvmField
    val names: MutableMap<Any, String> = mutableMapOf()

    @JvmField
    val aliases: Map<String, String> = mapOf(
        "datacard" to Constants.ItemName.DataCardTier1,
        "wlancard" to Constants.ItemName.WirelessNetworkCardTier2
    )

    override fun get(name: String): ItemInfo? = descriptors[name]

    override fun get(stack: ItemStack): ItemInfo? {
        val blockOrItem = getBlockOrItem(stack)
        return if (blockOrItem != null) {
            names[blockOrItem]?.let { get(it) }
        } else {
            null
        }
    }

    @JvmStatic
    fun registerBlock(instance: Block, id: String): Block {
        if (id !in descriptors) {
            if (instance is SimpleBlock) {
                instance.setTranslationKey("oc.$id")
                instance.setRegistryName(id)
                GameData.register_impl(instance)
                OpenComputers.proxy.registerModel(instance, id)

                val item: Item = li.cil.oc.common.block.Item(instance)
                item.setTranslationKey("oc.$id")
                item.setRegistryName(id)
                GameData.register_impl(item)
                OpenComputers.proxy.registerModel(item, id)
            } else {
                OpenComputers.log.warn("Instance $instance is not SimpleBlock")
            }
            OpenComputers.log.info("Registering block $id")
            descriptors[id] = object : ItemInfo {
                override fun name(): String = id

                override fun block(): Block = instance

                override fun item(): Item? = null

                override fun createItemStack(size: Int): ItemStack {
                    return if (instance is SimpleBlock) {
                        instance.createItemStack(size)
                    } else {
                        ItemStack(instance, size)
                    }
                }
            }
            names[instance] = id
        }
        return instance
    }

    @JvmStatic
    fun <T : Delegate> registerItem(delegate: T, id: String): T {
        if (id !in descriptors) {
            OpenComputers.proxy.registerModel(delegate, id)
            OpenComputers.log.info("Registering item $id")
            descriptors[id] = object : ItemInfo {
                override fun name(): String = id

                override fun block(): Block? = null

                override fun item(): Delegator = delegate.parent

                override fun createItemStack(size: Int): ItemStack = delegate.createItemStack(size)
            }
            names[delegate] = id
        }
        return delegate
    }

    @JvmStatic
    fun registerItem(instance: Item, id: String): Item {
        if (!descriptors.containsKey(id)) {
            if (instance is SimpleItem) {
                instance.setTranslationKey("oc.$id")
                GameData.register_impl(instance.setRegistryName(ResourceLocation(Settings.resourceDomain, id)))
                OpenComputers.proxy.registerModel(instance, id)
            }
            OpenComputers.log.info("Registering item $id")
            descriptors[id] = object : ItemInfo {
                override fun name(): String = id

                override fun block(): Block? = null

                override fun item(): Item = instance

                override fun createItemStack(size: Int): ItemStack {
                    return if (instance is SimpleItem) {
                        instance.createItemStack(size)
                    } else {
                        ItemStack(instance, size)
                    }
                }
            }
            names[instance] = id
        }
        return instance
    }

    @JvmStatic
    fun registerStack(stack: ItemStack, id: String): ItemStack {
        val immutableStack = stack.copy()
        OpenComputers.log.info("Registering stack $id")
        descriptors[id] = object : ItemInfo {
            override fun name(): String = id

            override fun block(): Block? = null

            override fun createItemStack(size: Int): ItemStack {
                val copy = immutableStack.copy()
                copy.count = size
                return copy
            }

            override fun item(): Item = immutableStack.item
        }
        return stack
    }

    private fun getBlockOrItem(stack: ItemStack): Any? {
        return if (stack.isEmpty) {
            null
        } else {
            Delegator.subItem(stack) ?: when (val item = stack.item) {
                is ItemBlock -> item.block
                else -> item
            }
        }
    }

    // ----------------------------------------------------------------------- //

    @JvmField
    val registeredItems: MutableList<ItemStack> = mutableListOf()

    override fun registerFloppy(name: String, color: EnumDyeColor, factory: Callable<FileSystem>, doRecipeCycling: Boolean): ItemStack {
        val stack = Loot.registerLootDisk(name, color, factory, doRecipeCycling)

        registeredItems.add(stack)

        return stack.copy()
    }

    override fun registerEEPROM(name: String?, code: ByteArray?, data: ByteArray?, readonly: Boolean): ItemStack {
        val nbt = NBTTagCompound()
        if (name != null) {
            nbt.setString(Settings.namespace + "label", name.trim().take(24))
        }
        if (code != null) {
            nbt.setByteArray(Settings.namespace + "eeprom", code.take(Settings.get.eepromSize).toByteArray())
        }
        if (data != null) {
            nbt.setByteArray(Settings.namespace + "userdata", data.take(Settings.get.eepromDataSize).toByteArray())
        }
        nbt.setBoolean(Settings.namespace + "readonly", readonly)

        val stackNbt = NBTTagCompound()
        stackNbt.setTag(Settings.namespace + "data", nbt)

        val stack = get(Constants.ItemName.EEPROM)!!.createItemStack(1)
        stack.tagCompound = stackNbt

        registeredItems.add(stack)

        return stack.copy()
    }

    // ----------------------------------------------------------------------- //

    private fun safeGetStack(name: String): ItemStack = get(name)?.createItemStack(1) ?: ItemStack.EMPTY

    @JvmStatic
    fun createConfiguredDrone(): ItemStack {
        val data = DroneData()

        data.name = "Crecopter"
        data.tier = Tier.Four
        data.storedEnergy = Settings.get.bufferDrone.toInt()
        data.components = arrayOf(
            safeGetStack(Constants.ItemName.InventoryUpgrade),
            safeGetStack(Constants.ItemName.InventoryUpgrade),
            safeGetStack(Constants.ItemName.InventoryControllerUpgrade),
            safeGetStack(Constants.ItemName.TankUpgrade),
            safeGetStack(Constants.ItemName.TankControllerUpgrade),
            safeGetStack(Constants.ItemName.LeashUpgrade),
            safeGetStack(Constants.ItemName.AngelUpgrade),

            safeGetStack(Constants.ItemName.WirelessNetworkCardTier2),

            LuaStateFactory.setDefaultArch(safeGetStack(Constants.ItemName.CPUTier3)),
            safeGetStack(Constants.ItemName.RAMTier6),
            safeGetStack(Constants.ItemName.RAMTier6)
        ).filter { !it.isEmpty }.toTypedArray()

        return data.createItemStack()
    }

    @JvmStatic
    fun createConfiguredMicrocontroller(): ItemStack {
        val data = MicrocontrollerData()

        data.tier = Tier.Four
        data.storedEnergy = Settings.get.bufferMicrocontroller.toInt()
        data.components = arrayOf(
            safeGetStack(Constants.ItemName.SignUpgrade),
            safeGetStack(Constants.ItemName.PistonUpgrade),

            safeGetStack(Constants.ItemName.RedstoneCardTier2),
            safeGetStack(Constants.ItemName.WirelessNetworkCardTier2),

            LuaStateFactory.setDefaultArch(safeGetStack(Constants.ItemName.CPUTier3)),
            safeGetStack(Constants.ItemName.RAMTier6),
            safeGetStack(Constants.ItemName.RAMTier6)
        ).filter { !it.isEmpty }.toTypedArray()

        return data.createItemStack()
    }

    @JvmStatic
    fun createConfiguredRobot(): ItemStack {
        val data = RobotData()

        data.name = "Creatix"
        data.tier = Tier.Four
        data.robotEnergy = Settings.get.bufferRobot.toInt()
        data.totalEnergy = data.robotEnergy
        data.components = arrayOf(
            safeGetStack(Constants.BlockName.ScreenTier1),
            safeGetStack(Constants.BlockName.Keyboard),
            safeGetStack(Constants.BlockName.Geolyzer),
            safeGetStack(Constants.ItemName.InventoryUpgrade),
            safeGetStack(Constants.ItemName.InventoryUpgrade),
            safeGetStack(Constants.ItemName.InventoryUpgrade),
            safeGetStack(Constants.ItemName.InventoryUpgrade),
            safeGetStack(Constants.ItemName.InventoryControllerUpgrade),
            safeGetStack(Constants.ItemName.TankUpgrade),
            safeGetStack(Constants.ItemName.TankControllerUpgrade),
            safeGetStack(Constants.ItemName.CraftingUpgrade),
            safeGetStack(Constants.ItemName.HoverUpgradeTier2),
            safeGetStack(Constants.ItemName.AngelUpgrade),
            safeGetStack(Constants.ItemName.TradingUpgrade),
            safeGetStack(Constants.ItemName.ExperienceUpgrade),

            safeGetStack(Constants.ItemName.GraphicsCardTier3),
            safeGetStack(Constants.ItemName.RedstoneCardTier2),
            safeGetStack(Constants.ItemName.WirelessNetworkCardTier2),
            safeGetStack(Constants.ItemName.InternetCard),

            LuaStateFactory.setDefaultArch(safeGetStack(Constants.ItemName.CPUTier3)),
            safeGetStack(Constants.ItemName.RAMTier6),
            safeGetStack(Constants.ItemName.RAMTier6),

            safeGetStack(Constants.ItemName.LuaBios),
            safeGetStack(Constants.ItemName.OpenOS),
            safeGetStack(Constants.ItemName.HDDTier3)
        ).filter { !it.isEmpty }.toTypedArray()
        data.containers = arrayOf(
            safeGetStack(Constants.ItemName.CardContainerTier3),
            safeGetStack(Constants.ItemName.UpgradeContainerTier3),
            safeGetStack(Constants.BlockName.DiskDrive)
        ).filter { !it.isEmpty }.toTypedArray()

        return data.createItemStack()
    }

    @JvmStatic
    fun createConfiguredTablet(): ItemStack {
        val data = TabletData()

        data.tier = Tier.Four
        data.energy = Settings.get.bufferTablet
        data.maxEnergy = data.energy
        data.items = arrayOf(
            safeGetStack(Constants.BlockName.ScreenTier1),
            safeGetStack(Constants.BlockName.Keyboard),

            safeGetStack(Constants.ItemName.SignUpgrade),
            safeGetStack(Constants.ItemName.PistonUpgrade),
            safeGetStack(Constants.BlockName.Geolyzer),
            safeGetStack(Constants.ItemName.NavigationUpgrade),
            safeGetStack(Constants.ItemName.Analyzer),

            safeGetStack(Constants.ItemName.GraphicsCardTier2),
            safeGetStack(Constants.ItemName.RedstoneCardTier2),
            safeGetStack(Constants.ItemName.WirelessNetworkCardTier2),

            LuaStateFactory.setDefaultArch(safeGetStack(Constants.ItemName.CPUTier3)),
            safeGetStack(Constants.ItemName.RAMTier6),
            safeGetStack(Constants.ItemName.RAMTier6),

            safeGetStack(Constants.ItemName.LuaBios),
            safeGetStack(Constants.ItemName.HDDTier3)
        ).let { arr ->
            val padded = arr.toMutableList()
            while (padded.size < 32) {
                padded.add(ItemStack.EMPTY)
            }
            padded.toTypedArray()
        }
        data.items[31] = safeGetStack(Constants.ItemName.OpenOS)
        data.container = safeGetStack(Constants.BlockName.DiskDrive)

        return data.createItemStack()
    }

    @JvmStatic
    fun createChargedHoverBoots(): ItemStack {
        val data = HoverBootsData()
        data.charge = Settings.get.bufferHoverBoots

        return data.createItemStack()
    }

    // ----------------------------------------------------------------------- //

    @JvmStatic
    fun init() {
        initMaterials()
        initTools()
        initComponents()
        initCards()
        initUpgrades()
        initStorage()
        initSpecial()

        // Register aliases.
        for ((k, v) in aliases) {
//            descriptors.getOrPut(k) { descriptors[v]!! }
            val v = descriptors[v] ?: continue
            descriptors.getOrPut(k) { v }
        }
    }

    // Crafting materials.
    private fun initMaterials() {
        val materials = newItem(Delegator(), "material")

        Recipes.addSubItem(CuttingWire(materials), Constants.ItemName.CuttingWire, "oc:materialCuttingWire")
        Recipes.addSubItem(Acid(materials), Constants.ItemName.Acid, "oc:materialAcid")
        Recipes.addSubItem(RawCircuitBoard(materials), Constants.ItemName.RawCircuitBoard, "oc:materialCircuitBoardRaw")
        Recipes.addSubItem(CircuitBoard(materials), Constants.ItemName.CircuitBoard, "oc:materialCircuitBoard")
        Recipes.addSubItem(PrintedCircuitBoard(materials), Constants.ItemName.PrintedCircuitBoard, "oc:materialCircuitBoardPrinted")
        Recipes.addSubItem(CardBase(materials), Constants.ItemName.Card, "oc:materialCard")
        Recipes.addSubItem(Transistor(materials), Constants.ItemName.Transistor, "oc:materialTransistor")
        Recipes.addSubItem(Microchip(materials, Tier.One), Constants.ItemName.ChipTier1, "oc:circuitChip1")
        Recipes.addSubItem(Microchip(materials, Tier.Two), Constants.ItemName.ChipTier2, "oc:circuitChip2")
        Recipes.addSubItem(Microchip(materials, Tier.Three), Constants.ItemName.ChipTier3, "oc:circuitChip3")
        Recipes.addSubItem(ALU(materials), Constants.ItemName.Alu, "oc:materialALU")
        Recipes.addSubItem(ControlUnit(materials), Constants.ItemName.ControlUnit, "oc:materialCU")
        Recipes.addSubItem(Disk(materials), Constants.ItemName.Disk, "oc:materialDisk")
        Recipes.addSubItem(Interweb(materials), Constants.ItemName.Interweb, "oc:materialInterweb")
        Recipes.addSubItem(ButtonGroup(materials), Constants.ItemName.ButtonGroup, "oc:materialButtonGroup")
        Recipes.addSubItem(ArrowKeys(materials), Constants.ItemName.ArrowKeys, "oc:materialArrowKey")
        Recipes.addSubItem(NumPad(materials), Constants.ItemName.NumPad, "oc:materialNumPad")

        Recipes.addSubItem(TabletCase(materials, Tier.One), Constants.ItemName.TabletCaseTier1, "oc:tabletCase1")
        Recipes.addSubItem(TabletCase(materials, Tier.Two), Constants.ItemName.TabletCaseTier2, "oc:tabletCase2")
        registerItem(TabletCase(materials, Tier.Four), Constants.ItemName.TabletCaseCreative)
        Recipes.addSubItem(MicrocontrollerCase(materials, Tier.One), Constants.ItemName.MicrocontrollerCaseTier1, "oc:microcontrollerCase1")
        Recipes.addSubItem(MicrocontrollerCase(materials, Tier.Two), Constants.ItemName.MicrocontrollerCaseTier2, "oc:microcontrollerCase2")
        registerItem(MicrocontrollerCase(materials, Tier.Four), Constants.ItemName.MicrocontrollerCaseCreative)
        Recipes.addSubItem(DroneCase(materials, Tier.One), Constants.ItemName.DroneCaseTier1, "oc:droneCase1")
        Recipes.addSubItem(DroneCase(materials, Tier.Two), Constants.ItemName.DroneCaseTier2, "oc:droneCase2")
        registerItem(DroneCase(materials, Tier.Four), Constants.ItemName.DroneCaseCreative)

        Recipes.addSubItem(InkCartridgeEmpty(materials), Constants.ItemName.InkCartridgeEmpty, "oc:inkCartridgeEmpty")
        Recipes.addSubItem(InkCartridge(materials), Constants.ItemName.InkCartridge, "oc:inkCartridge")
        Recipes.addSubItem(Chamelium(materials), Constants.ItemName.Chamelium, "oc:chamelium")

        registerItem(DiamondChip(materials), Constants.ItemName.DiamondChip)
    }

    // All kinds of tools.
    private fun initTools() {
        val tools = newItem(Delegator(), "tool")

        Recipes.addSubItem(ItemAnalyzer(tools), Constants.ItemName.Analyzer, "oc:analyzer")
        registerItem(Debugger(tools), Constants.ItemName.Debugger)
        Recipes.addSubItem(Terminal(tools), Constants.ItemName.Terminal, "oc:terminal")
        Recipes.addSubItem(TexturePicker(tools), Constants.ItemName.TexturePicker, "oc:texturePicker")
        Recipes.addSubItem(Manual(tools), Constants.ItemName.Manual, "oc:manual")
        Recipes.addItem(Wrench(), Constants.ItemName.Wrench, "oc:wrench")

        // 1.5.11
        Recipes.addItem(HoverBoots(), Constants.ItemName.HoverBoots, "oc:hoverBoots")

        // 1.5.18
        Recipes.addSubItem(Nanomachines(tools), Constants.ItemName.Nanomachines, "oc:nanomachines")
    }

    // General purpose components.
    private fun initComponents() {
        val components = newItem(Delegator(), "component")

        Recipes.addSubItem(CPU(components, Tier.One), Constants.ItemName.CPUTier1, "oc:cpu1")
        Recipes.addSubItem(CPU(components, Tier.Two), Constants.ItemName.CPUTier2, "oc:cpu2")
        Recipes.addSubItem(CPU(components, Tier.Three), Constants.ItemName.CPUTier3, "oc:cpu3")

        Recipes.addSubItem(ComponentBus(components, Tier.One), Constants.ItemName.ComponentBusTier1, "oc:componentBus1")
        Recipes.addSubItem(ComponentBus(components, Tier.Two), Constants.ItemName.ComponentBusTier2, "oc:componentBus2")
        Recipes.addSubItem(ComponentBus(components, Tier.Three), Constants.ItemName.ComponentBusTier3, "oc:componentBus3")

        Recipes.addSubItem(Memory(components, Tier.One), Constants.ItemName.RAMTier1, "oc:ram1")
        Recipes.addSubItem(Memory(components, Tier.Two), Constants.ItemName.RAMTier2, "oc:ram2")
        Recipes.addSubItem(Memory(components, Tier.Three), Constants.ItemName.RAMTier3, "oc:ram3")
        Recipes.addSubItem(Memory(components, Tier.Four), Constants.ItemName.RAMTier4, "oc:ram4")
        Recipes.addSubItem(Memory(components, Tier.Five), Constants.ItemName.RAMTier5, "oc:ram5")
        Recipes.addSubItem(Memory(components, Tier.Six), Constants.ItemName.RAMTier6, "oc:ram6")

        registerItem(Server(components, Tier.Four), Constants.ItemName.ServerCreative)
        Recipes.addSubItem(Server(components, Tier.One), Constants.ItemName.ServerTier1, "oc:server1")
        Recipes.addSubItem(Server(components, Tier.Two), Constants.ItemName.ServerTier2, "oc:server2")
        Recipes.addSubItem(Server(components, Tier.Three), Constants.ItemName.ServerTier3, "oc:server3")

        // 1.5.10
        Recipes.addSubItem(APU(components, Tier.One), Constants.ItemName.APUTier1, "oc:apu1")
        Recipes.addSubItem(APU(components, Tier.Two), Constants.ItemName.APUTier2, "oc:apu2")

        // 1.5.12
        registerItem(APU(components, Tier.Three), Constants.ItemName.APUCreative)

        // 1.6
        Recipes.addSubItem(TerminalServer(components), Constants.ItemName.TerminalServer, "oc:terminalServer")
        Recipes.addSubItem(DiskDriveMountable(components), Constants.ItemName.DiskDriveMountable, "oc:diskDriveMountable")
    }

    // Card components.
    private fun initCards() {
        val cards = newItem(Delegator(), "card")

        registerItem(DebugCard(cards), Constants.ItemName.DebugCard)
        Recipes.addSubItem(GraphicsCard(cards, Tier.One), Constants.ItemName.GraphicsCardTier1, "oc:graphicsCard1")
        Recipes.addSubItem(GraphicsCard(cards, Tier.Two), Constants.ItemName.GraphicsCardTier2, "oc:graphicsCard2")
        Recipes.addSubItem(GraphicsCard(cards, Tier.Three), Constants.ItemName.GraphicsCardTier3, "oc:graphicsCard3")
        Recipes.addSubItem(RedstoneCard(cards, Tier.One), Constants.ItemName.RedstoneCardTier1, "oc:redstoneCard1")
        Recipes.addSubItem(RedstoneCard(cards, Tier.Two), Constants.ItemName.RedstoneCardTier2, "oc:redstoneCard2")
        Recipes.addSubItem(NetworkCard(cards), Constants.ItemName.NetworkCard, "oc:lanCard")
        Recipes.addSubItem(WirelessNetworkCard(cards, Tier.Two), Constants.ItemName.WirelessNetworkCardTier2, "oc:wlanCard2")
        Recipes.addSubItem(InternetCard(cards), Constants.ItemName.InternetCard, "oc:internetCard")
        Recipes.addSubItem(LinkedCard(cards), Constants.ItemName.LinkedCard, "oc:linkedCard")

        // 1.5.13
        Recipes.addSubItem(DataCard(cards, Tier.One), Constants.ItemName.DataCardTier1, "oc:dataCard1")

        // 1.5.15
        Recipes.addSubItem(DataCard(cards, Tier.Two), Constants.ItemName.DataCardTier2, "oc:dataCard2")
        Recipes.addSubItem(DataCard(cards, Tier.Three), Constants.ItemName.DataCardTier3, "oc:dataCard3")
    }

    // Upgrade components.
    private fun initUpgrades() {
        val upgrades = newItem(Delegator(), "upgrade")

        Recipes.addSubItem(UpgradeAngel(upgrades), Constants.ItemName.AngelUpgrade, "oc:angelUpgrade")
        Recipes.addSubItem(UpgradeBattery(upgrades, Tier.One), Constants.ItemName.BatteryUpgradeTier1, "oc:batteryUpgrade1")
        Recipes.addSubItem(UpgradeBattery(upgrades, Tier.Two), Constants.ItemName.BatteryUpgradeTier2, "oc:batteryUpgrade2")
        Recipes.addSubItem(UpgradeBattery(upgrades, Tier.Three), Constants.ItemName.BatteryUpgradeTier3, "oc:batteryUpgrade3")
        Recipes.addSubItem(UpgradeChunkloader(upgrades), Constants.ItemName.ChunkloaderUpgrade, "oc:chunkloaderUpgrade")
        Recipes.addSubItem(UpgradeContainerCard(upgrades, Tier.One), Constants.ItemName.CardContainerTier1, "oc:cardContainer1")
        Recipes.addSubItem(UpgradeContainerCard(upgrades, Tier.Two), Constants.ItemName.CardContainerTier2, "oc:cardContainer2")
        Recipes.addSubItem(UpgradeContainerCard(upgrades, Tier.Three), Constants.ItemName.CardContainerTier3, "oc:cardContainer3")
        Recipes.addSubItem(UpgradeContainerUpgrade(upgrades, Tier.One), Constants.ItemName.UpgradeContainerTier1, "oc:upgradeContainer1")
        Recipes.addSubItem(UpgradeContainerUpgrade(upgrades, Tier.Two), Constants.ItemName.UpgradeContainerTier2, "oc:upgradeContainer2")
        Recipes.addSubItem(UpgradeContainerUpgrade(upgrades, Tier.Three), Constants.ItemName.UpgradeContainerTier3, "oc:upgradeContainer3")
        Recipes.addSubItem(UpgradeCrafting(upgrades), Constants.ItemName.CraftingUpgrade, "oc:craftingUpgrade")
        Recipes.addSubItem(UpgradeDatabase(upgrades, Tier.One), Constants.ItemName.DatabaseUpgradeTier1, "oc:databaseUpgrade1")
        Recipes.addSubItem(UpgradeDatabase(upgrades, Tier.Two), Constants.ItemName.DatabaseUpgradeTier2, "oc:databaseUpgrade2")
        Recipes.addSubItem(UpgradeDatabase(upgrades, Tier.Three), Constants.ItemName.DatabaseUpgradeTier3, "oc:databaseUpgrade3")
        Recipes.addSubItem(UpgradeExperience(upgrades), Constants.ItemName.ExperienceUpgrade, "oc:experienceUpgrade")
        Recipes.addSubItem(UpgradeGenerator(upgrades), Constants.ItemName.GeneratorUpgrade, "oc:generatorUpgrade")
        Recipes.addSubItem(UpgradeInventory(upgrades), Constants.ItemName.InventoryUpgrade, "oc:inventoryUpgrade")
        Recipes.addSubItem(UpgradeInventoryController(upgrades), Constants.ItemName.InventoryControllerUpgrade, "oc:inventoryControllerUpgrade")
        Recipes.addSubItem(UpgradeNavigation(upgrades), Constants.ItemName.NavigationUpgrade, "oc:navigationUpgrade")
        Recipes.addSubItem(UpgradePiston(upgrades), Constants.ItemName.PistonUpgrade, "oc:pistonUpgrade")
        Recipes.addSubItem(UpgradeSign(upgrades), Constants.ItemName.SignUpgrade, "oc:signUpgrade")
        Recipes.addSubItem(UpgradeSolarGenerator(upgrades), Constants.ItemName.SolarGeneratorUpgrade, "oc:solarGeneratorUpgrade")
        Recipes.addSubItem(UpgradeTank(upgrades), Constants.ItemName.TankUpgrade, "oc:tankUpgrade")
        Recipes.addSubItem(UpgradeTankController(upgrades), Constants.ItemName.TankControllerUpgrade, "oc:tankControllerUpgrade")
        Recipes.addSubItem(UpgradeTractorBeam(upgrades), Constants.ItemName.TractorBeamUpgrade, "oc:tractorBeamUpgrade")
        Recipes.addSubItem(UpgradeLeash(upgrades), Constants.ItemName.LeashUpgrade, "oc:leashUpgrade")

        // 1.5.8
        Recipes.addSubItem(UpgradeHover(upgrades, Tier.One), Constants.ItemName.HoverUpgradeTier1, "oc:hoverUpgrade1")
        Recipes.addSubItem(UpgradeHover(upgrades, Tier.Two), Constants.ItemName.HoverUpgradeTier2, "oc:hoverUpgrade2")

        // 1.6
        Recipes.addSubItem(UpgradeTrading(upgrades), Constants.ItemName.TradingUpgrade, "oc:tradingUpgrade")
        Recipes.addSubItem(UpgradeMF(upgrades), Constants.ItemName.MFU, "oc:mfu")

        // 1.7.2
        Recipes.addSubItem(WirelessNetworkCard(upgrades, Tier.One), Constants.ItemName.WirelessNetworkCardTier1, "oc:wlanCard1")
        registerItem(ComponentBus(upgrades, Tier.Four), Constants.ItemName.ComponentBusCreative)

        // 1.8
        Recipes.addSubItem(UpgradeStickyPiston(upgrades), Constants.ItemName.StickyPistonUpgrade, "oc:stickyPistonUpgrade")
    }

    // Storage media of all kinds.
    private fun initStorage() {
        val storage = newItem(Delegator(), "storage")

        Recipes.addSubItem(EEPROM(storage), Constants.ItemName.EEPROM, "oc:eeprom")
        Recipes.addSubItem(FloppyDisk(storage), Constants.ItemName.Floppy, "oc:floppy")
        Recipes.addSubItem(HardDiskDrive(storage, Tier.One), Constants.ItemName.HDDTier1, "oc:hdd1")
        Recipes.addSubItem(HardDiskDrive(storage, Tier.Two), Constants.ItemName.HDDTier2, "oc:hdd2")
        Recipes.addSubItem(HardDiskDrive(storage, Tier.Three), Constants.ItemName.HDDTier3, "oc:hdd3")

        val luaBios = run {
            val code = ByteArray(4 * 1024)
            val count = OpenComputers::class.java.getResourceAsStream(Settings.scriptPath + "bios.lua").read(code)
            registerEEPROM("EEPROM (Lua BIOS)", code.take(count).toByteArray(), null, false)
        }
        Recipes.addStack(luaBios, Constants.ItemName.LuaBios)
    }

    // Special purpose items that don't fit into any other category.
    private fun initSpecial() {
        val misc = newItem(object : Delegator() {
            private val configuredItems: Array<ItemStack> by lazy {
                (arrayOf(
                    Items.createConfiguredDrone(),
                    Items.createConfiguredMicrocontroller(),
                    Items.createConfiguredRobot(),
                    Items.createConfiguredTablet(),
//                    Items.createChargedHoverBoots() // TODO this is bad
                )
                + Loot.disksForClient
                + registeredItems
                )
            }

            override fun getSubItems(tab: CreativeTabs, list: NonNullList<ItemStack>) {
                super.getSubItems(tab, list)
                if (isInCreativeTab(tab)) {
                    for ((idx, configured) in configuredItems.withIndex())
                        if (configured.item.registryName == null) throw IllegalArgumentException("bad item $idx: $configured")
                    list.addAll(configuredItems)
                }
            }
        }, "misc")

        registerItem(Tablet(misc), Constants.ItemName.Tablet)
        registerItem(Drone(misc), Constants.ItemName.Drone)
        registerItem(Present(misc), Constants.ItemName.Present)
    }

    private fun <T : Item> newItem(item: T, name: String): T {
        item.setTranslationKey("oc.$name")
        GameData.register_impl(item.setRegistryName(ResourceLocation(Settings.resourceDomain, name)))
        return item
    }
}
