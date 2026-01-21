package li.cil.oc

import com.mojang.authlib.GameProfile
import com.typesafe.config.*
import com.typesafe.config.impl.OpenComputersConfigCommentManipulationHook
import li.cil.oc.common.Tier
import li.cil.oc.server.component.DebugCard
import li.cil.oc.util.InternetFilteringRule
import li.cil.oc.api.internal.TextBuffer.ColorDepth
import org.apache.commons.codec.binary.Hex
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.versioning.DefaultArtifactVersion
import net.minecraftforge.fml.common.versioning.VersionRange
import org.apache.commons.lang3.StringEscapeUtils
import java.io.*
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.*
import kotlin.math.max

@Suppress("unused")
class Settings(val config: Config) {
    // ----------------------------------------------------------------------- //
    // client
    val screenTextFadeStartDistance: Double = config.getDouble("client.screenTextFadeStartDistance")
    val maxScreenTextRenderDistance: Double = config.getDouble("client.maxScreenTextRenderDistance")
    val textLinearFiltering: Boolean = config.getBoolean("client.textLinearFiltering")
    val textAntiAlias: Boolean = config.getBoolean("client.textAntiAlias")
    val robotLabels: Boolean = config.getBoolean("client.robotLabels")
    val soundVolume: Float = config.getDouble("client.soundVolume").toFloat().coerceIn(0f, 2f)
    val fontCharScale: Double = config.getDouble("client.fontCharScale").coerceIn(0.5, 2.0)
    val hologramFadeStartDistance: Double = max(config.getDouble("client.hologramFadeStartDistance"), 0.0)
    val hologramRenderDistance: Double = max(config.getDouble("client.hologramRenderDistance"), 0.0)
    val hologramFlickerFrequency: Double = max(config.getDouble("client.hologramFlickerFrequency"), 0.0)
    val monochromeColor: Int = Integer.decode(config.getString("client.monochromeColor"))
    val fontRenderer: String = config.getString("client.fontRenderer")
    val beepSampleRate: Int = config.getInt("client.beepSampleRate")
    val beepAmplitude: Int = config.getInt("client.beepVolume").coerceIn(0, Byte.MAX_VALUE.toInt())
    val beepRadius: Float = config.getDouble("client.beepRadius").toFloat().coerceIn(1f, 32f)
    val nanomachineHudPos: Pair<Double, Double> = run {
        val list = config.getDoubleList("client.nanomachineHudPos")
        if (list.size == 2) {
            list[0] to list[1]
        } else {
            OpenComputers.log.warn("Bad number of HUD coordiantes, ignoring.")
            -1.0 to -1.0
        }
    }
    val enableNanomachinePfx: Boolean = config.getBoolean("client.enableNanomachinePfx")
    val transposerFluidTransferRate: Int = config.getInt("misc.transposerFluidTransferRate")

    // ----------------------------------------------------------------------- //
    // computer
    val threads: Int = max(config.getInt("computer.threads"), 1)
    val timeout: Double = max(config.getDouble("computer.timeout"), 0.0)
    val startupDelay: Double = max(config.getDouble("computer.startupDelay"), 0.05)
    val eepromSize: Int = max(config.getInt("computer.eepromSize"), 0)
    val eepromDataSize: Int = max(config.getInt("computer.eepromDataSize"), 0)
    val cpuComponentSupport: Array<Int> = run {
        val list = config.getIntList("computer.cpuComponentCount")
        if (list.size == 4) {
            arrayOf(list[0], list[1], list[2], list[3])
        } else {
            OpenComputers.log.warn("Bad number of CPU component counts, ignoring.")
            arrayOf(8, 12, 16, 1024)
        }
    }
    val callBudgets: Array<Double> = run {
        val list = config.getDoubleList("computer.callBudgets")
        if (list.size == 3) {
            arrayOf(list[0], list[1], list[2])
        } else {
            OpenComputers.log.warn("Bad number of call budgets, ignoring.")
            arrayOf(0.5, 1.0, 1.5)
        }
    }
    val canComputersBeOwned: Boolean = config.getBoolean("computer.canComputersBeOwned")
    val maxUsers: Int = max(config.getInt("computer.maxUsers"), 0)
    val maxUsernameLength: Int = max(config.getInt("computer.maxUsernameLength"), 0)
    val eraseTmpOnReboot: Boolean = config.getBoolean("computer.eraseTmpOnReboot")
    val executionDelay: Int = max(config.getInt("computer.executionDelay"), 0)

