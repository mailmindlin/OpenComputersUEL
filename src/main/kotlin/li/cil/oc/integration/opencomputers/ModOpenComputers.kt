package li.cil.oc.integration.opencomputers

import li.cil.oc.integration.Mod

import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.IMC
import li.cil.oc.api.Driver
import li.cil.oc.api.Manual
import li.cil.oc.api.Nanomachines
import li.cil.oc.api.Items
import li.cil.oc.api.detail.ItemInfo
import li.cil.oc.api.driver.item.Chargeable
import li.cil.oc.api.internal.Adapter
import li.cil.oc.api.internal.Drone
import li.cil.oc.api.internal.Microcontroller
import li.cil.oc.api.internal.Robot
import li.cil.oc.api.internal.Wrench
import li.cil.oc.api.manual.PathProvider
import li.cil.oc.api.prefab.ItemStackTabIconRenderer
import li.cil.oc.api.prefab.ResourceContentProvider
import li.cil.oc.api.prefab.TextureTabIconRenderer
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.markdown.segment.render.BlockImageProvider
import li.cil.oc.client.renderer.markdown.segment.render.ItemImageProvider
import li.cil.oc.client.renderer.markdown.segment.render.OreDictImageProvider
import li.cil.oc.client.renderer.markdown.segment.render.TextureImageProvider
import li.cil.oc.common.EventHandler
import li.cil.oc.common.Loot
import li.cil.oc.common.SaveHandler
import li.cil.oc.common.asm.SimpleComponentTickHandler
import li.cil.oc.common.block.SimpleBlock
import li.cil.oc.common.event.*
import li.cil.oc.common.item.Analyzer
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.item.RedstoneCard
import li.cil.oc.common.item.Tablet
import li.cil.oc.common.nanomachines.provider.DisintegrationProvider
import li.cil.oc.common.nanomachines.provider.HungryProvider
import li.cil.oc.common.nanomachines.provider.MagnetProvider
import li.cil.oc.common.nanomachines.provider.ParticleProvider
import li.cil.oc.common.nanomachines.provider.PotionProvider
import li.cil.oc.common.template.*
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.BundledRedstone
import li.cil.oc.integration.util.ItemBlacklist
import li.cil.oc.server.network.Waypoints
import li.cil.oc.server.network.WirelessNetwork
import li.cil.oc.util.Color
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.common.ForgeChunkManager
import net.minecraftforge.common.MinecraftForge

internal object ModOpenComputers : ModProxy {
  override val mod: Mod = Mods.OpenComputers

