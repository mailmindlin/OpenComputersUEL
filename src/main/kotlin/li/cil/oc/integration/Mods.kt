package li.cil.oc.integration

import li.cil.oc.Settings
// import li.cil.oc.integration
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.ModAPIManager
import net.minecraftforge.fml.common.versioning.ArtifactVersion
import net.minecraftforge.fml.common.versioning.VersionParser
import net.minecraftforge.fml.common.ModContainer
import forestry.core.proxy.Proxies



internal interface Mod {
  val id: String
  val isModAvailable: Boolean
}

internal interface ModProxy {
    val mod: Mod?
    fun initialize()
}

internal object Mods {
  private val handlers = mutableSetOf<ModProxy>()
  private val knownMods = mutableListOf<ModBase>()

  // ----------------------------------------------------------------------- //

  val All: List<ModBase> get() = knownMods.toList()
  internal val AppliedEnergistics2 = ClassBasedMod(IDs.AppliedEnergistics2, "appeng.api.storage.channels.IItemStorageChannel")
  internal val CoFHCore = SimpleMod(IDs.CoFHCore)
  internal val ComputerCraft = SimpleMod(IDs.ComputerCraft)
  internal val EnderIO = SimpleMod(IDs.EnderIO)
  internal val ExtraCells = SimpleMod(IDs.ExtraCells, "@[2.5.2,)")
  internal val Forestry = SimpleMod(IDs.Forestry, "@[5.2,)")
  internal val IndustrialCraft2 = SimpleMod(IDs.IndustrialCraft2)
  internal val Forge = SimpleMod(IDs.Forge)
  internal val Gregtech = SimpleMod(IDs.Gregtech)
  internal val JustEnoughItems = SimpleMod(IDs.JustEnoughItems)
  internal val Mekanism = SimpleMod(IDs.Mekanism)
  internal val MekanismGas = SimpleMod(IDs.MekanismGas)
  internal val Minecraft = SimpleMod(IDs.Minecraft)
  internal val OpenComputers = SimpleMod(IDs.OpenComputers)
  internal val Railcraft = SimpleMod(IDs.Railcraft)
  internal val TIS3D = SimpleMod(IDs.TIS3D, "@[0.9,)")
  internal val Waila = SimpleMod(IDs.Waila)
  internal val ProjectRedBase = SimpleMod((IDs.ProjectRedCore))
  internal val ProjectRedTransmission = SimpleMod((IDs.ProjectRedTransmission))
  internal val DraconicEvolution = SimpleMod(IDs.DraconicEvolution)
  internal val EnderStorage = SimpleMod(IDs.EnderStorage)
  internal val Thaumcraft = SimpleMod(IDs.Thaumcraft)
  internal val Charset = SimpleMod(IDs.Charset)
  internal val WirelessRedstoneCBE = SimpleMod(IDs.WirelessRedstoneCBE)

  // ----------------------------------------------------------------------- //

  private val Proxies: Array<ModProxy> = arrayOf(
    li.cil.oc.integration.appeng.ModAppEng,
    li.cil.oc.integration.cofh.item.ModCoFHItem,
    li.cil.oc.integration.cofh.tileentity.ModCoFHTileEntity,
    li.cil.oc.integration.ec.ModExtraCells,
    li.cil.oc.integration.enderio.ModEnderIO,
    li.cil.oc.integration.forestry.ModForestry,
    li.cil.oc.integration.ic2.ModIndustrialCraft2,
    li.cil.oc.integration.greg.ModGregtechCEU,
    li.cil.oc.integration.minecraftforge.ModMinecraftForge,
    li.cil.oc.integration.railcraft.ModRailcraft,
    li.cil.oc.integration.tis3d.ModTIS3D,
    li.cil.oc.integration.mekanism.ModMekanism,
    li.cil.oc.integration.mekanism.gas.ModMekanismGas,
    li.cil.oc.integration.minecraft.ModMinecraft,
    li.cil.oc.integration.waila.ModWaila,
    li.cil.oc.integration.projectred.ModProjectRed,
    li.cil.oc.integration.computercraft.ModComputerCraft,
    li.cil.oc.integration.enderstorage.ModEnderStorage,
    li.cil.oc.integration.thaumcraft.ModThaumcraft,
    li.cil.oc.integration.charset.ModCharset,
    li.cil.oc.integration.wrcbe.ModWRCBE,

    // We go late to ensure all other mod integration is done, e.g. to
    // allow properly checking if wireless redstone is present.
    li.cil.oc.integration.opencomputers.ModOpenComputers,
  )

  fun init() {
    Proxies.forEach { proxy -> tryInit(proxy) }
  }

  private fun tryInit(mod: ModProxy) {
    val isBlacklisted = mod.mod?.let { Settings.get.modBlacklist.contains(it.id) } ?: false;
    val alwaysEnabled = mod.mod == null || mod.mod == Mods.Minecraft
    if (!isBlacklisted && (alwaysEnabled || mod.mod!!.isModAvailable) && handlers.add(mod)) {
      li.cil.oc.OpenComputers.log.debug("Initializing mod integration for '${mod.mod!!.id}'.")
      try {
        mod.initialize()
      } catch (e: Exception) {
          li.cil.oc.OpenComputers.log.warn("Error initializing integration for '${mod.mod!!.id}'", e)
      }
    }
  }

  // ----------------------------------------------------------------------- //

  object IDs {
    const val AppliedEnergistics2 = "appliedenergistics2"
    const val CoFHCore = "cofhcore"
    const val ComputerCraft = "computercraft"
    const val EnderIO = "enderio"
    const val ExtraCells = "extracells"
    const val Forestry = "forestry"
    const val Forge = "forge"
    const val IndustrialCraft2 = "ic2"
    const val Gregtech = "gregtech"
    const val JustEnoughItems = "jei"
    const val Mekanism = "mekanism"
    const val MekanismGas = "MekanismAPI|gas"
    const val Minecraft = "minecraft"
    const val OpenComputers = "opencomputers"
    const val Railcraft = "railcraft"
    const val TIS3D = "tis3d"
    const val Waila = "waila"
    const val ProjectRedCore = "projectred-core"
    const val ProjectRedTransmission = "projectred-transmission"
    const val DraconicEvolution = "draconicevolution"
    const val EnderStorage = "enderstorage"
    const val Thaumcraft = "thaumcraft"
    const val Charset = "charset"
    const val WirelessRedstoneCBE = "wrcbe"
  }

  // ----------------------------------------------------------------------- //

  abstract internal class ModBase(override val id: String): Mod {
    init { knownMods += this }

    override abstract val isModAvailable: Boolean

    val container: ModContainer? get() = Loader.instance().indexedModList.get(id)
    val version: ArtifactVersion? get() = container?.let { it.processedVersion }
  }

  internal class SimpleMod(id: String, private val version_: String = ""): ModBase(id) {
    override val isModAvailable: Boolean by lazy {
      val version = VersionParser.parseVersionReference(id + this.version_)
      if (Loader.isModLoaded(version.label))
        version.containsVersion(Loader.instance().indexedModList.get(version.label)!!.processedVersion)
      else ModAPIManager.INSTANCE.hasAPI(version.label)
    }
  }

  internal class ClassBasedMod(id: String, vararg val classNames: String): ModBase(id) {
    override val isModAvailable: Boolean by lazy {
      Loader.isModLoaded(id) && classNames.all { className ->
        try {
          Class.forName(className) != null
        } catch (_: Exception) {
          false
        }
      }
    }
  }
}
