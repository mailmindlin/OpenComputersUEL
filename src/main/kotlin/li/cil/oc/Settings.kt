package li.cil.oc

import com.mojang.authlib.GameProfile
import com.typesafe.config.*
import com.typesafe.config.impl.OpenComputersConfigCommentManipulationHook
import li.cil.oc.common.Tier
import li.cil.oc.server.component.DebugCard
import li.cil.oc.util.InternetFilteringRule
import li.cil.oc.util.ScreenResolution
import li.cil.oc.util.by
import li.cil.oc.api.internal.TextBuffer.ColorDepth
import net.minecraft.util.ResourceLocation
import net.minecraft.world.World
import org.apache.commons.codec.binary.Hex
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.versioning.DefaultArtifactVersion
import net.minecraftforge.fml.common.versioning.VersionRange
import org.apache.commons.lang3.StringEscapeUtils
import org.luaj.vm2.ast.Str
import java.io.*
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.*
import kotlin.math.max


private fun DoubleArray.coerceAtLeast(minimumValue: Double): DoubleArray {
    for (i in this.indices) {
        this[i] = this[i].coerceAtLeast(minimumValue)
    }
    return this
}
private fun IntArray.coerceAtLeast(minimumValue: Int): IntArray {
    for (i in this.indices) {
        this[i] = this[i].coerceAtLeast(minimumValue)
    }
    return this
}

@Suppress("unused")
class Settings(val config: Config) {
    // ----------------------------------------------------------------------- //
    // client
    private fun getBoolean(name: String): Boolean = config.getBoolean(name)
    private fun getDouble(name: String): Double = config.getDouble(name)
    private fun getString(name: String): String = config.getString(name)
    private fun getDouble(name: String, range: Pair<Double, Double>): Double = getDouble(name).coerceIn(range.first, range.second)
    private fun getDouble(name: String, atLeast: Double): Double = getDouble(name).coerceAtLeast(atLeast)
    private inline fun getInt(name: String): Int = config.getInt(name)
    private inline fun getInt(name: String, range: Pair<Int, Int>): Int = getInt(name).coerceIn(range.first, range.second)
    private fun getInt(name: String, atLeast: Int): Int = getInt(name).coerceAtLeast(atLeast)

    private fun getFloat(name: String): Float = getDouble(name).toFloat()
    private inline fun getFloat(name: String, atLeast: Float): Float = getFloat(name).coerceAtLeast(atLeast)
    private fun getFloat(name: String, range: Pair<Float, Float>): Float = getFloat(name).coerceIn(range.first, range.second)
    private fun tryIntList(name: String, displayName: String, vararg defaults: Int): IntArray {
        val list = config.getIntList(name)
        if (list.size != defaults.size) {
            OpenComputers.log.warn("Bad number of $displayName, ignoring.")
            return defaults
        }
        return list.toIntArray()
    }
    private fun tryDoubleList(name: String, displayName: String, vararg defaults: Double): DoubleArray {
        val list = config.getDoubleList(name)
        if (list.size != defaults.size) {
            OpenComputers.log.warn("Bad number of $displayName, ignoring.")
            return defaults
        }
        return list.toDoubleArray()
    }

    val screenTextFadeStartDistance: Double = getDouble("client.screenTextFadeStartDistance")
    val maxScreenTextRenderDistance: Double = getDouble("client.maxScreenTextRenderDistance")
    val textLinearFiltering: Boolean = getBoolean("client.textLinearFiltering")
    val textAntiAlias: Boolean = getBoolean("client.textAntiAlias")
    val robotLabels: Boolean = getBoolean("client.robotLabels")
    val soundVolume: Float = getFloat("client.soundVolume", 0f to 2f)
    val fontCharScale: Double = getDouble("client.fontCharScale", 0.5 to 2.0)
    val hologramFadeStartDistance: Double = getDouble("client.hologramFadeStartDistance", atLeast = 0.0)
    val hologramRenderDistance: Double = getDouble("client.hologramRenderDistance", atLeast = 0.0)
    val hologramFlickerFrequency: Double = getDouble("client.hologramFlickerFrequency", atLeast = 0.0)
    val monochromeColor: Int = Integer.decode(config.getString("client.monochromeColor"))
    val fontRenderer: String = getString("client.fontRenderer")
    val beepSampleRate: Int = getInt("client.beepSampleRate")
    val beepAmplitude: Int = getInt("client.beepVolume", 0 to Byte.MAX_VALUE.toInt())
    val beepRadius: Float = getFloat("client.beepRadius", 1f to 32f)
    val nanomachineHudPos: Pair<Double, Double> = run {
        val list = config.getDoubleList("client.nanomachineHudPos")
        if (list.size == 2) {
            list[0] to list[1]
        } else {
            OpenComputers.log.warn("Bad number of HUD coordiantes, ignoring.")
            -1.0 to -1.0
        }
    }
    val enableNanomachinePfx: Boolean = getBoolean("client.enableNanomachinePfx")
    val transposerFluidTransferRate: Int = getInt("misc.transposerFluidTransferRate")

    // ----------------------------------------------------------------------- //
    // computer
    val threads: Int = getInt("computer.threads", atLeast = 1)
    val timeout: Double = getDouble("computer.timeout", atLeast = 0.0)
    val startupDelay: Double = getDouble("computer.startupDelay", atLeast = 0.05)
    val eepromSize: Int = getInt("computer.eepromSize", atLeast = 0)
    val eepromDataSize: Int = getInt("computer.eepromDataSize", atLeast = 0)
    val cpuComponentSupport: IntArray = tryIntList("computer.cpuComponentCount", "CPU component counts", 8, 12, 16, 1024)
    val callBudgets: DoubleArray = tryDoubleList("computer.callBudgets", "call budgets", 0.5, 1.0, 1.5)
    val canComputersBeOwned: Boolean = getBoolean("computer.canComputersBeOwned")
    val maxUsers: Int = getInt("computer.maxUsers", atLeast = 0)
    val maxUsernameLength: Int = getInt("computer.maxUsernameLength", atLeast = 0)
    val eraseTmpOnReboot: Boolean = getBoolean("computer.eraseTmpOnReboot")
    val executionDelay: Int = getInt("computer.executionDelay", atLeast = 0)