    // computer.lua
    val allowBytecode: Boolean = config.getBoolean("computer.lua.allowBytecode")
    val allowGC: Boolean = config.getBoolean("computer.lua.allowGC")
    val enableLua53: Boolean = config.getBoolean("computer.lua.enableLua53")
    val defaultLua53: Boolean = config.getBoolean("computer.lua.defaultLua53")
    val enableLua54: Boolean = config.getBoolean("computer.lua.enableLua54")
    val ramSizes: Array<Int> = run {
        val list = config.getIntList("computer.lua.ramSizes")
        if (list.size == 6) {
            arrayOf(list[0], list[1], list[2], list[3], list[4], list[5])
        } else {
            OpenComputers.log.warn("Bad number of RAM sizes, ignoring.")
            arrayOf(192, 256, 384, 512, 768, 1024)
        }
    }
    val ramScaleFor64Bit: Double = max(config.getDouble("computer.lua.ramScaleFor64Bit"), 1.0)
    val maxTotalRam: Int = max(config.getInt("computer.lua.maxTotalRam"), 0)

    // ----------------------------------------------------------------------- //
    // robot
    val allowActivateBlocks: Boolean = config.getBoolean("robot.allowActivateBlocks")
    val allowUseItemsWithDuration: Boolean = config.getBoolean("robot.allowUseItemsWithDuration")
    val canAttackPlayers: Boolean = config.getBoolean("robot.canAttackPlayers")
    val limitFlightHeight: Int = max(config.getInt("robot.limitFlightHeight"), -1)
    val screwCobwebs: Boolean = config.getBoolean("robot.notAfraidOfSpiders")
    val swingRange: Double = config.getDouble("robot.swingRange")
    val useAndPlaceRange: Double = config.getDouble("robot.useAndPlaceRange")
    val itemDamageRate: Double = config.getDouble("robot.itemDamageRate").coerceIn(0.0, 1.0)
    val nameFormat: String = config.getString("robot.nameFormat")
    val uuidFormat: String = config.getString("robot.uuidFormat")
    val upgradeFlightHeight: Array<Int> = run {
        val list = config.getIntList("robot.upgradeFlightHeight")
        if (list.size == 2) {
            arrayOf(list[0], list[1])
        } else {
            OpenComputers.log.warn("Bad number of hover flight height counts, ignoring.")
            arrayOf(64, 256)
        }
    }

    // robot.xp
    val baseXpToLevel: Double = max(config.getDouble("robot.xp.baseValue"), 0.0)
    val constantXpGrowth: Double = max(config.getDouble("robot.xp.constantGrowth"), 1.0)
    val exponentialXpGrowth: Double = max(config.getDouble("robot.xp.exponentialGrowth"), 1.0)
    val robotActionXp: Double = max(config.getDouble("robot.xp.actionXp"), 0.0)
    val robotExhaustionXpRate: Double = max(config.getDouble("robot.xp.exhaustionXpRate"), 0.0)
    val robotOreXpRate: Double = max(config.getDouble("robot.xp.oreXpRate"), 0.0)
    val bufferPerLevel: Double = max(config.getDouble("robot.xp.bufferPerLevel"), 0.0)
    val toolEfficiencyPerLevel: Double = max(config.getDouble("robot.xp.toolEfficiencyPerLevel"), 0.0)
    val harvestSpeedBoostPerLevel: Double = max(config.getDouble("robot.xp.harvestSpeedBoostPerLevel"), 0.0)

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
    val harvestRatio: Double = max(config.getDouble("robot.delays.harvestRatio"), 0.0)

    // ----------------------------------------------------------------------- //
    // power
    val ignorePower: Boolean = config.getBoolean("power.ignorePower")
    val tickFrequency: Double = max(config.getDouble("power.tickFrequency"), 1.0)
    val chargeRateExternal: Double = config.getDouble("power.chargerChargeRate")
    val chargeRateTablet: Double = config.getDouble("power.chargerChargeRateTablet")
    val generatorEfficiency: Double = config.getDouble("power.generatorEfficiency")
    val solarGeneratorEfficiency: Double = config.getDouble("power.solarGeneratorEfficiency")
    val assemblerTickAmount: Double = max(config.getDouble("power.assemblerTickAmount"), 1.0)
    val disassemblerTickAmount: Double = max(config.getDouble("power.disassemblerTickAmount"), 1.0)
    val printerTickAmount: Double = max(config.getDouble("power.printerTickAmount"), 1.0)
    val powerModBlacklist: MutableList<String> = config.getStringList("power.modBlacklist")

