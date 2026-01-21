package li.cil.oc.client

import li.cil.oc.Settings
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.util.RenderState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.util.ResourceLocation
import net.minecraftforge.client.event.TextureStitchEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object Textures {

  object Font : TextureBundle() {
    val Aliased = L("chars_aliased")
    val AntiAliased = L("chars")

    override val basePath = "textures/font/%s.png"

    override fun loader(map: TextureMap, loc: ResourceLocation) = Textures.bind(loc)
  }

  object GUI : TextureBundle() {
    val Background = L("background")
    val Bar = L("bar")
    val Borders = L("borders")
    val ButtonDriveMode = L("button_drive_mode")
    val ButtonPower = L("button_power")
    val ButtonRange = L("button_range")
    val ButtonRun = L("button_run")
    val ButtonScroll = L("button_scroll")
    val ButtonSide = L("button_side")
    val ButtonRelay = L("button_relay")
    val Computer = L("computer")
    val Database = L("database")
    val Database1 = L("database1")
    val Database2 = L("database2")
    val Disassembler = L("disassembler")
    val Drive = L("drive")
    val Drone = L("drone")
    val KeyboardMissing = L("keyboard_missing")
    val Manual = L("manual")
    val ManualHome = L("manual_home")
    val ManualMissingItem = L("manual_missing_item")
    val ManualTab = L("manual_tab")
    val Nanomachines = L("nanomachines_power")
    val NanomachinesBar = L("nanomachines_power_bar")
    val Printer = L("printer")
    val PrinterInk = L("printer_ink")
    val PrinterMaterial = L("printer_material")
    val PrinterProgress = L("printer_progress")
    val Rack = L("rack")
    val Raid = L("raid")
    val Range = L("range")
    val Robot = L("robot")
    val RobotAssembler = L("robot_assembler")
    val RobotNoScreen = L("robot_noscreen")
    val RobotSelection = L("robot_selection")
    val Server = L("server")
    val Slot = L("slot")
    val UpgradeTab = L("upgrade_tab")
    val Waypoint = L("waypoint")

    override val basePath = "textures/gui/%s.png"

    override fun loader(map: TextureMap, loc: ResourceLocation) = Textures.bind(loc)
  }

  object Icons : TextureBundle() {
    private val ForSlotType = Slot.All.associateBy({ it }, { L(it) })
    private val ForTier = mapOf(Tier.None to L("na")) + (Tier.One..Tier.Three).associateBy({ it }, { L("tier$it") })

    fun get(slotType: String): ResourceLocation? = ForSlotType[slotType]

    fun get(tier: Int): ResourceLocation? = ForTier[tier]

    override val basePath = "textures/icons/%s.png"

    override fun loader(map: TextureMap, loc: ResourceLocation) = Textures.bind(loc)
  }

  object Model : TextureBundle() {
    val UpgradeCrafting = L("crafting_upgrade")
    val UpgradeGenerator = L("generator_upgrade")
    val UpgradeInventory = L("inventory_upgrade")
    val HologramEffect = L("hologram_effect")
    val Drone = L("drone")
    val Robot = L("robot")

    override val basePath = "textures/model/%s.png"

    override fun loader(map: TextureMap, loc: ResourceLocation) = Textures.bind(loc)
  }

  object Item : TextureBundle() {
    val DroneItem = L("drone")
    val Robot = L("robot")

    override val basePath = "items/%s"

    override fun loader(map: TextureMap, loc: ResourceLocation) = map.registerSprite(loc)
  }

  // These are kept in the block texture atlas to support animations.
  object Block : TextureBundle() {
    val AdapterOn = L("overlay/adapter_on")
    val AssemblerSideAssembling = L("overlay/assembler_side_assembling")
    val AssemblerSideOn = L("overlay/assembler_side_on")
    val AssemblerTopOn = L("overlay/assembler_top_on")
    val CaseFrontActivity = L("overlay/case_front_activity")
    val CaseFrontError = L("overlay/case_front_error")
    val CaseFrontOn = L("overlay/case_front_on")
    val ChargerFrontOn = L("overlay/charger_front_on")
    val ChargerSideOn = L("overlay/charger_side_on")
    val DisassemblerSideOn = L("overlay/disassembler_side_on")
    val DisassemblerTopOn = L("overlay/disassembler_top_on")
    val DiskDriveFrontActivity = L("overlay/diskDrive_front_activity")
    val GeolyzerTopOn = L("overlay/geolyzer_top_on")
    val MicrocontrollerFrontLight = L("overlay/microcontroller_front_light")
    val MicrocontrollerFrontOn = L("overlay/microcontroller_front_on")
    val MicrocontrollerFrontError = L("overlay/microcontroller_front_error")
    val NetSplitterOn = L("overlay/netSplitter_on")
    val PowerDistributorSideOn = L("overlay/powerDistributor_side_on")
    val PowerDistributorTopOn = L("overlay/powerDistributor_top_on")
    val RackDiskDrive = L("rack_disk_drive")
    val RackDiskDriveActivity = L("overlay/rack_disk_drive_activity")
    val RackServer = L("rack_server")
    val RackServerActivity = L("overlay/rack_server_activity")
    val RackServerOn = L("overlay/rack_server_on")
    val RackServerError = L("overlay/rack_server_error")
    val RackServerNetworkActivity = L("overlay/rack_server_network_activity")
    val RackTerminalServer = L("rack_terminal_server")
    val RackTerminalServerOn = L("overlay/rack_terminal_server_on")
    val RackTerminalServerPresence = L("overlay/rack_terminal_server_presence")
    val RaidFrontActivity = L("overlay/raid_front_activity")
    val RaidFrontError = L("overlay/raid_front_error")
    val ScreenUpIndicator = L("overlay/screen_up_indicator")
    val SwitchSideOn = L("overlay/switch_side_on")
    val TransposerOn = L("overlay/transposer_on")

    val Cable = L("cable")
    val CableCap = L("cableCap")
    val GenericTop = L("generic_top", load = false)
    val NetSplitterSide = L("netSplitter_side")
    val NetSplitterTop = L("netSplitter_top")
    val RackFront = L("rack_front", load = false)
    val RackSide = L("rack_side", load = false)

    // Kill me now.
    object Screen {
      val Single = arrayOf(
        L("screen/b"),
        L("screen/b"),
        L("screen/b2"),
        L("screen/b2"),
        L("screen/b2"),
        L("screen/b2")
      )

      val SingleFront = arrayOf(
        L("screen/f"),
        L("screen/f2")
      )

      val Horizontal = arrayOf(
        // Vertical.
        arrayOf(
          arrayOf(
            L("screen/bht"),
            L("screen/bhb"),
            L("screen/bht2"),
            L("screen/bht2"),
            L("screen/b2"),
            L("screen/b2")
          ),
          arrayOf(
            L("screen/bhm"),
            L("screen/bhm"),
            L("screen/bhm2"),
            L("screen/bhm2"),
            L("screen/b"), // Not rendered.
            L("screen/b") // Not rendered.
          ),
          arrayOf(
            L("screen/bhb"),
            L("screen/bht"),
            L("screen/bhb2"),
            L("screen/bhb2"),
            L("screen/b2"),
            L("screen/b2")
          )
        ),
        // Horizontal.
        arrayOf(
          arrayOf(
            L("screen/bhb2"),
            L("screen/bht2"),
            L("screen/bht"),
            L("screen/bhb"),
            L("screen/b2"),
            L("screen/b2")
          ),
          arrayOf(
            L("screen/bhm2"),
            L("screen/bhm2"),
            L("screen/bhm"),
            L("screen/bhm"),
            L("screen/b"), // Not rendered.
            L("screen/b") // Not rendered.
          ),
          arrayOf(
            L("screen/bht2"),
            L("screen/bhb2"),
            L("screen/bhb"),
            L("screen/bht"),
            L("screen/b2"),
            L("screen/b2")
          )
        )
      )

      val HorizontalFront = arrayOf(
        // Vertical.
        arrayOf(
          L("screen/fhb2"),
          L("screen/fhm2"),
          L("screen/fht2")
        ),
        // Horizontal.
        arrayOf(
          L("screen/fhb"),
          L("screen/fhm"),
          L("screen/fht")
        )
      )

      val Vertical = arrayOf(
        // Vertical.
        arrayOf(
          arrayOf(
            L("screen/b"),
            L("screen/b"),
            L("screen/bvt"),
            L("screen/bvt"),
            L("screen/bvt"),
            L("screen/bvt")
          ),
          arrayOf(
            L("screen/b"), // Not rendered.
            L("screen/b"), // Not rendered.
            L("screen/bvm"),
            L("screen/bvm"),
            L("screen/bvm"),
            L("screen/bvm")
          ),
          arrayOf(
            L("screen/b"),
            L("screen/b"),
            L("screen/bvb2"),
            L("screen/bvb2"),
            L("screen/bvb2"),
            L("screen/bvb2")
          )
        ),
        // Horizontal.
        arrayOf(
          arrayOf(
            L("screen/b2"),
            L("screen/b2"),
            L("screen/bvt"),
            L("screen/bvt"),
            L("screen/bht2"),
            L("screen/bhb2")
          ),
          arrayOf(
            L("screen/b"), // Not rendered.
            L("screen/b"), // Not rendered.
            L("screen/bvm"),
            L("screen/bvm"),
            L("screen/bhm2"),
            L("screen/bhm2")
          ),
          arrayOf(
            L("screen/b2"),
            L("screen/b2"),
            L("screen/bvb"),
            L("screen/bvb"),
            L("screen/bhb2"),
            L("screen/bht2")
          )
        )
      )

      val VerticalFront = arrayOf(
        // Vertical.
        arrayOf(
          L("screen/fvt"),
          L("screen/fvm"),
          L("screen/fvb2")
        ),
        // Horizontal.
        arrayOf(
          L("screen/fvt"),
          L("screen/fvm"),
          L("screen/fvb")
        )
      )

      val Multi = arrayOf(
        // Vertical.
        arrayOf(
          // Top.
          arrayOf(
            arrayOf(
              L("screen/bht"),
              L("screen/bhb"),
              L("screen/btl"),
              L("screen/btr"),
              L("screen/bvb"),
              L("screen/bvt")
            ),
            arrayOf(
              L("screen/bhm"),
              L("screen/bhm"),
              L("screen/btm"),
              L("screen/btm"),
              L("screen/b"), // Not rendered.
              L("screen/b") // Not rendered.
            ),
            arrayOf(
              L("screen/bhb"),
              L("screen/bht"),
              L("screen/btr"),
              L("screen/btl"),
              L("screen/bvt"),
              L("screen/bvb")
            )
          ),
          // Middle.
          arrayOf(
            arrayOf(
              L("screen/b"), // Not rendered.
              L("screen/b"), // Not rendered.
              L("screen/bml"),
              L("screen/bmr"),
              L("screen/bvm"),
              L("screen/bvm")
            ),
            arrayOf(
              L("screen/b"), // Not rendered.
              L("screen/b"), // Not rendered.
              L("screen/bmm"),
              L("screen/bmm"),
              L("screen/b"), // Not rendered.
              L("screen/b") // Not rendered.
            ),
            arrayOf(
              L("screen/b"), // Not rendered.
              L("screen/b"), // Not rendered.
              L("screen/bmr"),
              L("screen/bml"),
              L("screen/bvm"),
              L("screen/bvt")
            )
          ),
          // Bottom.
          arrayOf(
            arrayOf(
              L("screen/bht"),
              L("screen/bhb"),
              L("screen/bbl2"),
              L("screen/bbr2"),
              L("screen/bvt"),
              L("screen/bvb2")
            ),
            arrayOf(
              L("screen/bhm"),
              L("screen/bhm"),
              L("screen/bbm2"),
              L("screen/bbm2"),
              L("screen/b"), // Not rendered.
              L("screen/b") // Not rendered.
            ),
            arrayOf(
              L("screen/bhb"),
              L("screen/bht"),
              L("screen/bbr2"),
              L("screen/bbl2"),
              L("screen/bvb2"),
              L("screen/bvt")
            )
          )
        ),
        // Horizontal.
        arrayOf(
          // Top.
          arrayOf(
            arrayOf(
              L("screen/bhb2"),
              L("screen/bht2"),
              L("screen/btl"),
              L("screen/btr"),
              L("screen/bht2"),
              L("screen/bhb2")
            ),
            arrayOf(
              L("screen/bhm2"),
              L("screen/bhm2"),
              L("screen/btm"),
              L("screen/btm"),
              L("screen/b"), // Not rendered.
              L("screen/b") // Not rendered.
            ),
            arrayOf(
              L("screen/bht2"),
              L("screen/bhb2"),
              L("screen/btr"),
              L("screen/btl"),
              L("screen/bht2"),
              L("screen/bhb2")
            )
          ),
          // Middle.
          arrayOf(
            arrayOf(
              L("screen/b"), // Not rendered.
              L("screen/b"), // Not rendered.
              L("screen/bml"),
              L("screen/bml"),
              L("screen/bhm2"),
              L("screen/bhm2")
            ),
            arrayOf(
              L("screen/b"), // Not rendered.
              L("screen/b"), // Not rendered.
              L("screen/bmm"),
              L("screen/bmm"),
              L("screen/b"), // Not rendered.
              L("screen/b") // Not rendered.
            ),
            arrayOf(
              L("screen/b"), // Not rendered.
              L("screen/b"), // Not rendered.
              L("screen/bmr"),
              L("screen/bmr"),
              L("screen/bhm2"),
              L("screen/bhm2")
            )
          ),
          // Bottom.
          arrayOf(
            arrayOf(
              L("screen/bhb2"),
              L("screen/bht2"),
              L("screen/bbl"),
              L("screen/bbr"),
              L("screen/bhb2"),
              L("screen/bht2")
            ),
            arrayOf(
              L("screen/bhm2"),
              L("screen/bhm2"),
              L("screen/bbm"),
              L("screen/bbm"),
              L("screen/b"), // Not rendered.
              L("screen/b") // Not rendered.
            ),
            arrayOf(
              L("screen/bht2"),
              L("screen/bhb2"),
              L("screen/bbr"),
              L("screen/bbl"),
              L("screen/bhb2"),
              L("screen/bht2")
            )
          )
        )
      )

      val MultiFront = arrayOf(
        // Vertical.
        arrayOf(
          arrayOf(
            L("screen/ftr"),
            L("screen/ftm"),
            L("screen/ftl")
          ),
          arrayOf(
            L("screen/fmr"),
            L("screen/fmm"),
            L("screen/fml")
          ),
          arrayOf(
            L("screen/fbr2"),
            L("screen/fbm2"),
            L("screen/fbl2")
          )
        ),
        // Horizontal.
        arrayOf(
          arrayOf(
            L("screen/ftr"),
            L("screen/ftm"),
            L("screen/ftl")
          ),
          arrayOf(
            L("screen/fmr"),
            L("screen/fmm"),
            L("screen/fml")
          ),
          arrayOf(
            L("screen/fbr"),
            L("screen/fbm"),
            L("screen/fbl")
          )
        )
      )

      // The hacks I do for namespacing...
      internal fun makeSureThisIsInitialized() {}
    }

    init {
      Screen.makeSureThisIsInitialized()
    }

    fun bind(): Unit = Textures.bind(TextureMap.LOCATION_BLOCKS_TEXTURE)

    override val basePath = "blocks/%s"

    override fun loader(map: TextureMap, loc: ResourceLocation) = map.registerSprite(loc)
  }

  fun bind(location: ResourceLocation): Unit {
    if (location == null) {
      RenderState.bindTexture(0)
    } else {
      val manager = Minecraft.getMinecraft().renderEngine
      manager.bindTexture(location)
      // IMPORTANT: manager.bindTexture uses GlStateManager.bindTexture, and
      // that has borked caching, so binding textures will sometimes fail,
      // because it'll think the texture is already bound although it isn't.
      // So we do it manually.
      val texture = manager.getTexture(location)
      if (texture != null) {
        RenderState.bindTexture(texture.glTextureId)
      }
    }
  }

  fun getSprite(location: String): TextureAtlasSprite = Minecraft.getMinecraft().textureMapBlocks.getAtlasSprite(location)

  fun getSprite(location: ResourceLocation): TextureAtlasSprite = getSprite(location.toString())

  @SubscribeEvent
  fun onTextureStitchPre(e: TextureStitchEvent.Pre): Unit {
    Font.init(e.map)
    GUI.init(e.map)
    Icons.init(e.map)
    Model.init(e.map)
    Item.init(e.map)
    Block.init(e.map)
  }

  abstract class TextureBundle {
    private val locations = mutableListOf<ResourceLocation>()

    protected val textureManager get() = Minecraft.getMinecraft().textureManager

    fun init(map: TextureMap): Unit {
      locations.forEach { loader(map, it) }
    }

    protected fun L(name: String, load: Boolean = true): ResourceLocation {
      val location = ResourceLocation(Settings.resourceDomain, String.format(basePath, name))
      if (load) locations += location
      return location
    }

    protected abstract val basePath: String

    protected abstract fun loader(map: TextureMap, loc: ResourceLocation): Unit
  }

}