    // computer.lua
    val allowBytecode: Boolean = getBoolean("computer.lua.allowBytecode")
    val allowGC: Boolean = getBoolean("computer.lua.allowGC")
    val enableLua53: Boolean = getBoolean("computer.lua.enableLua53")
    val defaultLua53: Boolean = getBoolean("computer.lua.defaultLua53")
    val enableLua54: Boolean = getBoolean("computer.lua.enableLua54")
    val ramSizes: IntArray = tryIntList("computer.lua.ramSizes", "RAM sizes", 192, 256, 384, 512, 768, 1024)
    val ramScaleFor64Bit: Double = getDouble("computer.lua.ramScaleFor64Bit", atLeast = 1.0)
    val maxTotalRam: Int = getInt("computer.lua.maxTotalRam", atLeast = 0)

    // ----------------------------------------------------------------------- //
    // robot
    val allowActivateBlocks: Boolean = getBoolean("robot.allowActivateBlocks")
    val allowUseItemsWithDuration: Boolean = getBoolean("robot.allowUseItemsWithDuration")
    val canAttackPlayers: Boolean = getBoolean("robot.canAttackPlayers")
    val limitFlightHeight: Int = max(config.getInt("robot.limitFlightHeight"), -1)
    val screwCobwebs: Boolean = getBoolean("robot.notAfraidOfSpiders")
    val swingRange: Double = getDouble("robot.swingRange")
    val useAndPlaceRange: Double = getDouble("robot.useAndPlaceRange")
    val itemDamageRate: Double = getDouble("robot.itemDamageRate", 0.0 to 1.0)
    val nameFormat: String = getString("robot.nameFormat")
    val uuidFormat: String = getString("robot.uuidFormat")
    val upgradeFlightHeight: IntArray = tryIntList("robot.upgradeFlightHeight", "hover flight height counts", 64, 256)

    // robot.xp
    val baseXpToLevel: Double = getDouble("robot.xp.baseValue", atLeast = 0.0)
    val constantXpGrowth: Double = getDouble("robot.xp.constantGrowth", atLeast = 1.0)
    val exponentialXpGrowth: Double = getDouble("robot.xp.exponentialGrowth", atLeast = 1.0)
    val robotActionXp: Double = getDouble("robot.xp.actionXp", atLeast = 0.0)
    val robotExhaustionXpRate: Double = getDouble("robot.xp.exhaustionXpRate", atLeast = 0.0)
    val robotOreXpRate: Double = getDouble("robot.xp.oreXpRate", atLeast = 0.0)
    val bufferPerLevel: Double = getDouble("robot.xp.bufferPerLevel", atLeast = 0.0)
    val toolEfficiencyPerLevel: Double = getDouble("robot.xp.toolEfficiencyPerLevel", atLeast = 0.0)
    val harvestSpeedBoostPerLevel: Double = getDouble("robot.xp.harvestSpeedBoostPerLevel", atLeast = 0.0)

    // ----------------------------------------------------------------------- //
    // robot.delays

    // Note: all delays are reduced by one tick to account for the tick they are
    // performed in (since all actions are delegated to the server thread).
    val turnDelay: Double = max(config.getDouble("robot.delays.turn") - 0.06, 0.05)
    val moveDelay: Double = max(config.getDouble("robot.delays.move") - 0.06, 0.05)
    val swingDelay: Double = max(config.getDouble("robot.delays.swing") - 0.06, 0.0)
    val useDelay: Double = max(config.getDouble("robot.delays.use") - 0.06, 0.0)
    val placeDelay: Double = max(config.getDouble("robot.delays.place") - 0.06, 0.0)
    val dropDelay: Double = max(config.getDouble("robot.delays.drop") - 0.06, 0.0)
    val suckDelay: Double = max(config.getDouble("robot.delays.suck") - 0.06, 0.0)
    val harvestRatio: Double = getDouble("robot.delays.harvestRatio", atLeast = 0.0)

    // ----------------------------------------------------------------------- //
    // power
    val ignorePower: Boolean = getBoolean("power.ignorePower")
    val tickFrequency: Double = getDouble("power.tickFrequency", atLeast = 1.0)
    fun isTickMultiple(worldTime: Long): Boolean = worldTime % tickFrequency.toLong() == 0L
    fun isTickMultiple(world: World): Boolean = isTickMultiple(world.totalWorldTime)
    fun isTickMultiple(world: World?): Boolean = world?.let(::isTickMultiple) ?: false
    val chargeRateExternal: Double = getDouble("power.chargerChargeRate")
    val chargeRateTablet: Double = config.getDouble("power.chargerChargeRateTablet")
    val generatorEfficiency: Double = config.getDouble("power.generatorEfficiency")
    val solarGeneratorEfficiency: Double = config.getDouble("power.solarGeneratorEfficiency")
    val assemblerTickAmount: Double = getDouble("power.assemblerTickAmount", atLeast = 1.0)
    val disassemblerTickAmount: Double = getDouble("power.disassemblerTickAmount", atLeast = 1.0)
    val printerTickAmount: Double = getDouble("power.printerTickAmount", atLeast = 1.0)
    val powerModBlacklist: MutableList<String> = config.getStringList("power.modBlacklist")

    // power.carpetedCapacitors
    val sheepPower: Double = getDouble("power.carpetedCapacitors.sheepPower", atLeast = 0.0)
    val ocelotPower: Double = getDouble("power.carpetedCapacitors.ocelotPower", atLeast = 0.0)
    val carpetDamageChance: Double = getDouble("power.carpetedCapacitors.damageChance", 0.0 to 1.0)