    // power.carpetedCapacitors
    val sheepPower: Double = max(config.getDouble("power.carpetedCapacitors.sheepPower"), 0.0)
    val ocelotPower: Double = max(config.getDouble("power.carpetedCapacitors.ocelotPower"), 0.0)
    val carpetDamageChance: Double = config.getDouble("power.carpetedCapacitors.damageChance").coerceIn(0.0, 1.0)

    // power.buffer
    val bufferCapacitor: Double = max(config.getDouble("power.buffer.capacitor"), 0.0)
    val bufferCapacitorAdjacencyBonus: Double = max(config.getDouble("power.buffer.capacitorAdjacencyBonus"), 0.0)
    val bufferComputer: Double = max(config.getDouble("power.buffer.computer"), 0.0)
    val bufferRobot: Double = max(config.getDouble("power.buffer.robot"), 0.0)
    val bufferConverter: Double = max(config.getDouble("power.buffer.converter"), 0.0)
    val bufferDistributor: Double = max(config.getDouble("power.buffer.distributor"), 0.0)
    val bufferCapacitorUpgrades: Array<Double> = run {
        val list = config.getDoubleList("power.buffer.batteryUpgrades")
        if (list.size == 3) {
            arrayOf(list[0], list[1], list[2])
        } else {
            OpenComputers.log.warn("Bad number of battery upgrade buffer sizes, ignoring.")
            arrayOf(10000.0, 15000.0, 20000.0)
        }
    }
    val bufferTablet: Double = max(config.getDouble("power.buffer.tablet"), 0.0)
    val bufferAccessPoint: Double = max(config.getDouble("power.buffer.accessPoint"), 0.0)
    val bufferDrone: Double = max(config.getDouble("power.buffer.drone"), 0.0)
    val bufferMicrocontroller: Double = max(config.getDouble("power.buffer.mcu"), 0.0)
    val bufferHoverBoots: Double = max(config.getDouble("power.buffer.hoverBoots"), 1.0)
    val bufferNanomachines: Double = max(config.getDouble("power.buffer.nanomachines"), 0.0)

