@file:Suppress("ConstPropertyName")

package li.cil.oc

import li.cil.oc.api.Items
import li.cil.oc.api.detail.ItemInfo as ItemInfoT
import li.cil.oc.util.ItemUtils
import net.minecraft.item.Item

object Constants {

    object BlockName {
        const val Adapter = "adapter"
        const val Assembler = "assembler"
        const val Cable = "cable"
        const val Capacitor = "capacitor"
        const val CarpetedCapacitor = "carpetedcapacitor"
        const val CaseCreative = "casecreative"
        const val CaseTier1 = "case1"
        const val CaseTier2 = "case2"
        const val CaseTier3 = "case3"
        const val ChameliumBlock = "chameliumblock"
        const val Charger = "charger"
        const val Disassembler = "disassembler"
        const val DiskDrive = "diskdrive"
        const val Endstone = "endstone"
        const val Geolyzer = "geolyzer"
        const val HologramTier1 = "hologram1"
        const val HologramTier2 = "hologram2"
        const val Keyboard = "keyboard"
        const val Microcontroller = "microcontroller"
        const val MotionSensor = "motionsensor"
        const val NetSplitter = "netsplitter"
        const val PowerConverter = "powerconverter"
        const val PowerDistributor = "powerdistributor"
        const val Print = "print"
        const val Printer = "printer"
        const val Raid = "raid"
        const val Redstone = "redstone"
        const val Relay = "relay"
        const val Robot = "robot"
        const val RobotAfterimage = "robotafterimage"
        const val ScreenTier1 = "screen1"
        const val ScreenTier2 = "screen2"
        const val ScreenTier3 = "screen3"
        const val Rack = "rack"
        const val Transposer = "transposer"
        const val Waypoint = "waypoint"

        @JvmStatic
        @Suppress("FunctionName")
        fun Case(tier: Int): String = ItemUtils.caseNameWithTierSuffix("case", tier)
    }
    object BlockInfo {
        internal val Adapter: ItemInfoT by lazy { itemInfo(Constants.BlockName.Adapter) }
        internal val Assembler: ItemInfoT by lazy { itemInfo(Constants.BlockName.Assembler) }
        internal val Cable: ItemInfoT by lazy { itemInfo(Constants.BlockName.Cable) }
        internal val Capacitor: ItemInfoT by lazy { itemInfo(Constants.BlockName.Capacitor) }
        internal val CarpetedCapacitor: ItemInfoT by lazy { itemInfo(Constants.BlockName.CarpetedCapacitor) }
        internal val CaseCreative: ItemInfoT by lazy { itemInfo(Constants.BlockName.CaseCreative) }
        internal val CaseTier1: ItemInfoT by lazy { itemInfo(Constants.BlockName.CaseTier1) }
        internal val CaseTier2: ItemInfoT by lazy { itemInfo(Constants.BlockName.CaseTier2) }
        internal val CaseTier3: ItemInfoT by lazy { itemInfo(Constants.BlockName.CaseTier3) }
        internal val ChameliumBlock: ItemInfoT by lazy { itemInfo(Constants.BlockName.ChameliumBlock) }
        internal val Charger: ItemInfoT by lazy { itemInfo(Constants.BlockName.Charger) }
        internal val Disassembler: ItemInfoT by lazy { itemInfo(Constants.BlockName.Disassembler) }
        internal val DiskDrive: ItemInfoT by lazy { itemInfo(Constants.BlockName.DiskDrive) }
        internal val Endstone: ItemInfoT by lazy { itemInfo(Constants.BlockName.Endstone) }
        internal val Geolyzer: ItemInfoT by lazy { itemInfo(Constants.BlockName.Geolyzer) }
        internal val HologramTier1: ItemInfoT by lazy { itemInfo(Constants.BlockName.HologramTier1) }
        internal val HologramTier2: ItemInfoT by lazy { itemInfo(Constants.BlockName.HologramTier2) }
        internal val Keyboard: ItemInfoT by lazy { itemInfo(Constants.BlockName.Keyboard) }
        internal val Microcontroller: ItemInfoT by lazy { itemInfo(Constants.BlockName.Microcontroller) }
        internal val MotionSensor: ItemInfoT by lazy { itemInfo(Constants.BlockName.MotionSensor) }
        internal val NetSplitter: ItemInfoT by lazy { itemInfo(Constants.BlockName.NetSplitter) }
        internal val PowerConverter: ItemInfoT by lazy { itemInfo(Constants.BlockName.PowerConverter) }
        internal val PowerDistributor: ItemInfoT by lazy { itemInfo(Constants.BlockName.PowerDistributor) }
        internal val Print: ItemInfoT by lazy { itemInfo(Constants.BlockName.Print) }
        internal val Printer: ItemInfoT by lazy { itemInfo(Constants.BlockName.Printer) }
        internal val Raid: ItemInfoT by lazy { itemInfo(Constants.BlockName.Raid) }
        internal val Redstone: ItemInfoT by lazy { itemInfo(Constants.BlockName.Redstone) }
        internal val Relay: ItemInfoT by lazy { itemInfo(Constants.BlockName.Relay) }
        internal val Robot: ItemInfoT by lazy { itemInfo(Constants.BlockName.Robot) }
        internal val RobotAfterimage: ItemInfoT by lazy { itemInfo(Constants.BlockName.RobotAfterimage) }
        internal val ScreenTier1: ItemInfoT by lazy { itemInfo(Constants.BlockName.ScreenTier1) }
        internal val ScreenTier2: ItemInfoT by lazy { itemInfo(Constants.BlockName.ScreenTier2) }
        internal val ScreenTier3: ItemInfoT by lazy { itemInfo(Constants.BlockName.ScreenTier3) }
        internal val Rack: ItemInfoT by lazy { itemInfo(Constants.BlockName.Rack) }
        internal val Transposer: ItemInfoT by lazy { itemInfo(Constants.BlockName.Transposer) }
        internal val Waypoint: ItemInfoT by lazy { itemInfo(Constants.BlockName.Waypoint) }
        @JvmStatic
        @Suppress("FunctionName")
        fun Case(tier: Int): ItemInfoT = itemInfo(Constants.BlockName.Case(tier))
    }