    // power.buffer
    val bufferCapacitor: Double = getDouble("power.buffer.capacitor", atLeast = 0.0)
    val bufferCapacitorAdjacencyBonus: Double = getDouble("power.buffer.capacitorAdjacencyBonus", atLeast = 0.0)
    val bufferComputer: Double = getDouble("power.buffer.computer", atLeast = 0.0)
    val bufferRobot: Double = getDouble("power.buffer.robot", atLeast = 0.0)
    val bufferConverter: Double = getDouble("power.buffer.converter", atLeast = 0.0)
    val bufferDistributor: Double = getDouble("power.buffer.distributor", atLeast = 0.0)
    val bufferCapacitorUpgrades: DoubleArray = tryDoubleList("power.buffer.batteryUpgrades", "battery upgrade buffer sizes", 10000.0, 15000.0, 20000.0)
    val bufferTablet: Double = getDouble("power.buffer.tablet", atLeast = 0.0)
    val bufferAccessPoint: Double = getDouble("power.buffer.accessPoint", atLeast = 0.0)
    val bufferDrone: Double = getDouble("power.buffer.drone", atLeast = 0.0)
    val bufferMicrocontroller: Double = getDouble("power.buffer.mcu", atLeast = 0.0)
    val bufferHoverBoots: Double = getDouble("power.buffer.hoverBoots", atLeast = 1.0)
    val bufferNanomachines: Double = getDouble("power.buffer.nanomachines", atLeast = 0.0)

    // power.cost
    val computerCost: Double = getDouble("power.cost.computer", atLeast = 0.0)
    val microcontrollerCost: Double = getDouble("power.cost.microcontroller", atLeast = 0.0)
    val robotCost: Double = getDouble("power.cost.robot", atLeast = 0.0)
    val droneCost: Double = getDouble("power.cost.drone", atLeast = 0.0)
    val sleepCostFactor: Double = getDouble("power.cost.sleepFactor", atLeast = 0.0)
    val screenCost: Double = getDouble("power.cost.screen", atLeast = 0.0)
    val hologramCost: Double = getDouble("power.cost.hologram", atLeast = 0.0)
    val hddReadCost: Double = getDouble("power.cost.hddRead", atLeast = 0.0) / 1024
    val hddWriteCost: Double = getDouble("power.cost.hddWrite", atLeast = 0.0) / 1024
    val gpuSetCost: Double = getDouble("power.cost.gpuSet", atLeast = 0.0) / basicScreenPixels
    val gpuFillCost: Double = getDouble("power.cost.gpuFill", atLeast = 0.0) / basicScreenPixels
    val gpuClearCost: Double = getDouble("power.cost.gpuClear", atLeast = 0.0) / basicScreenPixels
    val gpuCopyCost: Double = getDouble("power.cost.gpuCopy", atLeast = 0.0) / basicScreenPixels
    val robotTurnCost: Double = getDouble("power.cost.robotTurn", atLeast = 0.0)
    val robotMoveCost: Double = getDouble("power.cost.robotMove", atLeast = 0.0)
    val robotExhaustionCost: Double = getDouble("power.cost.robotExhaustion", atLeast = 0.0)
    val wirelessCostPerRange: DoubleArray = tryDoubleList("power.cost.wirelessCostPerRange", "wireless card energy costs", 0.05, 0.05)
    val abstractBusPacketCost: Double = getDouble("power.cost.abstractBusPacket", atLeast = 0.0)
    val geolyzerScanCost: Double = getDouble("power.cost.geolyzerScan", atLeast = 0.0)
    val robotBaseCost: Double = getDouble("power.cost.robotAssemblyBase", atLeast = 0.0)
    val robotComplexityCost: Double = getDouble("power.cost.robotAssemblyComplexity", atLeast = 0.0)
    val microcontrollerBaseCost: Double = getDouble("power.cost.microcontrollerAssemblyBase", atLeast = 0.0)
    val microcontrollerComplexityCost: Double = getDouble("power.cost.microcontrollerAssemblyComplexity", atLeast = 0.0)
    val tabletBaseCost: Double = getDouble("power.cost.tabletAssemblyBase", atLeast = 0.0)
    val tabletComplexityCost: Double = getDouble("power.cost.tabletAssemblyComplexity", atLeast = 0.0)
    val droneBaseCost: Double = getDouble("power.cost.droneAssemblyBase", atLeast = 0.0)
    val droneComplexityCost: Double = getDouble("power.cost.droneAssemblyComplexity", atLeast = 0.0)
    val disassemblerItemCost: Double = getDouble("power.cost.disassemblerPerItem", atLeast = 0.0)
    val chunkloaderCost: Double = getDouble("power.cost.chunkloaderCost", atLeast = 0.0)
    val pistonCost: Double = getDouble("power.cost.pistonPush", atLeast = 0.0)
    val eepromWriteCost: Double = getDouble("power.cost.eepromWrite", atLeast = 0.0)
    val printCost: Double = getDouble("power.cost.printerModel", atLeast = 0.0)
    val hoverBootJump: Double = getDouble("power.cost.hoverBootJump", atLeast = 0.0)
    val hoverBootAbsorb: Double = getDouble("power.cost.hoverBootAbsorb", atLeast = 0.0)
    val hoverBootMove: Double = getDouble("power.cost.hoverBootMove", atLeast = 0.0)
    val dataCardTrivial: Double = getDouble("power.cost.dataCardTrivial", atLeast = 0.0)
    val dataCardTrivialByte: Double = getDouble("power.cost.dataCardTrivialByte", atLeast = 0.0)
    val dataCardSimple: Double = getDouble("power.cost.dataCardSimple", atLeast = 0.0)
    val dataCardSimpleByte: Double = getDouble("power.cost.dataCardSimpleByte", atLeast = 0.0)
    val dataCardComplex: Double = getDouble("power.cost.dataCardComplex", atLeast = 0.0)
    val dataCardComplexByte: Double = getDouble("power.cost.dataCardComplexByte", atLeast = 0.0)
    val dataCardAsymmetric: Double = getDouble("power.cost.dataCardAsymmetric", atLeast = 0.0)
    val transposerCost: Double = getDouble("power.cost.transposer", atLeast = 0.0)
    val nanomachineCost: Double = getDouble("power.cost.nanomachineInput", atLeast = 0.0)
    val nanomachineReconfigureCost: Double = getDouble("power.cost.nanomachinesReconfigure", atLeast = 0.0)
    val mfuCost: Double = getDouble("power.cost.mfuRelay", atLeast = 0.0)