    // power.cost
    val computerCost: Double = max(config.getDouble("power.cost.computer"), 0.0)
    val microcontrollerCost: Double = max(config.getDouble("power.cost.microcontroller"), 0.0)
    val robotCost: Double = max(config.getDouble("power.cost.robot"), 0.0)
    val droneCost: Double = max(config.getDouble("power.cost.drone"), 0.0)
    val sleepCostFactor: Double = max(config.getDouble("power.cost.sleepFactor"), 0.0)
    val screenCost: Double = max(config.getDouble("power.cost.screen"), 0.0)
    val hologramCost: Double = max(config.getDouble("power.cost.hologram"), 0.0)
    val hddReadCost: Double = max(config.getDouble("power.cost.hddRead"), 0.0) / 1024
    val hddWriteCost: Double = max(config.getDouble("power.cost.hddWrite"), 0.0) / 1024
    val gpuSetCost: Double = max(config.getDouble("power.cost.gpuSet"), 0.0) / basicScreenPixels
    val gpuFillCost: Double = max(config.getDouble("power.cost.gpuFill"), 0.0) / basicScreenPixels
    val gpuClearCost: Double = max(config.getDouble("power.cost.gpuClear"), 0.0) / basicScreenPixels
    val gpuCopyCost: Double = max(config.getDouble("power.cost.gpuCopy"), 0.0) / basicScreenPixels
    val robotTurnCost: Double = max(config.getDouble("power.cost.robotTurn"), 0.0)
    val robotMoveCost: Double = max(config.getDouble("power.cost.robotMove"), 0.0)
    val robotExhaustionCost: Double = max(config.getDouble("power.cost.robotExhaustion"), 0.0)
    val wirelessCostPerRange: Array<Double> = run {
        val list = config.getDoubleList("power.cost.wirelessCostPerRange")
        if (list.size == 2) {
            arrayOf(max(list[0], 0.0), max(list[1], 0.0))
        } else {
            OpenComputers.log.warn("Bad number of wireless card energy costs, ignoring.")
            arrayOf(0.05, 0.05)
        }
    }
    val abstractBusPacketCost: Double = max(config.getDouble("power.cost.abstractBusPacket"), 0.0)
    val geolyzerScanCost: Double = max(config.getDouble("power.cost.geolyzerScan"), 0.0)
    val robotBaseCost: Double = max(config.getDouble("power.cost.robotAssemblyBase"), 0.0)
    val robotComplexityCost: Double = max(config.getDouble("power.cost.robotAssemblyComplexity"), 0.0)
    val microcontrollerBaseCost: Double = max(config.getDouble("power.cost.microcontrollerAssemblyBase"), 0.0)
    val microcontrollerComplexityCost: Double = max(config.getDouble("power.cost.microcontrollerAssemblyComplexity"), 0.0)
    val tabletBaseCost: Double = max(config.getDouble("power.cost.tabletAssemblyBase"), 0.0)
    val tabletComplexityCost: Double = max(config.getDouble("power.cost.tabletAssemblyComplexity"), 0.0)
    val droneBaseCost: Double = max(config.getDouble("power.cost.droneAssemblyBase"), 0.0)
    val droneComplexityCost: Double = max(config.getDouble("power.cost.droneAssemblyComplexity"), 0.0)
    val disassemblerItemCost: Double = max(config.getDouble("power.cost.disassemblerPerItem"), 0.0)
    val chunkloaderCost: Double = max(config.getDouble("power.cost.chunkloaderCost"), 0.0)
    val pistonCost: Double = max(config.getDouble("power.cost.pistonPush"), 0.0)
    val eepromWriteCost: Double = max(config.getDouble("power.cost.eepromWrite"), 0.0)
    val printCost: Double = max(config.getDouble("power.cost.printerModel"), 0.0)
    val hoverBootJump: Double = max(config.getDouble("power.cost.hoverBootJump"), 0.0)
    val hoverBootAbsorb: Double = max(config.getDouble("power.cost.hoverBootAbsorb"), 0.0)
    val hoverBootMove: Double = max(config.getDouble("power.cost.hoverBootMove"), 0.0)
    val dataCardTrivial: Double = max(config.getDouble("power.cost.dataCardTrivial"), 0.0)
    val dataCardTrivialByte: Double = max(config.getDouble("power.cost.dataCardTrivialByte"), 0.0)
    val dataCardSimple: Double = max(config.getDouble("power.cost.dataCardSimple"), 0.0)
    val dataCardSimpleByte: Double = max(config.getDouble("power.cost.dataCardSimpleByte"), 0.0)
    val dataCardComplex: Double = max(config.getDouble("power.cost.dataCardComplex"), 0.0)
    val dataCardComplexByte: Double = max(config.getDouble("power.cost.dataCardComplexByte"), 0.0)
    val dataCardAsymmetric: Double = max(config.getDouble("power.cost.dataCardAsymmetric"), 0.0)
    val transposerCost: Double = max(config.getDouble("power.cost.transposer"), 0.0)
    val nanomachineCost: Double = max(config.getDouble("power.cost.nanomachineInput"), 0.0)
    val nanomachineReconfigureCost: Double = max(config.getDouble("power.cost.nanomachinesReconfigure"), 0.0)
    val mfuCost: Double = max(config.getDouble("power.cost.mfuRelay"), 0.0)

    // power.rate
    val accessPointRate: Double = max(config.getDouble("power.rate.accessPoint"), 0.0)
    val assemblerRate: Double = max(config.getDouble("power.rate.assembler"), 0.0)
    val caseRate: Array<Double> = run {
        val list = config.getDoubleList("power.rate.case")
        val base = if (list.size == 3) {
            arrayOf(list[0], list[1], list[2])
        } else {
            OpenComputers.log.warn("Bad number of computer case conversion rates, ignoring.")
            arrayOf(5.0, 10.0, 20.0)
        }
        // Creative case.
        base + arrayOf(9001.0)
    }
    val chargerRate: Double = max(config.getDouble("power.rate.charger"), 0.0)
    val disassemblerRate: Double = max(config.getDouble("power.rate.disassembler"), 0.0)
    val powerConverterRate: Double = max(config.getDouble("power.rate.powerConverter"), 0.0)
    val serverRackRate: Double = max(config.getDouble("power.rate.serverRack"), 0.0)