  override fun initialize() {
    ItemBlacklist.apply()

    DroneTemplate.register()
    MicrocontrollerTemplate.register()
    NavigationUpgradeTemplate.register()
    RobotTemplate.register()
    ServerTemplate.register()
    TabletTemplate.register()
    TemplateBlacklist.register()

    IMC.registerWrenchTool("li.cil.oc.integration.opencomputers.ModOpenComputers.useWrench")
    IMC.registerWrenchToolCheck("li.cil.oc.integration.opencomputers.ModOpenComputers.isWrench")
    IMC.registerItemCharge(
      "OpenComputers",
      "li.cil.oc.integration.opencomputers.ModOpenComputers.canCharge",
      "li.cil.oc.integration.opencomputers.ModOpenComputers.charge")

    IMC.registerInkProvider("li.cil.oc.integration.opencomputers.ModOpenComputers.inkCartridgeInkProvider")
    IMC.registerInkProvider("li.cil.oc.integration.opencomputers.ModOpenComputers.dyeInkProvider")

    IMC.registerProgramDiskLabel("build", "builder", "Lua 5.2", "Lua 5.3", "LuaJ")
    IMC.registerProgramDiskLabel("dig", "dig", "Lua 5.2", "Lua 5.3", "LuaJ")
    IMC.registerProgramDiskLabel("base64", "data", "Lua 5.2", "Lua 5.3", "LuaJ")
    IMC.registerProgramDiskLabel("deflate", "data", "Lua 5.2", "Lua 5.3", "LuaJ")
    IMC.registerProgramDiskLabel("gpg", "data", "Lua 5.2", "Lua 5.3", "LuaJ")
    IMC.registerProgramDiskLabel("inflate", "data", "Lua 5.2", "Lua 5.3", "LuaJ")
    IMC.registerProgramDiskLabel("md5sum", "data", "Lua 5.2", "Lua 5.3", "LuaJ")
    IMC.registerProgramDiskLabel("sha256sum", "data", "Lua 5.2", "Lua 5.3", "LuaJ")
    IMC.registerProgramDiskLabel("refuel", "generator", "Lua 5.2", "Lua 5.3", "LuaJ")
    IMC.registerProgramDiskLabel("irc", "irc", "Lua 5.2", "Lua 5.3", "LuaJ")
    IMC.registerProgramDiskLabel("maze", "maze", "Lua 5.2", "Lua 5.3", "LuaJ")
    IMC.registerProgramDiskLabel("arp", "network", "Lua 5.2", "Lua 5.3", "LuaJ")
    IMC.registerProgramDiskLabel("ifconfig", "network", "Lua 5.2", "Lua 5.3", "LuaJ")
    IMC.registerProgramDiskLabel("ping", "network", "Lua 5.2", "Lua 5.3", "LuaJ")
    IMC.registerProgramDiskLabel("route", "network", "Lua 5.2", "Lua 5.3", "LuaJ")
    IMC.registerProgramDiskLabel("opl-flash", "openloader", "Lua 5.2", "Lua 5.3", "LuaJ")
    IMC.registerProgramDiskLabel("oppm", "oppm", "Lua 5.2", "Lua 5.3", "LuaJ")

    ForgeChunkManager.setForcedChunkLoadingCallback(OpenComputers, ChunkloaderUpgradeHandler)

    MinecraftForge.EVENT_BUS.register(EventHandler)
    MinecraftForge.EVENT_BUS.register(NanomachinesHandler.Common)
    MinecraftForge.EVENT_BUS.register(SimpleComponentTickHandler.Instance)
    MinecraftForge.EVENT_BUS.register(Tablet)

    MinecraftForge.EVENT_BUS.register(Analyzer)
    MinecraftForge.EVENT_BUS.register(AngelUpgradeHandler)
    MinecraftForge.EVENT_BUS.register(BlockChangeHandler)
    MinecraftForge.EVENT_BUS.register(ChunkloaderUpgradeHandler)
    MinecraftForge.EVENT_BUS.register(EventHandler)
    MinecraftForge.EVENT_BUS.register(ExperienceUpgradeHandler)
    MinecraftForge.EVENT_BUS.register(FileSystemAccessHandler)
    MinecraftForge.EVENT_BUS.register(HoverBootsHandler)
    MinecraftForge.EVENT_BUS.register(Loot)
    MinecraftForge.EVENT_BUS.register(NanomachinesHandler.Common)
    MinecraftForge.EVENT_BUS.register(NetworkActivityHandler)
    MinecraftForge.EVENT_BUS.register(RobotCommonHandler)
    MinecraftForge.EVENT_BUS.register(SaveHandler)
    MinecraftForge.EVENT_BUS.register(Tablet)
    MinecraftForge.EVENT_BUS.register(Waypoints)
    MinecraftForge.EVENT_BUS.register(WirelessNetwork)
    MinecraftForge.EVENT_BUS.register(WirelessNetworkCardHandler)
    MinecraftForge.EVENT_BUS.register(li.cil.oc.client.ComponentTracker)
    MinecraftForge.EVENT_BUS.register(li.cil.oc.server.ComponentTracker)

    Driver.add(ConverterNanomachines)
    Driver.add(ConverterLinkedCard)

    Driver.add(DriverAPU)
    Driver.add(DriverComponentBus)
    Driver.add(DriverCPU)
    Driver.add(DriverDataCard)
    Driver.add(DriverDebugCard)
    Driver.add(DriverEEPROM)
    Driver.add(DriverFileSystem)
    Driver.add(DriverGraphicsCard)
    Driver.add(DriverInternetCard)
    Driver.add(DriverLinkedCard)
    Driver.add(DriverLootDisk)
    Driver.add(DriverMemory)
    Driver.add(DriverNetworkCard)
    Driver.add(DriverKeyboard)
    Driver.add(DriverRedstoneCard)
    Driver.add(DriverTablet)
    Driver.add(DriverWirelessNetworkCard)

    Driver.add(DriverContainerCard)
    Driver.add(DriverContainerFloppy)
    Driver.add(DriverContainerUpgrade)

    Driver.add(DriverGeolyzer)
    Driver.add(DriverMotionSensor)
    Driver.add(DriverScreen)
    Driver.add(DriverTransposer)

    Driver.add(DriverDiskDriveMountable)
    Driver.add(DriverServer)
    Driver.add(DriverTerminalServer)

    Driver.add(DriverUpgradeAngel)
    Driver.add(DriverUpgradeBarcodeReader)
    Driver.add(DriverUpgradeBattery)
    Driver.add(DriverUpgradeChunkloader)
    Driver.add(DriverUpgradeCrafting)
    Driver.add(DriverUpgradeDatabase)
    Driver.add(DriverUpgradeExperience)
    Driver.add(DriverUpgradeGenerator)
    Driver.add(DriverUpgradeHover)
    Driver.add(DriverUpgradeInventory)
    Driver.add(DriverUpgradeInventoryController)
    Driver.add(DriverUpgradeLeash)
    Driver.add(DriverUpgradeNavigation)
    Driver.add(DriverUpgradePiston)
    Driver.add(DriverUpgradeSign)
    Driver.add(DriverUpgradeSolarGenerator)
    Driver.add(DriverUpgradeStickyPiston)
    Driver.add(DriverUpgradeTank)
    Driver.add(DriverUpgradeTankController)
    Driver.add(DriverUpgradeTractorBeam)
    Driver.add(DriverUpgradeTrading)
    Driver.add(DriverUpgradeMF)

    Driver.add(DriverAPU.Provider)
    Driver.add(DriverDataCard.Provider)
    Driver.add(DriverDebugCard.Provider)
    Driver.add(DriverEEPROM.Provider)
    Driver.add(DriverGraphicsCard.Provider)
    Driver.add(DriverInternetCard.Provider)
    Driver.add(DriverLinkedCard.Provider)
    Driver.add(DriverNetworkCard.Provider)
    Driver.add(DriverRedstoneCard.Provider)
    Driver.add(DriverWirelessNetworkCard.Provider)

    Driver.add(DriverGeolyzer.Provider)
    Driver.add(DriverMotionSensor.Provider)
    Driver.add(DriverScreen.Provider)
    Driver.add(DriverTransposer.Provider)

    Driver.add(DriverUpgradeChunkloader.Provider)
    Driver.add(DriverUpgradeCrafting.Provider)
    Driver.add(DriverUpgradeDatabase.Provider)
    Driver.add(DriverUpgradeExperience.Provider)
    Driver.add(DriverUpgradeGenerator.Provider)
    Driver.add(DriverUpgradeInventoryController.Provider)
    Driver.add(DriverUpgradeLeash.Provider)
    Driver.add(DriverUpgradeNavigation.Provider)
    Driver.add(DriverUpgradePiston.Provider)
    Driver.add(DriverUpgradeSign.Provider)
    Driver.add(DriverUpgradeStickyPiston.Provider)
    Driver.add(DriverUpgradeTankController.Provider)
    Driver.add(DriverUpgradeTractorBeam.Provider)
    Driver.add(DriverUpgradeMF.Provider)

    Driver.add(EnvironmentProviderBlocks)

    Driver.add(InventoryProviderDatabase)
    Driver.add(InventoryProviderServer)

    blacklistHost(Adapter::class.java,
      Constants.BlockName.Geolyzer,
      Constants.BlockName.MotionSensor,
      Constants.BlockName.Keyboard,
      Constants.BlockName.ScreenTier1,
      Constants.BlockName.Transposer,
      Constants.BlockName.CarpetedCapacitor,
      Constants.ItemName.Analyzer,
      Constants.ItemName.AngelUpgrade,
      Constants.ItemName.BatteryUpgradeTier1,
      Constants.ItemName.BatteryUpgradeTier2,
      Constants.ItemName.BatteryUpgradeTier3,
      Constants.ItemName.ChunkloaderUpgrade,
      Constants.ItemName.CraftingUpgrade,
      Constants.ItemName.ExperienceUpgrade,
      Constants.ItemName.GeneratorUpgrade,
      Constants.ItemName.HoverUpgradeTier1,
      Constants.ItemName.HoverUpgradeTier2,
      Constants.ItemName.InventoryUpgrade,
      Constants.ItemName.NavigationUpgrade,
      Constants.ItemName.PistonUpgrade,
      Constants.ItemName.StickyPistonUpgrade,
      Constants.ItemName.SolarGeneratorUpgrade,
      Constants.ItemName.TankUpgrade,
      Constants.ItemName.TractorBeamUpgrade,
      Constants.ItemName.LeashUpgrade,
      Constants.ItemName.TradingUpgrade)
    blacklistHost(Drone::class.java,
      Constants.BlockName.Keyboard,
      Constants.BlockName.ScreenTier1,
      Constants.BlockName.Transposer,
      Constants.BlockName.CarpetedCapacitor,
      Constants.ItemName.Analyzer,
      Constants.ItemName.APUTier1,
      Constants.ItemName.APUTier2,
      Constants.ItemName.GraphicsCardTier1,
      Constants.ItemName.GraphicsCardTier2,
      Constants.ItemName.GraphicsCardTier3,
      Constants.ItemName.NetworkCard,
      Constants.ItemName.RedstoneCardTier1,
      Constants.ItemName.CraftingUpgrade,
      Constants.ItemName.HoverUpgradeTier1,
      Constants.ItemName.HoverUpgradeTier2)
    blacklistHost(Microcontroller::class.java,
      Constants.BlockName.Keyboard,
      Constants.BlockName.ScreenTier1,
      Constants.BlockName.CarpetedCapacitor,
      Constants.ItemName.Analyzer,
      Constants.ItemName.APUTier1,
      Constants.ItemName.APUTier2,
      Constants.ItemName.GraphicsCardTier1,
      Constants.ItemName.GraphicsCardTier2,
      Constants.ItemName.GraphicsCardTier3,
      Constants.ItemName.AngelUpgrade,
      Constants.ItemName.CraftingUpgrade,
      Constants.ItemName.DatabaseUpgradeTier1,
      Constants.ItemName.DatabaseUpgradeTier2,
      Constants.ItemName.DatabaseUpgradeTier3,
      Constants.ItemName.ExperienceUpgrade,
      Constants.ItemName.GeneratorUpgrade,
      Constants.ItemName.HoverUpgradeTier1,
      Constants.ItemName.HoverUpgradeTier2,
      Constants.ItemName.InventoryUpgrade,
      Constants.ItemName.InventoryControllerUpgrade,
      Constants.ItemName.NavigationUpgrade,
      Constants.ItemName.TankUpgrade,
      Constants.ItemName.TankControllerUpgrade,
      Constants.ItemName.TractorBeamUpgrade,
      Constants.ItemName.LeashUpgrade,
      Constants.ItemName.TradingUpgrade)
    blacklistHost(Robot::class.java,
      Constants.BlockName.Transposer,
      Constants.BlockName.CarpetedCapacitor,
      Constants.ItemName.Analyzer,
      Constants.ItemName.LeashUpgrade)
    blacklistHost(li.cil.oc.api.internal.Tablet::class.java,
      Constants.BlockName.ScreenTier1,
      Constants.BlockName.Transposer,
      Constants.BlockName.CarpetedCapacitor,
      Constants.ItemName.NetworkCard,
      Constants.ItemName.RedstoneCardTier1,
      Constants.ItemName.AngelUpgrade,
      Constants.ItemName.ChunkloaderUpgrade,
      Constants.ItemName.CraftingUpgrade,
      Constants.ItemName.DatabaseUpgradeTier1,
      Constants.ItemName.DatabaseUpgradeTier2,
      Constants.ItemName.DatabaseUpgradeTier3,
      Constants.ItemName.ExperienceUpgrade,
      Constants.ItemName.GeneratorUpgrade,
      Constants.ItemName.HoverUpgradeTier1,
      Constants.ItemName.HoverUpgradeTier2,
      Constants.ItemName.InventoryUpgrade,
      Constants.ItemName.InventoryControllerUpgrade,
      Constants.ItemName.TankUpgrade,
      Constants.ItemName.TankControllerUpgrade,
      Constants.ItemName.LeashUpgrade,
      Constants.ItemName.TradingUpgrade)

    // Note: kinda nasty, but we have to check for availability for extended
    // redstone mods after integration init, so we have to set tier two
    // redstone card availability here, after all other mods were inited.
    if (BundledRedstone.isAvailable) {
      OpenComputers.log.info("Found extended redstone mods, enabling tier two redstone card.")
      when (val item = Delegator.subItem(Items.get(Constants.ItemName.RedstoneCardTier2).createItemStack(1))) {
        is RedstoneCard -> item.showInItemList = true
      }
    }

    Manual.addProvider(DefinitionPathProvider)
    Manual.addProvider(ResourceContentProvider(Settings.resourceDomain, "doc/"))
    Manual.addProvider("", TextureImageProvider)
    Manual.addProvider("item", ItemImageProvider)
    Manual.addProvider("block", BlockImageProvider)
    Manual.addProvider("oredict", OreDictImageProvider)

    Manual.addTab(TextureTabIconRenderer(Textures.GUI.ManualHome), "oc:gui.Manual.Home", "%LANGUAGE%/index.md")
    Manual.addTab(ItemStackTabIconRenderer(Items.get("case1").createItemStack(1)), "oc:gui.Manual.Blocks", "%LANGUAGE%/block/index.md")
    Manual.addTab(ItemStackTabIconRenderer(Items.get("cpu1").createItemStack(1)), "oc:gui.Manual.Items", "%LANGUAGE%/item/index.md")

    Nanomachines.addProvider(DisintegrationProvider)
    Nanomachines.addProvider(HungryProvider)
    Nanomachines.addProvider(ParticleProvider)
    Nanomachines.addProvider(PotionProvider)
    Nanomachines.addProvider(MagnetProvider)
  }