    // power.rate
    val accessPointRate: Double = getDouble("power.rate.accessPoint", atLeast = 0.0)
    val assemblerRate: Double = getDouble("power.rate.assembler", atLeast = 0.0)
    val caseRate: DoubleArray = run {
        val base = tryDoubleList("power.rate.case", "computer case conversion rates", 5.0, 10.0, 20.0)
        // Creative case.
        base + 9001.0
    }
    val chargerRate: Double = getDouble("power.rate.charger", atLeast = 0.0)
    val disassemblerRate: Double = getDouble("power.rate.disassembler", atLeast = 0.0)
    val powerConverterRate: Double = getDouble("power.rate.powerConverter", atLeast = 0.0)
    val serverRackRate: Double = getDouble("power.rate.serverRack", atLeast = 0.0)

    // power.value
    private val valueAppliedEnergistics2: Double = getDouble("power.value.AppliedEnergistics2")
    private val valueFactorization: Double = getDouble("power.value.Factorization")
    private val valueGalacticraft: Double = getDouble("power.value.Galacticraft")
    private val valueIndustrialCraft2: Double = getDouble("power.value.IndustrialCraft2")
    private val valueMekanism: Double = getDouble("power.value.Mekanism")
    private val valuePowerAdvantage: Double = getDouble("power.value.PowerAdvantage")
    private val valueRedstoneFlux: Double = getDouble("power.value.RedstoneFlux")
    private val valueRotaryCraft: Double = config.getDouble("power.value.RotaryCraft") / 11256.0
    private val valueForgeEnergy: Double = if (config.hasPath("power.value.ForgeEnergy")) config.getDouble("power.value.ForgeEnergy") else valueRedstoneFlux

    private val valueInternal: Int = 1000

    val ratioAppliedEnergistics2: Double = valueAppliedEnergistics2 / valueInternal
    val ratioFactorization: Double = valueFactorization / valueInternal
    val ratioGalacticraft: Double = valueGalacticraft / valueInternal
    val ratioIndustrialCraft2: Double = valueIndustrialCraft2 / valueInternal
    val ratioMekanism: Double = valueMekanism / valueInternal
    val ratioPowerAdvantage: Double = valuePowerAdvantage / valueInternal
    val ratioRedstoneFlux: Double = valueRedstoneFlux / valueInternal
    val ratioRotaryCraft: Double = valueRotaryCraft / valueInternal
    val ratioForgeEnergy: Double = valueForgeEnergy / valueInternal

    // ----------------------------------------------------------------------- //
    // filesystem
    val fileCost: Int = getInt("filesystem.fileCost", atLeast = 0)
    val bufferChanges: Boolean = getBoolean("filesystem.bufferChanges")
    val hddSizes: IntArray = tryIntList("filesystem.hddSizes", "HDD sizes", 1024, 2048, 4096)
    val hddPlatterCounts: IntArray = tryIntList("filesystem.hddPlatterCounts", "HDD platter counts", 2, 4, 6)
    val floppySize: Int = getInt("filesystem.floppySize", atLeast = 0)
    val tmpSize: Int = getInt("filesystem.tmpSize", atLeast = 0)
    val maxHandles: Int = getInt("filesystem.maxHandles", atLeast = 0)
    val maxReadBuffer: Int = getInt("filesystem.maxReadBuffer", atLeast = 0)
    val sectorSeekThreshold: Int = getInt("filesystem.sectorSeekThreshold")
    val sectorSeekTime: Double = getDouble("filesystem.sectorSeekTime")

    // ----------------------------------------------------------------------- //
    // internet
    val httpEnabled: Boolean = getBoolean("internet.enableHttp")
    val httpHeadersEnabled: Boolean = getBoolean("internet.enableHttpHeaders")
    val tcpEnabled: Boolean = getBoolean("internet.enableTcp")
    val internetFilteringRules: Array<InternetFilteringRule> = config.getStringList("internet.filteringRules")
        .filter { it != "removeme" }
        .map(InternetFilteringRule::parse)
        .toTypedArray()
    val internetFilteringRulesObserved: Boolean = "removeme" !in config.getStringList("internet.filteringRules")
    val httpTimeout: Int = getInt("internet.requestTimeout", atLeast = 0) * 1000
    val maxConnections: Int = getInt("internet.maxTcpConnections", atLeast = 0)
    val internetThreads: Int = getInt("internet.threads", atLeast = 1)

    // ----------------------------------------------------------------------- //
    // switch
    val switchDefaultMaxQueueSize: Int = getInt("switch.defaultMaxQueueSize", atLeast = 1)
    val switchQueueSizeUpgrade: Int = getInt("switch.queueSizeUpgrade", atLeast = 0)
    val switchDefaultRelayDelay: Int = getInt("switch.defaultRelayDelay", atLeast = 1)
    val switchRelayDelayUpgrade: Double = getDouble("switch.relayDelayUpgrade", atLeast = 0.0)
    val switchDefaultRelayAmount: Int = getInt("switch.defaultRelayAmount", atLeast = 1)
    val switchRelayAmountUpgrade: Int = getInt("switch.relayAmountUpgrade", atLeast = 0)