    // power.value
    private val valueAppliedEnergistics2: Double = config.getDouble("power.value.AppliedEnergistics2")
    private val valueFactorization: Double = config.getDouble("power.value.Factorization")
    private val valueGalacticraft: Double = config.getDouble("power.value.Galacticraft")
    private val valueIndustrialCraft2: Double = config.getDouble("power.value.IndustrialCraft2")
    private val valueMekanism: Double = config.getDouble("power.value.Mekanism")
    private val valuePowerAdvantage: Double = config.getDouble("power.value.PowerAdvantage")
    private val valueRedstoneFlux: Double = config.getDouble("power.value.RedstoneFlux")
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
    val fileCost: Int = max(config.getInt("filesystem.fileCost"), 0)
    val bufferChanges: Boolean = config.getBoolean("filesystem.bufferChanges")
    val hddSizes: Array<Int> = run {
        val list = config.getIntList("filesystem.hddSizes")
        if (list.size == 3) {
            arrayOf(list[0], list[1], list[2])
        } else {
            OpenComputers.log.warn("Bad number of HDD sizes, ignoring.")
            arrayOf(1024, 2048, 4096)
        }
    }
    val hddPlatterCounts: Array<Int> = run {
        val list = config.getIntList("filesystem.hddPlatterCounts")
        if (list.size == 3) {
            arrayOf(list[0], list[1], list[2])
        } else {
            OpenComputers.log.warn("Bad number of HDD platter counts, ignoring.")
            arrayOf(2, 4, 6)
        }
    }
    val floppySize: Int = max(config.getInt("filesystem.floppySize"), 0)
    val tmpSize: Int = max(config.getInt("filesystem.tmpSize"), 0)
    val maxHandles: Int = max(config.getInt("filesystem.maxHandles"), 0)
    val maxReadBuffer: Int = max(config.getInt("filesystem.maxReadBuffer"), 0)
    val sectorSeekThreshold: Int = config.getInt("filesystem.sectorSeekThreshold")
    val sectorSeekTime: Double = config.getDouble("filesystem.sectorSeekTime")

    // ----------------------------------------------------------------------- //
    // internet
    val httpEnabled: Boolean = config.getBoolean("internet.enableHttp")
    val httpHeadersEnabled: Boolean = config.getBoolean("internet.enableHttpHeaders")
    val tcpEnabled: Boolean = config.getBoolean("internet.enableTcp")
    val internetFilteringRules: Array<InternetFilteringRule> = config.getStringList("internet.filteringRules")
        .filter { it != "removeme" }
        .map { InternetFilteringRule(it) }
        .toTypedArray()
    val internetFilteringRulesObserved: Boolean = !config.getStringList("internet.filteringRules")
        .contains("removeme")
    val httpTimeout: Int = max(config.getInt("internet.requestTimeout"), 0) * 1000
    val maxConnections: Int = max(config.getInt("internet.maxTcpConnections"), 0)
    val internetThreads: Int = max(config.getInt("internet.threads"), 1)

    // ----------------------------------------------------------------------- //
    // switch
    val switchDefaultMaxQueueSize: Int = max(config.getInt("switch.defaultMaxQueueSize"), 1)
    val switchQueueSizeUpgrade: Int = max(config.getInt("switch.queueSizeUpgrade"), 0)
    val switchDefaultRelayDelay: Int = max(config.getInt("switch.defaultRelayDelay"), 1)
    val switchRelayDelayUpgrade: Double = max(config.getDouble("switch.relayDelayUpgrade"), 0.0)
    val switchDefaultRelayAmount: Int = max(config.getInt("switch.defaultRelayAmount"), 1)
    val switchRelayAmountUpgrade: Int = max(config.getInt("switch.relayAmountUpgrade"), 0)

    // ----------------------------------------------------------------------- //
    // hologram
    val hologramMaxScaleByTier: Array<Double> = run {
        val list = config.getDoubleList("hologram.maxScale")
        if (list.size == 2) {
            arrayOf(max(list[0], 1.0), max(list[1], 1.0))
        } else {
            OpenComputers.log.warn("Bad number of hologram max scales, ignoring.")
            arrayOf(3.0, 4.0)
        }
    }
    val hologramMaxTranslationByTier: Array<Double> = run {
        val list = config.getDoubleList("hologram.maxTranslation")
        if (list.size == 2) {
            arrayOf(max(list[0], 0.0), max(list[1], 0.0))
        } else {
            OpenComputers.log.warn("Bad number of hologram max translations, ignoring.")
            arrayOf(0.25, 0.5)
        }
    }
    val hologramSetRawDelay: Double = max(config.getDouble("hologram.setRawDelay"), 0.0)
    val hologramLight: Boolean = config.getBoolean("hologram.emitLight")