  @JvmStatic
  fun useWrench(player: EntityPlayer, pos: BlockPos, changeDurability: Boolean): Boolean =
    when (val item = player.heldItemMainhand.item) {
      is Wrench -> item.useWrenchOnBlock(player, player.entityWorld, pos, !changeDurability)
      else -> false
    }

  @JvmStatic
  fun isWrench(stack: ItemStack): Boolean = stack.item is Wrench

  @JvmStatic
  fun canCharge(stack: ItemStack): Boolean = when (val item = stack.item) {
    is Chargeable -> item.canCharge(stack)
    else -> false
  }

  @JvmStatic
  fun charge(stack: ItemStack, amount: Double, simulate: Boolean): Double =
    when (val item = stack.item) {
      is Chargeable -> item.charge(stack, amount, simulate)
      else -> amount
    }

  @JvmStatic
  fun inkCartridgeInkProvider(stack: ItemStack): Int =
    if (Items.get(stack) == Items.get(Constants.ItemName.InkCartridge))
      Settings.get.printInkValue
    else
      0

  @JvmStatic
  fun dyeInkProvider(stack: ItemStack): Int =
    if (Color.isDye(stack))
      Settings.get.printInkValue / 10
    else
      0

  private fun blacklistHost(host: Class<*>, vararg itemNames: String) {
    for (itemName in itemNames) {
      try {
        IMC.blacklistHost(itemName, host, Items.get(itemName).createItemStack(1))
      } catch (t: Throwable) {
        OpenComputers.log.warn("Error blacklisting '$itemName' for '${host.simpleName}.", t)
      }
    }
  }

  object DefinitionPathProvider : PathProvider {
    private val Blacklist = setOf(
      Constants.ItemName.Debugger,
      Constants.ItemName.DiamondChip,
      Constants.BlockName.Endstone
    )

    override fun pathFor(stack: ItemStack): String? = Items.get(stack)?.let { checkBlacklisted(it) }

    override fun pathFor(world: World, pos: BlockPos): String? = when (val block = world.getBlockState(pos).block) {
      is SimpleBlock -> checkBlacklisted(Items.get(ItemStack(block)))
      else -> null
    }

    private fun checkBlacklisted(info: ItemInfo?): String? =
      if (info == null || Blacklist.contains(info.name())) null
      else if (info.block() != null) "%LANGUAGE%/block/${info.name()}.md"
      else "%LANGUAGE%/item/${info.name()}.md"
  }
}