    // ----------------------------------------------------------------------- //
    // hologram
    val hologramMaxScaleByTier: DoubleArray = tryDoubleList("hologram.maxScale", "hologram max scales", 3.0, 4.0).coerceAtLeast(1.0)
    val hologramMaxTranslationByTier: DoubleArray = tryDoubleList("hologram.maxTranslation", "hologram max translations", 0.25, 0.5).coerceAtLeast(0.0)
    val hologramSetRawDelay: Double = getDouble("hologram.setRawDelay", atLeast = 0.0)
    val hologramLight: Boolean = getBoolean("hologram.emitLight")

    // ----------------------------------------------------------------------- //
    // misc
    val maxScreenWidth: Int = getInt("misc.maxScreenWidth", atLeast = 1)
    val maxScreenHeight: Int = getInt("misc.maxScreenHeight", atLeast = 1)
    val inputUsername: Boolean = getBoolean("misc.inputUsername")
    val initialNetworkPacketTTL: Int = getInt("misc.initialNetworkPacketTTL", atLeast = 5)
    val maxNetworkPacketSize: Int = getInt("misc.maxNetworkPacketSize", atLeast = 0)
    // Need at least 4 for nanomachine protocol. Because I can!
    val maxNetworkPacketParts: Int = getInt("misc.maxNetworkPacketParts", atLeast = 4)
    val maxOpenPorts: IntArray = tryIntList("misc.maxOpenPorts", "max open ports", 16, 1, 16).coerceAtLeast(0)
    val maxWirelessRange: DoubleArray = tryDoubleList("misc.maxWirelessRange", "wireless card max ranges", 16.0, 400.0).coerceAtLeast(0.0)
    val rTreeMaxEntries: Int = 10
    val terminalsPerServer: Int = 4
    val updateCheck: Boolean = getBoolean("misc.updateCheck")
    val lootProbability: Int = getInt("misc.lootProbability")
    val lootRecrafting: Boolean = getBoolean("misc.lootRecrafting")
    val geolyzerRange: Int = getInt("misc.geolyzerRange")
    val geolyzerNoise: Float = getFloat("misc.geolyzerNoise", atLeast = 0f)
    val disassembleAllTheThings: Boolean = getBoolean("misc.disassembleAllTheThings")
    val disassemblerBreakChance: Double = getDouble("misc.disassemblerBreakChance", 0.0 to 1.0)
    val disassemblerInputBlacklist: MutableList<String> = config.getStringList("misc.disassemblerInputBlacklist")
    val hideOwnPet: Boolean = getBoolean("misc.hideOwnSpecial")
    val allowItemStackInspection: Boolean = getBoolean("misc.allowItemStackInspection")
    val databaseEntriesPerTier: IntArray = intArrayOf(9, 25, 81)
    // Not configurable because of GUI design.
    val presentChance: Double = config.getDouble("misc.presentChance").coerceIn(0.0, 1.0)
    val assemblerBlacklist: MutableList<String> = config.getStringList("misc.assemblerBlacklist")
    val threadPriority: Int = getInt("misc.threadPriority")
    val giveManualToNewPlayers: Boolean = getBoolean("misc.giveManualToNewPlayers")
    val dataCardSoftLimit: Int = getInt("misc.dataCardSoftLimit", atLeast = 0)
    val dataCardHardLimit: Int = getInt("misc.dataCardHardLimit", atLeast = 0)
    val dataCardTimeout: Double = getDouble("misc.dataCardTimeout", atLeast = 0.0)
    val serverRackSwitchTier: Int = (config.getInt("misc.serverRackSwitchTier") - 1).coerceIn(Tier.None, Tier.Three)
    val redstoneDelay: Double = getDouble("misc.redstoneDelay", atLeast = 0.0)
    val tradingRange: Double = getDouble("misc.tradingRange", atLeast = 0.0)
    val mfuRange: Int = getInt("misc.mfuRange", 0 to 128)

    // ----------------------------------------------------------------------- //
    // nanomachines
    val nanomachineTriggerQuota: Double = getDouble("nanomachines.triggerQuota", atLeast = 0.0)
    val nanomachineConnectorQuota: Double = getDouble("nanomachines.connectorQuota", atLeast = 0.0)
    val nanomachineMaxInputs: Int = getInt("nanomachines.maxInputs", atLeast = 1)
    val nanomachineMaxOutputs: Int = getInt("nanomachines.maxOutputs", atLeast = 1)
    val nanomachinesSafeInputsActive: Int = getInt("nanomachines.safeInputsActive", atLeast = 0)
    val nanomachinesMaxInputsActive: Int = getInt("nanomachines.maxInputsActive", atLeast = 0)
    val nanomachinesCommandDelay: Double = getDouble("nanomachines.commandDelay", atLeast = 0.0)
    val nanomachinesCommandRange: Double = getDouble("nanomachines.commandRange", atLeast = 0.0)
    val nanomachineMagnetRange: Double = getDouble("nanomachines.magnetRange", atLeast = 0.0)
    val nanomachineDisintegrationRange: Int = getInt("nanomachines.disintegrationRange", atLeast = 0)
    val nanomachinePotionWhitelist: List<Any> = config.getAnyRefList("nanomachines.potionWhitelist")
    val nanomachinesHungryDamage: Float = getFloat("nanomachines.hungryDamage", atLeast = 0f)
    val nanomachinesHungryEnergyRestored: Double = getDouble("nanomachines.hungryEnergyRestored", atLeast = 0.0)

    // ----------------------------------------------------------------------- //
    // printer
    val maxPrintComplexity: Int = getInt("printer.maxShapes")
    val printRecycleRate: Double = getDouble("printer.recycleRate")
    val chameliumEdible: Boolean = getBoolean("printer.chameliumEdible")
    val maxPrintLightLevel: Int = getInt("printer.maxBaseLightLevel", 0 to 15)
    val printCustomRedstone: Int = getInt("printer.customRedstoneCost", atLeast = 0)
    val printMaterialValue: Int = getInt("printer.materialValue", atLeast = 0)
    val printInkValue: Int = getInt("printer.inkValue", atLeast = 0)
    val printsHaveOpacity: Boolean = getBoolean("printer.printsHaveOpacity")
    val noclipMultiplier: Double = getDouble("printer.noclipMultiplier", atLeast = 0.0)

