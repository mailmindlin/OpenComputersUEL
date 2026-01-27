package li.cil.oc.client

import li.cil.oc.OpenComputers
import li.cil.oc.api.API
import li.cil.oc.client.renderer.HighlightRenderer
import li.cil.oc.client.renderer.MFUTargetRenderer
import li.cil.oc.client.renderer.PetRenderer
import li.cil.oc.client.renderer.TextBufferRenderCache
import li.cil.oc.client.renderer.WirelessNetworkDebugRenderer
import li.cil.oc.client.renderer.block.ModelInitialization
import li.cil.oc.client.renderer.block.NetSplitterModel
import li.cil.oc.client.renderer.entity.DroneRenderer
import li.cil.oc.client.renderer.tileentity.*
import li.cil.oc.common.component.TextBuffer
import li.cil.oc.common.entity.Drone
import li.cil.oc.common.event.NanomachinesHandler
import li.cil.oc.common.event.RackMountableRenderHandler
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.tileentity.Adapter as TileEntityAdapter
import li.cil.oc.common.tileentity.Assembler as TileEntityAssembler
import li.cil.oc.common.tileentity.Case as TileEntityCase
import li.cil.oc.common.tileentity.Charger as TileEntityCharger
import li.cil.oc.common.tileentity.Disassembler as TileEntityDisassembler
import li.cil.oc.common.tileentity.DiskDrive as TileEntityDiskDrive
import li.cil.oc.common.tileentity.Geolyzer as TileEntityGeolyzer
import li.cil.oc.common.tileentity.Hologram as TileEntityHologram
import li.cil.oc.common.tileentity.Microcontroller as TileEntityMicrocontroller
import li.cil.oc.common.tileentity.NetSplitter as TileEntityNetSplitter
import li.cil.oc.common.tileentity.PowerDistributor as TileEntityPowerDistributor
import li.cil.oc.common.tileentity.Printer as TileEntityPrinter
import li.cil.oc.common.tileentity.Raid as TileEntityRaid
import li.cil.oc.common.tileentity.Rack as TileEntityRack
import li.cil.oc.common.tileentity.Relay as TileEntityRelay
import li.cil.oc.common.tileentity.RobotProxy as TileEntityRobotProxy
import li.cil.oc.common.tileentity.Screen as TileEntityScreen
import li.cil.oc.common.tileentity.Transposer as TileEntityTransposer
import li.cil.oc.common.Proxy as CommonProxy
import li.cil.oc.util.Audio
import net.minecraft.block.Block
import net.minecraft.client.renderer.entity.Render
import net.minecraft.client.renderer.entity.RenderManager
import net.minecraft.item.Item
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.client.registry.ClientRegistry
import net.minecraftforge.fml.client.registry.IRenderFactory
import net.minecraftforge.fml.client.registry.RenderingRegistry
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.network.NetworkRegistry
import org.lwjgl.opengl.GLContext

internal class Proxy : CommonProxy() {
  override fun preInit(e: FMLPreInitializationEvent) {
    super.preInit(e)

    API.manual = Manual

    CommandHandler.register()
    MinecraftForge.EVENT_BUS.register(Textures)
    MinecraftForge.EVENT_BUS.register(NetSplitterModel)

    ModelInitialization.preInit()

    RenderingRegistry.registerEntityRenderingHandler(Drone::class.java, ::DroneRenderer)
  }

  override fun init(e: FMLInitializationEvent) {
    super.init(e)

    OpenComputers.channel.register(PacketHandler)

    ColorHandler.init()

    ClientRegistry.bindTileEntitySpecialRenderer(TileEntityAdapter::class.java, AdapterRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(TileEntityAssembler::class.java, AssemblerRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(TileEntityCase::class.java, CaseRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(TileEntityCharger::class.java, ChargerRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(TileEntityDisassembler::class.java, DisassemblerRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(TileEntityDiskDrive::class.java, DiskDriveRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(TileEntityGeolyzer::class.java, GeolyzerRenderer)
    if (GLContext.getCapabilities().OpenGL15)
      ClientRegistry.bindTileEntitySpecialRenderer(TileEntityHologram::class.java, HologramRenderer)
    else
      ClientRegistry.bindTileEntitySpecialRenderer(TileEntityHologram::class.java, HologramRendererFallback)
    ClientRegistry.bindTileEntitySpecialRenderer(TileEntityMicrocontroller::class.java, MicrocontrollerRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(TileEntityNetSplitter::class.java, NetSplitterRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(TileEntityPowerDistributor::class.java, PowerDistributorRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(TileEntityPrinter::class.java, PrinterRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(TileEntityRaid::class.java, RaidRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(TileEntityRack::class.java, RackRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(TileEntityRelay::class.java, RelayRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(TileEntityRobotProxy::class.java, RobotRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(TileEntityScreen::class.java, ScreenRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(TileEntityTransposer::class.java, TransposerRenderer)

    ClientRegistry.registerKeyBinding(KeyBindings.clipboardPaste)

    MinecraftForge.EVENT_BUS.register(HighlightRenderer)
    MinecraftForge.EVENT_BUS.register(NanomachinesHandler.Client)
    MinecraftForge.EVENT_BUS.register(PetRenderer)
    MinecraftForge.EVENT_BUS.register(RackMountableRenderHandler)
    MinecraftForge.EVENT_BUS.register(Sound)
    MinecraftForge.EVENT_BUS.register(TextBuffer)
    MinecraftForge.EVENT_BUS.register(MFUTargetRenderer)
    MinecraftForge.EVENT_BUS.register(WirelessNetworkDebugRenderer)

    //TODO
    // NetworkRegistry.INSTANCE.registerGuiHandler(OpenComputers, GuiHandler)

    MinecraftForge.EVENT_BUS.register(Audio)
    MinecraftForge.EVENT_BUS.register(HologramRenderer)
    MinecraftForge.EVENT_BUS.register(PetRenderer)
    MinecraftForge.EVENT_BUS.register(Sound)
    MinecraftForge.EVENT_BUS.register(TextBufferRenderCache)
  }

  override fun registerModel(instance: Delegate, id: String) = ModelInitialization.registerModel(instance, id)

  override fun registerModel(instance: Item, id: String) = ModelInitialization.registerModel(instance, id)

  override fun registerModel(instance: Block, id: String) = ModelInitialization.registerModel(instance, id)
}
