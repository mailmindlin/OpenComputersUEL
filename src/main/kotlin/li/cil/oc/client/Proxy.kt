package li.cil.oc.client

import li.cil.oc.OpenComputers
import li.cil.oc.api
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
import li.cil.oc.common.tileentity
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

    api.API.manual = Manual

    CommandHandler.register()

    MinecraftForge.EVENT_BUS.register(Textures)
    MinecraftForge.EVENT_BUS.register(NetSplitterModel)

    ModelInitialization.preInit()

    RenderingRegistry.registerEntityRenderingHandler(Drone::class.java, IRenderFactory<Drone> { manager ->
      DroneRenderer(manager)
    })
  }

  override fun init(e: FMLInitializationEvent) {
    super.init(e)

    OpenComputers.channel.register(PacketHandler)

    ColorHandler.init()

    ClientRegistry.bindTileEntitySpecialRenderer(tileentity.Adapter::class.java, AdapterRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(tileentity.Assembler::class.java, AssemblerRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(tileentity.Case::class.java, CaseRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(tileentity.Charger::class.java, ChargerRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(tileentity.Disassembler::class.java, DisassemblerRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(tileentity.DiskDrive::class.java, DiskDriveRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(tileentity.Geolyzer::class.java, GeolyzerRenderer)
    if (GLContext.getCapabilities().OpenGL15)
      ClientRegistry.bindTileEntitySpecialRenderer(tileentity.Hologram::class.java, HologramRenderer)
    else
      ClientRegistry.bindTileEntitySpecialRenderer(tileentity.Hologram::class.java, HologramRendererFallback)
    ClientRegistry.bindTileEntitySpecialRenderer(tileentity.Microcontroller::class.java, MicrocontrollerRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(tileentity.NetSplitter::class.java, NetSplitterRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(tileentity.PowerDistributor::class.java, PowerDistributorRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(tileentity.Printer::class.java, PrinterRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(tileentity.Raid::class.java, RaidRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(tileentity.Rack::class.java, RackRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(tileentity.Relay::class.java, RelayRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(tileentity.RobotProxy::class.java, RobotRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(tileentity.Screen::class.java, ScreenRenderer)
    ClientRegistry.bindTileEntitySpecialRenderer(tileentity.Transposer::class.java, TransposerRenderer)

    ClientRegistry.registerKeyBinding(KeyBindings.clipboardPaste)

    MinecraftForge.EVENT_BUS.register(HighlightRenderer)
    MinecraftForge.EVENT_BUS.register(NanomachinesHandler.Client)
    MinecraftForge.EVENT_BUS.register(PetRenderer)
    MinecraftForge.EVENT_BUS.register(RackMountableRenderHandler)
    MinecraftForge.EVENT_BUS.register(Sound)
    MinecraftForge.EVENT_BUS.register(TextBuffer)
    MinecraftForge.EVENT_BUS.register(MFUTargetRenderer)
    MinecraftForge.EVENT_BUS.register(WirelessNetworkDebugRenderer)

    NetworkRegistry.INSTANCE.registerGuiHandler(OpenComputers, GuiHandler)

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