    // chunkloader
    val chunkloadDimensionBlacklist: List<Int> = getIntList(config, "chunkloader.dimBlacklist")
    val chunkloadDimensionWhitelist: List<Int> = getIntList(config, "chunkloader.dimWhitelist")

    // ----------------------------------------------------------------------- //
    // integration
    val modBlacklist: List<String> = config.getStringList("integration.modBlacklist")

    /**
     * List of classes to blacklist (mutated by IMC)
     */
    val peripheralBlacklist: MutableList<String> = config.getStringList("integration.peripheralBlacklist").toMutableList()
    val fakePlayerUuid: String = getString("integration.fakePlayerUuid")
    val fakePlayerName: String = getString("integration.fakePlayerName")
    val fakePlayerProfile: GameProfile = GameProfile(UUID.fromString(fakePlayerUuid), fakePlayerName)

    // integration.vanilla
    val enableInventoryDriver: Boolean = getBoolean("integration.vanilla.enableInventoryDriver")
    val enableTankDriver: Boolean = getBoolean("integration.vanilla.enableTankDriver")
    val enableCommandBlockDriver: Boolean = getBoolean("integration.vanilla.enableCommandBlockDriver")
    val allowItemStackNBTTags: Boolean = getBoolean("integration.vanilla.allowItemStackNBTTags")

    // integration.buildcraft
    val costProgrammingTable: Double = getDouble("integration.buildcraft.programmingTableCost", atLeast = 0.0)

    // ----------------------------------------------------------------------- //
    // debug
    val logLuaCallbackErrors: Boolean = getBoolean("debug.logCallbackErrors")
    val forceLuaJ: Boolean = getBoolean("debug.forceLuaJ")
    val allowUserdata: Boolean = !getBoolean("debug.disableUserdata")
    val allowPersistence: Boolean = !getBoolean("debug.disablePersistence")
    val limitMemory: Boolean = !getBoolean("debug.disableMemoryLimit")
    val forceCaseInsensitive: Boolean = getBoolean("debug.forceCaseInsensitiveFS")
    val logFullLibLoadErrors: Boolean = getBoolean("debug.logFullNativeLibLoadErrors")
    val forceNativeLibPlatform: String = getString("debug.forceNativeLibPlatform")
    val forceNativeLibPathFirst: String = getString("debug.forceNativeLibPathFirst")
    val logOpenGLErrors: Boolean = getBoolean("debug.logOpenGLErrors")
    val logHexFontErrors: Boolean = getBoolean("debug.logHexFontErrors")
    val alwaysTryNative: Boolean = getBoolean("debug.alwaysTryNative")
    val debugPersistence: Boolean = getBoolean("debug.verbosePersistenceErrors")
    val nativeInTmpDir: Boolean = getBoolean("debug.nativeInTmpDir")
    val periodicallyForceLightUpdate: Boolean = getBoolean("debug.periodicallyForceLightUpdate")
    val insertIdsInConverters: Boolean = getBoolean("debug.insertIdsInConverters")

    val debugCardAccess: DebugCardAccess = DebugCardAccess.parse(config.getValue("debug.debugCardAccess").unwrapped())

    val registerLuaJArchitecture: Boolean = getBoolean("debug.registerLuaJArchitecture")
    val disableLocaleChanging: Boolean = getBoolean("debug.disableLocaleChanging")

    // >= 1.7.4
    val maxSignalQueueSize: Int = max(if (config.hasPath("computer.maxSignalQueueSize")) config.getInt("computer.maxSignalQueueSize") else 256, 256)

    // >= 1.7.6
    val vramSizes: DoubleArray = tryDoubleList("gpu.vramSizes", "VRAM sizes (expected 3)", 1.0, 2.0, 3.0)

    val bitbltCost: Double = if (config.hasPath("gpu.bitbltCost")) config.getDouble("gpu.bitbltCost") else 0.5

    // >= 1.8.2
    val diskActivitySoundDelay: Int = getInt("misc.diskActivitySoundDelay", atLeast = -1)
    val maxNetworkClientPacketDistance: Double = getDouble("misc.maxNetworkClientPacketDistance", atLeast = 0.0)
    val maxNetworkClientEffectPacketDistance: Double = getDouble("misc.maxNetworkClientEffectPacketDistance", atLeast = 0.0)
    val maxNetworkClientSoundPacketDistance: Double = getDouble("misc.maxNetworkClientSoundPacketDistance", atLeast = 0.0)

    fun internetFilteringRulesInvalid(): Boolean {
        return internetFilteringRules.any { it.invalid() }
    }

    fun internetAccessConfigured(): Boolean {
        return httpEnabled || tcpEnabled
    }

    fun internetAccessAllowed(): Boolean {
        return internetAccessConfigured() && !internetFilteringRulesInvalid()
    }

    // >= 1.8.8
    val httpUserAgent: String = getString("internet.httpUserAgent")