    // ----------------------------------------------------------------------- //
    // misc
    val maxScreenWidth: Int = max(config.getInt("misc.maxScreenWidth"), 1)
    val maxScreenHeight: Int = max(config.getInt("misc.maxScreenHeight"), 1)
    val inputUsername: Boolean = config.getBoolean("misc.inputUsername")
    val initialNetworkPacketTTL: Int = max(config.getInt("misc.initialNetworkPacketTTL"), 5)
    val maxNetworkPacketSize: Int = max(config.getInt("misc.maxNetworkPacketSize"), 0)
    // Need at least 4 for nanomachine protocol. Because I can!
    val maxNetworkPacketParts: Int = max(config.getInt("misc.maxNetworkPacketParts"), 4)
    val maxOpenPorts: Array<Int> = run {
        val list = config.getIntList("misc.maxOpenPorts")
        if (list.size == 3) {
            arrayOf(max(list[0], 0), max(list[1], 0), max(list[2], 0))
        } else {
            OpenComputers.log.warn("Bad number of max open ports, ignoring.")
            arrayOf(16, 1, 16)
        }
    }
    val maxWirelessRange: Array<Double> = run {
        val list = config.getDoubleList("misc.maxWirelessRange")
        if (list.size == 2) {
            arrayOf(max(list[0], 0.0), max(list[1], 0.0))
        } else {
            OpenComputers.log.warn("Bad number of wireless card max ranges, ignoring.")
            arrayOf(16.0, 400.0)
        }
    }
    val rTreeMaxEntries: Int = 10
    val terminalsPerServer: Int = 4
    val updateCheck: Boolean = config.getBoolean("misc.updateCheck")
    val lootProbability: Int = config.getInt("misc.lootProbability")
    val lootRecrafting: Boolean = config.getBoolean("misc.lootRecrafting")
    val geolyzerRange: Int = config.getInt("misc.geolyzerRange")
    val geolyzerNoise: Float = max(config.getDouble("misc.geolyzerNoise").toFloat(), 0f)
    val disassembleAllTheThings: Boolean = config.getBoolean("misc.disassembleAllTheThings")
    val disassemblerBreakChance: Double = config.getDouble("misc.disassemblerBreakChance").coerceIn(0.0, 1.0)
    val disassemblerInputBlacklist: MutableList<String> = config.getStringList("misc.disassemblerInputBlacklist")
    val hideOwnPet: Boolean = config.getBoolean("misc.hideOwnSpecial")
    val allowItemStackInspection: Boolean = config.getBoolean("misc.allowItemStackInspection")
    val databaseEntriesPerTier: Array<Int> = arrayOf(9, 25, 81)
    // Not configurable because of GUI design.
    val presentChance: Double = config.getDouble("misc.presentChance").coerceIn(0.0, 1.0)
    val assemblerBlacklist: MutableList<String> = config.getStringList("misc.assemblerBlacklist")
    val threadPriority: Int = config.getInt("misc.threadPriority")
    val giveManualToNewPlayers: Boolean = config.getBoolean("misc.giveManualToNewPlayers")
    val dataCardSoftLimit: Int = max(config.getInt("misc.dataCardSoftLimit"), 0)
    val dataCardHardLimit: Int = max(config.getInt("misc.dataCardHardLimit"), 0)
    val dataCardTimeout: Double = max(config.getDouble("misc.dataCardTimeout"), 0.0)
    val serverRackSwitchTier: Int = (config.getInt("misc.serverRackSwitchTier") - 1).coerceIn(Tier.None, Tier.Three)
    val redstoneDelay: Double = max(config.getDouble("misc.redstoneDelay"), 0.0)
    val tradingRange: Double = max(config.getDouble("misc.tradingRange"), 0.0)
    val mfuRange: Int = config.getInt("misc.mfuRange").coerceIn(0, 128)

    // ----------------------------------------------------------------------- //
    // nanomachines
    val nanomachineTriggerQuota: Double = max(config.getDouble("nanomachines.triggerQuota"), 0.0)
    val nanomachineConnectorQuota: Double = max(config.getDouble("nanomachines.connectorQuota"), 0.0)
    val nanomachineMaxInputs: Int = max(config.getInt("nanomachines.maxInputs"), 1)
    val nanomachineMaxOutputs: Int = max(config.getInt("nanomachines.maxOutputs"), 1)
    val nanomachinesSafeInputsActive: Int = max(config.getInt("nanomachines.safeInputsActive"), 0)
    val nanomachinesMaxInputsActive: Int = max(config.getInt("nanomachines.maxInputsActive"), 0)
    val nanomachinesCommandDelay: Double = max(config.getDouble("nanomachines.commandDelay"), 0.0)
    val nanomachinesCommandRange: Double = max(config.getDouble("nanomachines.commandRange"), 0.0)
    val nanomachineMagnetRange: Double = max(config.getDouble("nanomachines.magnetRange"), 0.0)
    val nanomachineDisintegrationRange: Int = max(config.getInt("nanomachines.disintegrationRange"), 0)
    val nanomachinePotionWhitelist: List<Any> = config.getAnyRefList("nanomachines.potionWhitelist")
    val nanomachinesHungryDamage: Float = max(config.getDouble("nanomachines.hungryDamage").toFloat(), 0f)
    val nanomachinesHungryEnergyRestored: Double = max(config.getDouble("nanomachines.hungryEnergyRestored"), 0.0)