    object ItemName {
        const val AbstractBusCard = "abstractbuscard"
        const val Acid = "acid"
        const val Alu = "alu"
        const val Analyzer = "analyzer"
        const val AngelUpgrade = "angelupgrade"
        const val APUCreative = "apucreative"
        const val APUTier1 = "apu1"
        const val APUTier2 = "apu2"
        const val ArrowKeys = "arrowkeys"
        const val BatteryUpgradeTier1 = "batteryupgrade1"
        const val BatteryUpgradeTier2 = "batteryupgrade2"
        const val BatteryUpgradeTier3 = "batteryupgrade3"
        const val ButtonGroup = "buttongroup"
        const val Card = "card"
        const val CardContainerTier1 = "cardcontainer1"
        const val CardContainerTier2 = "cardcontainer2"
        const val CardContainerTier3 = "cardcontainer3"
        const val Chamelium = "chamelium"
        const val ChipTier1 = "chip1"
        const val ChipTier2 = "chip2"
        const val ChipTier3 = "chip3"
        const val ChunkloaderUpgrade = "chunkloaderupgrade"
        const val CircuitBoard = "circuitboard"
        const val ComponentBusTier1 = "componentbus1"
        const val ComponentBusTier2 = "componentbus2"
        const val ComponentBusTier3 = "componentbus3"
        const val ComponentBusCreative = "componentbuscreative"
        const val CPUTier1 = "cpu1"
        const val CPUTier2 = "cpu2"
        const val CPUTier3 = "cpu3"
        const val CraftingUpgrade = "craftingupgrade"
        const val ControlUnit = "cu"
        const val CuttingWire = "cuttingwire"
        const val DatabaseUpgradeTier1 = "databaseupgrade1"
        const val DatabaseUpgradeTier2 = "databaseupgrade2"
        const val DatabaseUpgradeTier3 = "databaseupgrade3"
        const val DataCardTier1 = "datacard1"
        const val DataCardTier2 = "datacard2"
        const val DataCardTier3 = "datacard3"
        const val DebugCard = "debugcard"
        const val Debugger = "debugger"
        const val DiamondChip = "chipdiamond"
        const val Disk = "disk"
        const val DiskDriveMountable = "diskdrivemountable"
        const val Drone = "drone"
        const val DroneCaseCreative = "dronecasecreative"
        const val DroneCaseTier1 = "dronecase1"
        const val DroneCaseTier2 = "dronecase2"
        const val EEPROM = "eeprom"
        const val ExperienceUpgrade = "experienceupgrade"
        const val Floppy = "floppy"
        const val GeneratorUpgrade = "generatorupgrade"
        const val GraphicsCardTier1 = "graphicscard1"
        const val GraphicsCardTier2 = "graphicscard2"
        const val GraphicsCardTier3 = "graphicscard3"
        const val HDDTier1 = "hdd1"
        const val HDDTier2 = "hdd2"
        const val HDDTier3 = "hdd3"
        const val HoverBoots = "hoverboots"
        const val HoverUpgradeTier1 = "hoverupgrade1"
        const val HoverUpgradeTier2 = "hoverupgrade2"
        const val InkCartridgeEmpty = "inkcartridgeempty"
        const val InkCartridge = "inkcartridge"
        const val InternetCard = "internetcard"
        const val Interweb = "interweb"
        const val InventoryControllerUpgrade = "inventorycontrollerupgrade"
        const val InventoryUpgrade = "inventoryupgrade"
        const val LeashUpgrade = "leashupgrade"
        const val LinkedCard = "linkedcard"
        const val LuaBios = "luabios"
        const val MFU = "mfu"
        const val Manual = "manual"
        const val MicrocontrollerCaseCreative = "microcontrollercasecreative"
        const val MicrocontrollerCaseTier1 = "microcontrollercase1"
        const val MicrocontrollerCaseTier2 = "microcontrollercase2"
        const val Nanomachines = "nanomachines"
        const val NavigationUpgrade = "navigationupgrade"
        const val NetworkCard = "lancard"
        const val NumPad = "numpad"
        const val OpenOS = "openos"
        const val PistonUpgrade = "pistonupgrade"
        const val StickyPistonUpgrade = "stickypistonupgrade"
        const val Present = "present"
        const val PrintedCircuitBoard = "printedcircuitboard"
        const val RAMTier1 = "ram1"
        const val RAMTier2 = "ram2"
        const val RAMTier3 = "ram3"
        const val RAMTier4 = "ram4"
        const val RAMTier5 = "ram5"
        const val RAMTier6 = "ram6"
        const val RawCircuitBoard = "rawcircuitboard"
        const val RedstoneCardTier1 = "redstonecard1"
        const val RedstoneCardTier2 = "redstonecard2"
        const val ServerCreative = "servercreative"
        const val ServerTier1 = "server1"
        const val ServerTier2 = "server2"
        const val ServerTier3 = "server3"
        const val SignUpgrade = "signupgrade"
        const val SolarGeneratorUpgrade = "solargeneratorupgrade"
        const val Tablet = "tablet"
        const val TabletCaseCreative = "tabletcasecreative"
        const val TabletCaseTier1 = "tabletcase1"
        const val TabletCaseTier2 = "tabletcase2"
        const val TankControllerUpgrade = "tankcontrollerupgrade"
        const val TankUpgrade = "tankupgrade"
        const val Terminal = "terminal"
        const val TerminalServer = "terminalserver"
        const val TexturePicker = "texturepicker"
        const val TractorBeamUpgrade = "tractorbeamupgrade"
        const val TradingUpgrade = "tradingupgrade"
        const val Transistor = "transistor"
        const val UpgradeContainerTier1 = "upgradecontainer1"
        const val UpgradeContainerTier2 = "upgradecontainer2"
        const val UpgradeContainerTier3 = "upgradecontainer3"
        const val WirelessNetworkCardTier1 = "wlancard1"
        const val WirelessNetworkCardTier2 = "wlancard2"
        const val WorldSensorCard = "worldsensorcard"
        const val Wrench = "wrench"