    companion object {
        const val resourceDomain: String = "opencomputers"
        @Deprecated(message = "Use ResourceLocation", replaceWith = ReplaceWith("namespace()"))
        const val namespace: String = "oc:"
        const val savePath: String = "opencomputers/"
        const val scriptPath: String = "/assets/$resourceDomain/lua/"

        internal fun namespace(value: String): ResourceLocation = ResourceLocation("oc", value)

        @JvmField
        val screenResolutionsByTier: Array<ScreenResolution> = arrayOf(50 by 16, 80 by 25, 160 by 50)

        @JvmField
        val screenDepthsByTier: Array<ColorDepth> = arrayOf(
            ColorDepth.OneBit,
            ColorDepth.FourBit,
            ColorDepth.EightBit
        )

        @JvmField
        val deviceComplexityByTier: IntArray = intArrayOf(12, 24, 32, 9001)

        @JvmField
        var rTreeDebugRenderer: Boolean = false

        @JvmField
        var blockRenderId: Int = -1

        private val forbiddenConfigLists: List<String> = listOf(
            /* 1.8.3+ filtering rules migration */
            "internet.blacklist", "internet.whitelist"
        )
        private const val prefix: String = "opencomputers."

        @JvmStatic
        val basicScreenPixels: Int
            get() = screenResolutionsByTier[0].pixels

        private var settings: Settings? = null

        val tryGet: Settings?
            @JvmName("tryGet")
            @JvmStatic
            get() = settings

        val get: Settings
            @JvmName("get")
            @JvmStatic
            get() = settings!!

        @JvmStatic
        fun load(file: File) {
            val EOL = System.lineSeparator()
            // typesafe config's internal method for loading the reference.conf file
            // seems to fail on some systems (as does their parseResource method), so
            // we'll have to load the default config manually. This was reported on the
            // Minecraft Forums, I could not reproduce the issue, but this version has
            // reportedly fixed the problem.
            val defaults = run {
                val inputStream = Settings::class.java.getResourceAsStream("/application.conf")
                val config = inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.joinToString(separator = EOL, postfix = EOL)
                }
                inputStream.close()
                ConfigFactory.parseString(config)
            }
            val config = try {
                val plain = file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.joinToString(separator = EOL, postfix = EOL)
                }
                val config = patchConfig(ConfigFactory.parseString(plain), defaults).withFallback(defaults)
                settings = Settings(config.getConfig("opencomputers"))
                config
            } catch (e: Throwable) {
                if (file.exists()) {
                    throw RuntimeException("Error parsing configuration file. To restore defaults, delete '${file.name}' and restart the game.", e)
                }
                settings = Settings(defaults.getConfig("opencomputers"))
                defaults
            }
            for (key in forbiddenConfigLists) {
                if (config.hasPath(prefix + key)) {
                    if (config.getStringList(prefix + key).isNotEmpty()) {
                        throw RuntimeException("Error parsing configuration file: removed configuration option '$key' is not empty. This option should no longer be used.")
                    }
                }
            }
            try {
                val renderSettings = ConfigRenderOptions.defaults().setJson(false).setOriginComments(false)
                val nl = System.lineSeparator()
                val nle = StringEscapeUtils.escapeJava(nl)
                file.parentFile.mkdirs()
                val out = PrintWriter(file)
                out.write(config.root().render(renderSettings).lines()
                    // Indent two spaces instead of four.
                    .map { line -> """^(\s*)""".toRegex().replace(line) { m -> m.groupValues[1].replace("  ", " ") } }
                    // Finalize the string.
                    .filter { it.isNotEmpty() }.joinToString(nl)
                    // Newline after values.
                    .replace(Regex("""((?:\s*#.*$nle)(?:\s*[^#\s].*$nle)+)"""), "$1$nl"))
                out.close()
            } catch (e: Exception) {
                OpenComputers.log.warn("Failed saving config.", e)
            }
        }

        private val fileringRulesPatchVersion: VersionRange = VersionRange.createFromVersionSpec("[0.0, 1.8.3)")

        /**
         * Checks the config version (i.e. the version of the mod the config was
         * created by) against the current version to see if some hard changes
         * were made. If so, the new default values are copied over.
         */
        private fun patchConfig(config: Config, defaults: Config): Config {
            val mod = Loader.instance().activeModContainer()!!
            val configVersion = DefaultArtifactVersion(if (config.hasPath(prefix + "version")) config.getString(prefix + "version") else "0.0.0")
            var patched = config
            if (configVersion.compareTo(mod.processedVersion) != 0) {
                OpenComputers.log.info("Updating config from version '${configVersion.versionString}' to '${defaults.getString(prefix + "version")}'.")
                patched = patched.withValue(prefix + "version", defaults.getValue(prefix + "version"))

                // Usage: VersionRange.createFromVersionSpec("[0.0,1.5)") -> Array("computer.ramSizes") will
                // re-set the value of `computer.ramSizes` if a config saved with a version < 1.5 is loaded.
                fun patch(spec: String, vararg paths: String) {
                    val version = VersionRange.createFromVersionSpec(spec)
                    if (!version.containsVersion(configVersion)) return
                    for (path in paths) {
                        val fullPath = prefix + path
                        OpenComputers.log.info("=> Updating setting '$fullPath'. ")
                        patched = if (defaults.hasPath(fullPath)) {
                            patched.withValue(fullPath, defaults.getValue(fullPath))
                        } else {
                            patched.withoutPath(fullPath)
                        }
                    }
                }
                // Upgrading to version 1.5.20, changed relay delay default.
                patch("[0.0, 1.5.20)", "switch.relayDelayUpgrade")
                // Potion whitelist was fixed in 1.6.2.
                patch("[0.0, 1.6.2)", "nanomachines.potionWhitelist")
                // Upgrading past version 1.7.1, changed wireless card stuff for t1 card.
                patch("[0.0, 1.7.2)",
                    "power.cost.wirelessCostPerRange",
                    "misc.maxWirelessRange",
                    "misc.maxOpenPorts",
                    "computer.cpuComponentCount"
                )
                // Upgrading to version 1.8.0, changed meaning of limitFlightHeight value,
                patch("[0.0, 1.8.0)", "computer.robot.limitFlightHeight")

                // Migrate filtering rules to 1.8.3+
                if (fileringRulesPatchVersion.containsVersion(configVersion)) {
                    OpenComputers.log.info("=> Migrating Internet Card filtering rules. ")
                    val cidrPattern = """(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})(?:/(\d{1,2}))""".toRegex()
                    val httpHostWhitelist = patched.getStringList(prefix + "internet.whitelist")
                    val httpHostBlacklist = patched.getStringList(prefix + "internet.blacklist")
                    val internetFilteringRules = mutableListOf<String>()
                    for (blockedAddress in httpHostBlacklist) {
                        if (cidrPattern.containsMatchIn(blockedAddress)) {
                            internetFilteringRules += "deny ip:$blockedAddress"
                        } else {
                            internetFilteringRules += "deny domain:$blockedAddress"
                        }
                    }
                    for (allowedAddress in httpHostWhitelist) {
                        if (cidrPattern.containsMatchIn(allowedAddress)) {
                            internetFilteringRules += "allow ip:$allowedAddress"
                        } else {
                            internetFilteringRules += "allow domain:$allowedAddress"
                        }
                    }
                    if (httpHostWhitelist.isNotEmpty()) {
                        internetFilteringRules += "deny all"
                    }
                    for (defaultRule in defaults.getStringList(prefix + "internet.filteringRules")) {
                        internetFilteringRules += defaultRule
                    }
                    var patchedRules: ConfigValue = ConfigValueFactory.fromIterable(internetFilteringRules)
                    // We need to use the private APIs here, unfortunately.
                    try {
                        for (key in listOf("internet.whitelist", "internet.blacklist")) {
                            if (patched.hasPath(prefix + key)) {
                                val originalValue = patched.getValue(prefix + key)
                                var deprecatedValue: ConfigValue = ConfigValueFactory.fromIterable(ArrayList<String>(), originalValue.origin().description())
                                val comments = mutableListOf("No longer used! See internet.filteringRules.", "", "Previous contents:")
                                for (value in patched.getStringList(prefix + key)) {
                                    comments += "\"$value\""
                                }
                                deprecatedValue = OpenComputersConfigCommentManipulationHook.setComments(deprecatedValue, comments)
                                patched = patched.withValue(prefix + key, deprecatedValue)
                            }
                        }
                        patchedRules = OpenComputersConfigCommentManipulationHook.setComments(
                            patchedRules, defaults.getValue(prefix + "internet.filteringRules").origin().comments()
                        )
                    } catch (_: Throwable) {
                        /* pass */
                    }
                    patched = patched.withValue(prefix + "internet.filteringRules", patchedRules)
                }
            }
            return patched
        }

        @JvmStatic
        fun getIntList(config: Config, path: String, default: MutableList<Int>? = null): MutableList<Int> {
            return if (config.hasPath(path)) {
                config.getIntList(path).map { it.toInt() }.toMutableList()
            } else {
                default ?: mutableListOf()
            }
        }
    }

    sealed class DebugCardAccess {
        abstract fun checkAccess(ctx: DebugCard.AccessContext?): String?

        companion object {
            fun parse(filter: Any): DebugCardAccess {
                return when (filter) {
                    "true", "allow", true -> DebugCardAccess.Allowed
                    "false", "deny", false -> DebugCardAccess.Forbidden
                    "whitelist" -> {
                        val wlFile = File(Loader.instance().configDir.toString() + File.separator + "opencomputers" + File.separator +
                                "debug_card_whitelist.txt")
                        DebugCardAccess.Whitelist(wlFile)
                    }
                    else -> {
                        // Fallback to most secure configuration
                        OpenComputers.log.warn("Unknown debug card access type, falling back to `deny`. Allowed values: `allow`, `deny`, `whitelist`.")
                        DebugCardAccess.Forbidden
                    }
                }
            }
        }
        object Forbidden : DebugCardAccess() {
            override fun checkAccess(ctx: DebugCard.AccessContext?): String = "debug card is disabled"
        }

        object Allowed : DebugCardAccess() {
            override fun checkAccess(ctx: DebugCard.AccessContext?): String? = null
        }

        class Whitelist(private val noncesFile: File) : DebugCardAccess() {
            private val values = mutableMapOf<String, String>()
            private val rng: SecureRandom = SecureRandom.getInstance("SHA1PRNG")

            init {
                load()
            }

            fun save() {
                val noncesDir = noncesFile.parentFile
                if (!noncesDir.exists() && !noncesDir.mkdirs())
                    throw IOException("Cannot create nonces directory: ${noncesDir.canonicalPath}")

                val writer = PrintWriter(OutputStreamWriter(FileOutputStream(noncesFile), StandardCharsets.UTF_8), false)
                try {
                    for ((p, n) in values)
                        writer.println("$p $n")
                } finally {
                    writer.close()
                }
            }

            fun load() {
                values.clear()

                if (!noncesFile.exists())
                    return

                val reader = BufferedReader(InputStreamReader(FileInputStream(noncesFile), StandardCharsets.UTF_8))
                generateSequence { reader.readLine() }
                    .map { it.split(" ", limit = 2) }
                    .filter { it.size == 2 }
                    .forEach { values[it[0]] = it[1] }
            }

            private fun generateNonce(): String {
                val buf = ByteArray(16)
                rng.nextBytes(buf)
                return String(Hex.encodeHex(buf, true))
            }

            fun nonce(player: String): String? = values[player.lowercase()]

            fun isWhitelisted(player: String): Boolean = values.containsKey(player.lowercase())

            val whitelist: Set<String> get() = values.keys

            fun add(player: String) {
                if (!values.containsKey(player.lowercase())) {
                    values[player.lowercase()] = generateNonce()
                    save()
                }
            }

            fun remove(player: String) {
                if (values.remove(player.lowercase()) != null)
                    save()
            }

            fun invalidate(player: String) {
                if (values.containsKey(player.lowercase())) {
                    values[player.lowercase()] = generateNonce()
                    save()
                }
            }

            override fun checkAccess(ctx: DebugCard.AccessContext?): String? {
                return if (ctx != null) {
                    val storedNonce = values[ctx.player.lowercase()]
                    when {
                        storedNonce == null -> "you are not whitelisted to use debug card"
                        storedNonce == ctx.nonce -> null
                        else -> "debug card is invalidated, please re-bind it to yourself"
                    }
                } else {
                    "debug card is whitelisted, Shift+Click with it to bind card to yourself"
                }
            }
        }
    }
}