    // ----------------------------------------------------------------------- //
    // printer
    val maxPrintComplexity: Int = config.getInt("printer.maxShapes")
    val printRecycleRate: Double = config.getDouble("printer.recycleRate")
    val chameliumEdible: Boolean = config.getBoolean("printer.chameliumEdible")
    val maxPrintLightLevel: Int = config.getInt("printer.maxBaseLightLevel").coerceIn(0, 15)
    val printCustomRedstone: Int = max(config.getInt("printer.customRedstoneCost"), 0)
    val printMaterialValue: Int = max(config.getInt("printer.materialValue"), 0)
    val printInkValue: Int = max(config.getInt("printer.inkValue"), 0)
    val printsHaveOpacity: Boolean = config.getBoolean("printer.printsHaveOpacity")
    val noclipMultiplier: Double = max(config.getDouble("printer.noclipMultiplier"), 0.0)

    // chunkloader
    val chunkloadDimensionBlacklist: MutableList<Int> = getIntList(config, "chunkloader.dimBlacklist")
    val chunkloadDimensionWhitelist: MutableList<Int> = getIntList(config, "chunkloader.dimWhitelist")

    // ----------------------------------------------------------------------- //
    // integration
    val modBlacklist: MutableList<String> = config.getStringList("integration.modBlacklist")
    val peripheralBlacklist: MutableList<String> = config.getStringList("integration.peripheralBlacklist")
    val fakePlayerUuid: String = config.getString("integration.fakePlayerUuid")
    val fakePlayerName: String = config.getString("integration.fakePlayerName")
    val fakePlayerProfile: GameProfile = GameProfile(UUID.fromString(fakePlayerUuid), fakePlayerName)

    // integration.vanilla
    val enableInventoryDriver: Boolean = config.getBoolean("integration.vanilla.enableInventoryDriver")
    val enableTankDriver: Boolean = config.getBoolean("integration.vanilla.enableTankDriver")
    val enableCommandBlockDriver: Boolean = config.getBoolean("integration.vanilla.enableCommandBlockDriver")
    val allowItemStackNBTTags: Boolean = config.getBoolean("integration.vanilla.allowItemStackNBTTags")

    // integration.buildcraft
    val costProgrammingTable: Double = max(config.getDouble("integration.buildcraft.programmingTableCost"), 0.0)

    // ----------------------------------------------------------------------- //
    // debug
    val logLuaCallbackErrors: Boolean = config.getBoolean("debug.logCallbackErrors")
    val forceLuaJ: Boolean = config.getBoolean("debug.forceLuaJ")
    val allowUserdata: Boolean = !config.getBoolean("debug.disableUserdata")
    val allowPersistence: Boolean = !config.getBoolean("debug.disablePersistence")
    val limitMemory: Boolean = !config.getBoolean("debug.disableMemoryLimit")
    val forceCaseInsensitive: Boolean = config.getBoolean("debug.forceCaseInsensitiveFS")
    val logFullLibLoadErrors: Boolean = config.getBoolean("debug.logFullNativeLibLoadErrors")
    val forceNativeLibPlatform: String = config.getString("debug.forceNativeLibPlatform")
    val forceNativeLibPathFirst: String = config.getString("debug.forceNativeLibPathFirst")
    val logOpenGLErrors: Boolean = config.getBoolean("debug.logOpenGLErrors")
    val logHexFontErrors: Boolean = config.getBoolean("debug.logHexFontErrors")
    val alwaysTryNative: Boolean = config.getBoolean("debug.alwaysTryNative")
    val debugPersistence: Boolean = config.getBoolean("debug.verbosePersistenceErrors")
    val nativeInTmpDir: Boolean = config.getBoolean("debug.nativeInTmpDir")
    val periodicallyForceLightUpdate: Boolean = config.getBoolean("debug.periodicallyForceLightUpdate")
    val insertIdsInConverters: Boolean = config.getBoolean("debug.insertIdsInConverters")