        @JvmStatic
        fun DroneCase(tier: Int): String = ItemUtils.caseNameWithTierSuffix("dronecase", tier)

        @JvmStatic
        fun MicrocontrollerCase(tier: Int): String = ItemUtils.caseNameWithTierSuffix("microcontrollercase", tier)

        @JvmStatic
        fun TabletCase(tier: Int): String = ItemUtils.caseNameWithTierSuffix("tabletcase", tier)
    }

    object DeviceInfo {
        const val DefaultVendor = "MightyPirates GmbH & Co. KG"
        const val Scummtech = "Scummtech, Inc."
    }

    internal object ItemInfo {
        internal val AbstractBusCard: ItemInfoT by lazy { itemInfo(Constants.ItemName.AbstractBusCard) }
        internal val Acid: ItemInfoT by lazy { itemInfo(Constants.ItemName.Acid) }
        internal val Alu: ItemInfoT by lazy { itemInfo(Constants.ItemName.Alu) }
        internal val Analyzer: ItemInfoT by lazy { itemInfo(Constants.ItemName.Analyzer) }
        internal val AngelUpgrade: ItemInfoT by lazy { itemInfo(Constants.ItemName.AngelUpgrade) }
        internal val APUCreative: ItemInfoT by lazy { itemInfo(Constants.ItemName.APUCreative) }
        internal val APUTier1: ItemInfoT by lazy { itemInfo(Constants.ItemName.APUTier1) }
        internal val APUTier2: ItemInfoT by lazy { itemInfo(Constants.ItemName.APUTier2) }
        internal val ArrowKeys: ItemInfoT by lazy { itemInfo(Constants.ItemName.ArrowKeys) }
        internal val BatteryUpgradeTier1: ItemInfoT by lazy { itemInfo(Constants.ItemName.BatteryUpgradeTier1) }
        internal val BatteryUpgradeTier2: ItemInfoT by lazy { itemInfo(Constants.ItemName.BatteryUpgradeTier2) }
        internal val BatteryUpgradeTier3: ItemInfoT by lazy { itemInfo(Constants.ItemName.BatteryUpgradeTier3) }
        internal val ButtonGroup: ItemInfoT by lazy { itemInfo(Constants.ItemName.ButtonGroup) }
        internal val Card: ItemInfoT by lazy { itemInfo(Constants.ItemName.Card) }
        internal val CardContainerTier1: ItemInfoT by lazy { itemInfo(Constants.ItemName.CardContainerTier1) }
        internal val CardContainerTier2: ItemInfoT by lazy { itemInfo(Constants.ItemName.CardContainerTier2) }
        internal val CardContainerTier3: ItemInfoT by lazy { itemInfo(Constants.ItemName.CardContainerTier3) }
        internal val Chamelium: ItemInfoT by lazy { itemInfo(Constants.ItemName.Chamelium) }
        internal val ChipTier1: ItemInfoT by lazy { itemInfo(Constants.ItemName.ChipTier1) }
        internal val ChipTier2: ItemInfoT by lazy { itemInfo(Constants.ItemName.ChipTier2) }
        internal val ChipTier3: ItemInfoT by lazy { itemInfo(Constants.ItemName.ChipTier3) }
        internal val ChunkloaderUpgrade: ItemInfoT by lazy { itemInfo(Constants.ItemName.ChunkloaderUpgrade) }
        internal val CircuitBoard: ItemInfoT by lazy { itemInfo(Constants.ItemName.CircuitBoard) }
        internal val ComponentBusTier1: ItemInfoT by lazy { itemInfo(Constants.ItemName.ComponentBusTier1) }
        internal val ComponentBusTier2: ItemInfoT by lazy { itemInfo(Constants.ItemName.ComponentBusTier2) }
        internal val ComponentBusTier3: ItemInfoT by lazy { itemInfo(Constants.ItemName.ComponentBusTier3) }
        internal val ComponentBusCreative: ItemInfoT by lazy { itemInfo(Constants.ItemName.ComponentBusCreative) }
        internal val CPUTier1: ItemInfoT by lazy { itemInfo(Constants.ItemName.CPUTier1) }
        internal val CPUTier2: ItemInfoT by lazy { itemInfo(Constants.ItemName.CPUTier2) }
        internal val CPUTier3: ItemInfoT by lazy { itemInfo(Constants.ItemName.CPUTier3) }
        internal val CraftingUpgrade: ItemInfoT by lazy { itemInfo(Constants.ItemName.CraftingUpgrade) }
        internal val ControlUnit: ItemInfoT by lazy { itemInfo(Constants.ItemName.ControlUnit) }
        internal val CuttingWire: ItemInfoT by lazy { itemInfo(Constants.ItemName.CuttingWire) }
        internal val DatabaseUpgradeTier1: ItemInfoT by lazy { itemInfo(Constants.ItemName.DatabaseUpgradeTier1) }
        internal val DatabaseUpgradeTier2: ItemInfoT by lazy { itemInfo(Constants.ItemName.DatabaseUpgradeTier2) }
        internal val DatabaseUpgradeTier3: ItemInfoT by lazy { itemInfo(Constants.ItemName.DatabaseUpgradeTier3) }
        internal val DataCardTier1: ItemInfoT by lazy { itemInfo(Constants.ItemName.DataCardTier1) }
        internal val DataCardTier2: ItemInfoT by lazy { itemInfo(Constants.ItemName.DataCardTier2) }
        internal val DataCardTier3: ItemInfoT by lazy { itemInfo(Constants.ItemName.DataCardTier3) }
        internal val DebugCard: ItemInfoT by lazy { itemInfo(Constants.ItemName.DebugCard) }
        internal val Debugger: ItemInfoT by lazy { itemInfo(Constants.ItemName.Debugger) }
        internal val DiamondChip: ItemInfoT by lazy { itemInfo(Constants.ItemName.DiamondChip) }
        internal val Disk: ItemInfoT by lazy { itemInfo(Constants.ItemName.Disk) }
        internal val DiskDriveMountable: ItemInfoT by lazy { itemInfo(Constants.ItemName.DiskDriveMountable) }
        internal val Drone: ItemInfoT by lazy { itemInfo(Constants.ItemName.Drone) }
        internal val DroneCaseCreative: ItemInfoT by lazy { itemInfo(Constants.ItemName.DroneCaseCreative) }
        internal val DroneCaseTier1: ItemInfoT by lazy { itemInfo(Constants.ItemName.DroneCaseTier1) }
        internal val DroneCaseTier2: ItemInfoT by lazy { itemInfo(Constants.ItemName.DroneCaseTier2) }
        internal val EEPROM: ItemInfoT by lazy { itemInfo(Constants.ItemName.EEPROM) }
        internal val ExperienceUpgrade: ItemInfoT by lazy { itemInfo(Constants.ItemName.ExperienceUpgrade) }
        internal val Floppy: ItemInfoT by lazy { itemInfo(Constants.ItemName.Floppy) }
        internal val GeneratorUpgrade: ItemInfoT by lazy { itemInfo(Constants.ItemName.GeneratorUpgrade) }
        internal val GraphicsCardTier1: ItemInfoT by lazy { itemInfo(Constants.ItemName.GraphicsCardTier1) }
        internal val GraphicsCardTier2: ItemInfoT by lazy { itemInfo(Constants.ItemName.GraphicsCardTier2) }
        internal val GraphicsCardTier3: ItemInfoT by lazy { itemInfo(Constants.ItemName.GraphicsCardTier3) }
        internal val HDDTier1: ItemInfoT by lazy { itemInfo(Constants.ItemName.HDDTier1) }
        internal val HDDTier2: ItemInfoT by lazy { itemInfo(Constants.ItemName.HDDTier2) }
        internal val HDDTier3: ItemInfoT by lazy { itemInfo(Constants.ItemName.HDDTier3) }
        internal val HoverBoots: ItemInfoT by lazy { itemInfo(Constants.ItemName.HoverBoots) }
        internal val HoverUpgradeTier1: ItemInfoT by lazy { itemInfo(Constants.ItemName.HoverUpgradeTier1) }
        internal val HoverUpgradeTier2: ItemInfoT by lazy { itemInfo(Constants.ItemName.HoverUpgradeTier2) }
        internal val InkCartridgeEmpty: ItemInfoT by lazy { itemInfo(Constants.ItemName.InkCartridgeEmpty) }
        internal val InkCartridge: ItemInfoT by lazy { itemInfo(Constants.ItemName.InkCartridge) }
        internal val InternetCard: ItemInfoT by lazy { itemInfo(Constants.ItemName.InternetCard) }
        internal val Interweb: ItemInfoT by lazy { itemInfo(Constants.ItemName.Interweb) }
        internal val InventoryControllerUpgrade: ItemInfoT by lazy { itemInfo(Constants.ItemName.InventoryControllerUpgrade) }
        internal val InventoryUpgrade: ItemInfoT by lazy { itemInfo(Constants.ItemName.InventoryUpgrade) }
        internal val LeashUpgrade: ItemInfoT by lazy { itemInfo(Constants.ItemName.LeashUpgrade) }
        internal val LinkedCard: ItemInfoT by lazy { itemInfo(Constants.ItemName.LinkedCard) }
        internal val LuaBios: ItemInfoT by lazy { itemInfo(Constants.ItemName.LuaBios) }
        internal val MFU: ItemInfoT by lazy { itemInfo(Constants.ItemName.MFU) }
        internal val Manual: ItemInfoT by lazy { itemInfo(Constants.ItemName.Manual) }
        internal val MicrocontrollerCaseCreative: ItemInfoT by lazy { itemInfo(Constants.ItemName.MicrocontrollerCaseCreative) }
        internal val MicrocontrollerCaseTier1: ItemInfoT by lazy { itemInfo(Constants.ItemName.MicrocontrollerCaseTier1) }
        internal val MicrocontrollerCaseTier2: ItemInfoT by lazy { itemInfo(Constants.ItemName.MicrocontrollerCaseTier2) }
        internal val Nanomachines: ItemInfoT by lazy { itemInfo(Constants.ItemName.Nanomachines) }
        internal val NavigationUpgrade: ItemInfoT by lazy { itemInfo(Constants.ItemName.NavigationUpgrade) }
        internal val NetworkCard: ItemInfoT by lazy { itemInfo(Constants.ItemName.NetworkCard) }
        internal val NumPad: ItemInfoT by lazy { itemInfo(Constants.ItemName.NumPad) }
        internal val OpenOS: ItemInfoT by lazy { itemInfo(Constants.ItemName.OpenOS) }
        internal val PistonUpgrade: ItemInfoT by lazy { itemInfo(Constants.ItemName.PistonUpgrade) }
        internal val StickyPistonUpgrade: ItemInfoT by lazy { itemInfo(Constants.ItemName.StickyPistonUpgrade) }
        internal val Present: ItemInfoT by lazy { itemInfo(Constants.ItemName.Present) }
        internal val PrintedCircuitBoard: ItemInfoT by lazy { itemInfo(Constants.ItemName.PrintedCircuitBoard) }
        internal val RAMTier1: ItemInfoT by lazy { itemInfo(Constants.ItemName.RAMTier1) }
        internal val RAMTier2: ItemInfoT by lazy { itemInfo(Constants.ItemName.RAMTier2) }
        internal val RAMTier3: ItemInfoT by lazy { itemInfo(Constants.ItemName.RAMTier3) }
        internal val RAMTier4: ItemInfoT by lazy { itemInfo(Constants.ItemName.RAMTier4) }
        internal val RAMTier5: ItemInfoT by lazy { itemInfo(Constants.ItemName.RAMTier5) }
        internal val RAMTier6: ItemInfoT by lazy { itemInfo(Constants.ItemName.RAMTier6) }
        internal val RawCircuitBoard: ItemInfoT by lazy { itemInfo(Constants.ItemName.RawCircuitBoard) }
        internal val RedstoneCardTier1: ItemInfoT by lazy { itemInfo(Constants.ItemName.RedstoneCardTier1) }
        internal val RedstoneCardTier2: ItemInfoT by lazy { itemInfo(Constants.ItemName.RedstoneCardTier2) }
        internal val ServerCreative: ItemInfoT by lazy { itemInfo(Constants.ItemName.ServerCreative) }
        internal val ServerTier1: ItemInfoT by lazy { itemInfo(Constants.ItemName.ServerTier1) }
        internal val ServerTier2: ItemInfoT by lazy { itemInfo(Constants.ItemName.ServerTier2) }
        internal val ServerTier3: ItemInfoT by lazy { itemInfo(Constants.ItemName.ServerTier3) }
        internal val SignUpgrade: ItemInfoT by lazy { itemInfo(Constants.ItemName.SignUpgrade) }
        internal val SolarGeneratorUpgrade: ItemInfoT by lazy { itemInfo(Constants.ItemName.SolarGeneratorUpgrade) }
        internal val Tablet: ItemInfoT by lazy { itemInfo(Constants.ItemName.Tablet) }
        internal val TabletCaseCreative: ItemInfoT by lazy { itemInfo(Constants.ItemName.TabletCaseCreative) }
        internal val TabletCaseTier1: ItemInfoT by lazy { itemInfo(Constants.ItemName.TabletCaseTier1) }
        internal val TabletCaseTier2: ItemInfoT by lazy { itemInfo(Constants.ItemName.TabletCaseTier2) }
        internal val TankControllerUpgrade: ItemInfoT by lazy { itemInfo(Constants.ItemName.TankControllerUpgrade) }
        internal val TankUpgrade: ItemInfoT by lazy { itemInfo(Constants.ItemName.TankUpgrade) }
        internal val Terminal: ItemInfoT by lazy { itemInfo(Constants.ItemName.Terminal) }
        internal val TerminalServer: ItemInfoT by lazy { itemInfo(Constants.ItemName.TerminalServer) }
        internal val TexturePicker: ItemInfoT by lazy { itemInfo(Constants.ItemName.TexturePicker) }
        internal val TractorBeamUpgrade: ItemInfoT by lazy { itemInfo(Constants.ItemName.TractorBeamUpgrade) }
        internal val TradingUpgrade: ItemInfoT by lazy { itemInfo(Constants.ItemName.TradingUpgrade) }
        internal val Transistor: ItemInfoT by lazy { itemInfo(Constants.ItemName.Transistor) }
        internal val UpgradeContainerTier1: ItemInfoT by lazy { itemInfo(Constants.ItemName.UpgradeContainerTier1) }
        internal val UpgradeContainerTier2: ItemInfoT by lazy { itemInfo(Constants.ItemName.UpgradeContainerTier2) }
        internal val UpgradeContainerTier3: ItemInfoT by lazy { itemInfo(Constants.ItemName.UpgradeContainerTier3) }
        internal val WirelessNetworkCardTier1: ItemInfoT by lazy { itemInfo(Constants.ItemName.WirelessNetworkCardTier1) }
        internal val WirelessNetworkCardTier2: ItemInfoT by lazy { itemInfo(Constants.ItemName.WirelessNetworkCardTier2) }
        internal val WorldSensorCard: ItemInfoT by lazy { itemInfo(Constants.ItemName.WorldSensorCard) }
        internal val Wrench: ItemInfoT by lazy { itemInfo(Constants.ItemName.Wrench) }

        @JvmStatic
        internal fun DroneCase(tier: Int): ItemInfoT = itemInfo(ItemName.DroneCase(tier))

        @JvmStatic
        internal fun MicrocontrollerCase(tier: Int): ItemInfoT = itemInfo(ItemName.MicrocontrollerCase(tier))

        @JvmStatic
        internal fun TabletCase(tier: Int): ItemInfoT = itemInfo(ItemName.TabletCase(tier))
    }
}

/** Get item info for constant */
internal inline fun itemInfo(name: String): ItemInfoT = Items.get(name)!!