    val debugCardAccess: DebugCardAccess = run {
        when (config.getValue("debug.debugCardAccess").unwrapped()) {
            "true", "allow", java.lang.Boolean.TRUE -> DebugCardAccess.Allowed
            "false", "deny", java.lang.Boolean.FALSE -> DebugCardAccess.Forbidden
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

    val registerLuaJArchitecture: Boolean = config.getBoolean("debug.registerLuaJArchitecture")
    val disableLocaleChanging: Boolean = config.getBoolean("debug.disableLocaleChanging")

    // >= 1.7.4
    val maxSignalQueueSize: Int = max(if (config.hasPath("computer.maxSignalQueueSize")) config.getInt("computer.maxSignalQueueSize") else 256, 256)

    // >= 1.7.6
    val vramSizes: Array<Double> = run {
        val list = config.getDoubleList("gpu.vramSizes")
        if (list.size == 3) {
            arrayOf(list[0], list[1], list[2])
        } else {
            OpenComputers.log.warn("Bad number of VRAM sizes (expected 3), ignoring.")
            arrayOf(1.0, 2.0, 3.0)
        }
    }

    val bitbltCost: Double = if (config.hasPath("gpu.bitbltCost")) config.getDouble("gpu.bitbltCost") else 0.5

    // >= 1.8.2
    val diskActivitySoundDelay: Int = max(config.getInt("misc.diskActivitySoundDelay"), -1)
    val maxNetworkClientPacketDistance: Double = max(config.getDouble("misc.maxNetworkClientPacketDistance"), 0.0)
    val maxNetworkClientEffectPacketDistance: Double = max(config.getDouble("misc.maxNetworkClientEffectPacketDistance"), 0.0)
    val maxNetworkClientSoundPacketDistance: Double = max(config.getDouble("misc.maxNetworkClientSoundPacketDistance"), 0.0)

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
    val httpUserAgent: String = config.getString("internet.httpUserAgent")

    companion object {
        const val resourceDomain: String = "opencomputers"
        const val namespace: String = "oc:"
        const val savePath: String = "opencomputers/"
        const val scriptPath: String = "/assets/$resourceDomain/lua/"

        @JvmField
        val screenResolutionsByTier: Array<Pair<Int, Int>> = arrayOf(50 to 16, 80 to 25, 160 to 50)

        @JvmField
        val screenDepthsByTier: Array<ColorDepth> = arrayOf(
            ColorDepth.OneBit,
            ColorDepth.FourBit,
            ColorDepth.EightBit
        )

        @JvmField
        val deviceComplexityByTier: Array<Int> = arrayOf(12, 24, 32, 9001)

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
            get() = screenResolutionsByTier[0].first * screenResolutionsByTier[0].second

        private var settings: Settings? = null

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
            } catch (e: Throwable) {
                OpenComputers.log.warn("Failed saving config.", e)
            }
        }

        // Usage: VersionRange.createFromVersionSpec("[0.0,1.5)") -> Array("computer.ramSizes") will
        // re-set the value of `computer.ramSizes` if a config saved with a version < 1.5 is loaded.
        private val configPatches: Array<Pair<VersionRange, Array<String>>> = arrayOf(
            // Upgrading to version 1.5.20, changed relay delay default.
            VersionRange.createFromVersionSpec("[0.0, 1.5.20)") to arrayOf(
                "switch.relayDelayUpgrade"
            ),
            // Potion whitelist was fixed in 1.6.2.
            VersionRange.createFromVersionSpec("[0.0, 1.6.2)") to arrayOf(
                "nanomachines.potionWhitelist"
            ),
            // Upgrading past version 1.7.1, changed wireless card stuff for t1 card.
            VersionRange.createFromVersionSpec("[0.0, 1.7.2)") to arrayOf(
                "power.cost.wirelessCostPerRange",
                "misc.maxWirelessRange",
                "misc.maxOpenPorts",
                "computer.cpuComponentCount"
            ),
            // Upgrading to version 1.8.0, changed meaning of limitFlightHeight value,
            VersionRange.createFromVersionSpec("[0.0, 1.8.0)") to arrayOf(
                "computer.robot.limitFlightHeight"
            )
        )
        private val fileringRulesPatchVersion: VersionRange = VersionRange.createFromVersionSpec("[0.0, 1.8.3)")

        // Checks the config version (i.e. the version of the mod the config was
        // created by) against the current version to see if some hard changes
        // were made. If so, the new default values are copied over.
        private fun patchConfig(config: Config, defaults: Config): Config {
            val mod = Loader.instance().activeModContainer()!!
            val configVersion = DefaultArtifactVersion(if (config.hasPath(prefix + "version")) config.getString(prefix + "version") else "0.0.0")
            var patched = config
            if (configVersion.compareTo(mod.processedVersion) != 0) {
                OpenComputers.log.info("Updating config from version '${configVersion.versionString}' to '${defaults.getString(prefix + "version")}'.")
                patched = patched.withValue(prefix + "version", defaults.getValue(prefix + "version"))
                for ((version, paths) in configPatches) {
                    if (version.containsVersion(configVersion)) {
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
                }

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
