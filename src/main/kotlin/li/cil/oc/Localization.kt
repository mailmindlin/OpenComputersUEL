package li.cil.oc

import li.cil.oc.client.CommandHandler.SetClipboardCommand
import net.minecraft.util.text.ITextComponent
import net.minecraft.util.text.TextComponentString
import net.minecraft.util.text.TextComponentTranslation
import net.minecraft.util.text.event.ClickEvent
import net.minecraft.util.text.event.HoverEvent
import net.minecraft.util.text.translation.I18n
import net.minecraftforge.fml.common.event.FMLFingerprintViolationEvent

object Localization {
    private val nl = Regex.escape("[nl]")

    private fun resolveKey(key: String): String? {
        return when {
            canLocalize(Settings.namespace + key) -> Settings.namespace + key
            canLocalize(key) -> key
            else -> null
        }
    }

    @JvmStatic
    fun canLocalize(key: String): Boolean = I18n.canTranslate(key)

    @JvmStatic
    fun localizeLater(formatKey: String, vararg values: Any): ITextComponent =
        TextComponentTranslation(resolveKey(formatKey) ?: formatKey, *values)

    @JvmStatic
    fun localizeLater(key: String): ITextComponent {
        val resolved = resolveKey(key)
        return if (resolved != null) TextComponentTranslation(resolved) else TextComponentString(key)
    }

    @JvmStatic
    fun localizeImmediately(formatKey: String, vararg values: Any): String =
        I18n.translateToLocalFormatted(resolveKey(formatKey) ?: formatKey, *values)
            .split(nl.toRegex())
            .joinToString("\n") { it.trim() }

    @JvmStatic
    fun localizeImmediately(key: String): String {
        val resolved = resolveKey(key)
        val translated = if (resolved != null) I18n.translateToLocal(resolved) else key
        return translated.split(nl.toRegex()).joinToString("\n") { it.trim() }
    }

    object Analyzer {
        @JvmStatic
        fun Address(value: String): ITextComponent {
            val result = localizeLater("gui.Analyzer.Address", value)
            result.style.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/${SetClipboardCommand.name} $value")
            result.style.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, localizeLater("gui.Analyzer.CopyToClipboard"))
            return result
        }

        @JvmStatic
        fun AddressCopied(): ITextComponent = localizeLater("gui.Analyzer.AddressCopied")

        @JvmStatic
        fun ChargerSpeed(value: Double): ITextComponent = localizeLater("gui.Analyzer.ChargerSpeed", "${(value * 100).toInt()}%")

        @JvmStatic
        fun ComponentName(value: String): ITextComponent = localizeLater("gui.Analyzer.ComponentName", value)

        @JvmStatic
        fun Components(count: Int, maxCount: Int): ITextComponent = localizeLater("gui.Analyzer.Components", "$count/$maxCount")

        @JvmStatic
        fun LastError(value: String): ITextComponent = localizeLater("gui.Analyzer.LastError", localizeLater(value))

        @JvmStatic
        fun RobotOwner(owner: String): ITextComponent = localizeLater("gui.Analyzer.RobotOwner", owner)

        @JvmStatic
        fun RobotName(name: String): ITextComponent = localizeLater("gui.Analyzer.RobotName", name)

        @JvmStatic
        fun RobotXp(experience: Double, level: Int): ITextComponent = localizeLater("gui.Analyzer.RobotXp", "%.2f".format(experience), level.toString())

        @JvmStatic
        fun StoredEnergy(value: String): ITextComponent = localizeLater("gui.Analyzer.StoredEnergy", value)

        @JvmStatic
        fun TotalEnergy(value: String): ITextComponent = localizeLater("gui.Analyzer.TotalEnergy", value)

        @JvmStatic
        fun Users(list: Iterable<String>): ITextComponent = localizeLater("gui.Analyzer.Users", list.joinToString(", "))

        @JvmStatic
        fun WirelessStrength(value: Double): ITextComponent = localizeLater("gui.Analyzer.WirelessStrength", value.toInt().toString())
    }

    object Assembler {
        @JvmStatic
        fun InsertTemplate(): String = localizeImmediately("gui.Assembler.InsertCase")

        @JvmStatic
        fun CollectResult(): String = localizeImmediately("gui.Assembler.Collect")

        @JvmStatic
        fun InsertCPU(): ITextComponent = localizeLater("gui.Assembler.InsertCPU")

        @JvmStatic
        fun InsertRAM(): ITextComponent = localizeLater("gui.Assembler.InsertRAM")

        @JvmStatic
        fun Complexity(complexity: Int, maxComplexity: Int): ITextComponent {
            val message = localizeLater("gui.Assembler.Complexity", complexity.toString(), maxComplexity.toString())
            return if (complexity > maxComplexity) TextComponentString("§4").appendSibling(message)
            else message
        }

        @JvmStatic
        fun Run(): String = localizeImmediately("gui.Assembler.Run")

        @JvmStatic
        fun Progress(progress: Double, timeRemaining: String): String = localizeImmediately("gui.Assembler.Progress", progress.toInt().toString(), timeRemaining)

        @JvmStatic
        fun Warning(name: String): ITextComponent = TextComponentString("§7- ").appendSibling(localizeLater("gui.Assembler.Warning.$name"))

        @JvmStatic
        fun Warnings(): ITextComponent = localizeLater("gui.Assembler.Warnings")
    }

    object Chat {
        @JvmStatic
        fun WarningLuaFallback(): ITextComponent = TextComponentString("§aOpenComputers§f: ").appendSibling(localizeLater("gui.Chat.WarningLuaFallback"))

        @JvmStatic
        fun WarningProjectRed(): ITextComponent = TextComponentString("§aOpenComputers§f: ").appendSibling(localizeLater("gui.Chat.WarningProjectRed"))

        @JvmStatic
        fun WarningFingerprint(event: FMLFingerprintViolationEvent): ITextComponent = TextComponentString("§aOpenComputers§f: ").appendSibling(localizeLater("gui.Chat.WarningFingerprint", event.expectedFingerprint, event.fingerprints.toTypedArray().joinToString(", ")))

        @JvmStatic
        fun WarningRecipes(): ITextComponent = TextComponentString("§aOpenComputers§f: ").appendSibling(localizeLater("gui.Chat.WarningRecipes"))

        @JvmStatic
        fun WarningClassTransformer(): ITextComponent = TextComponentString("§aOpenComputers§f: ").appendSibling(localizeLater("gui.Chat.WarningClassTransformer"))

        @JvmStatic
        fun WarningSimpleComponent(): ITextComponent = TextComponentString("§aOpenComputers§f: ").appendSibling(localizeLater("gui.Chat.WarningSimpleComponent"))

        @JvmStatic
        fun WarningLink(url: String): ITextComponent = TextComponentString("§aOpenComputers§f: ").appendSibling(localizeLater("gui.Chat.WarningLink", url))

        @JvmStatic
        fun InfoNewVersion(version: String): ITextComponent = TextComponentString("§aOpenComputers§f: ").appendSibling(localizeLater("gui.Chat.NewVersion", version))

        @JvmStatic
        fun TextureName(name: String): ITextComponent = TextComponentString("§aOpenComputers§f: ").appendSibling(localizeLater("gui.Chat.TextureName", name))
    }

    object Computer {
        @JvmStatic
        fun TurnOff(): String = localizeImmediately("gui.Robot.TurnOff")

        @JvmStatic
        fun TurnOn(): String = localizeImmediately("gui.Robot.TurnOn")

        @JvmStatic
        fun Power(): String = localizeImmediately("gui.Robot.Power")
    }

    object Drive {
        @JvmStatic
        fun Managed(): String = localizeImmediately("gui.Drive.Managed")

        @JvmStatic
        fun Unmanaged(): String = localizeImmediately("gui.Drive.Unmanaged")

        @JvmStatic
        fun Warning(): String = localizeImmediately("gui.Drive.Warning")

        @JvmStatic
        fun ReadOnlyLock(): String = localizeImmediately("gui.Drive.ReadOnlyLock")

        @JvmStatic
        fun LockWarning(): String = localizeImmediately("gui.Drive.ReadOnlyLockWarning")
    }

    object Raid {
        @JvmStatic
        fun Warning(): String = localizeImmediately("gui.Raid.Warning")
    }

    object Rack {
        @JvmStatic
        fun Top(): String = localizeImmediately("gui.Rack.Top")

        @JvmStatic
        fun Bottom(): String = localizeImmediately("gui.Rack.Bottom")

        @JvmStatic
        fun Left(): String = localizeImmediately("gui.Rack.Left")

        @JvmStatic
        fun Right(): String = localizeImmediately("gui.Rack.Right")

        @JvmStatic
        fun Back(): String = localizeImmediately("gui.Rack.Back")

        @JvmStatic
        fun None(): String = localizeImmediately("gui.Rack.None")

        @JvmStatic
        fun RelayEnabled(): String = localizeImmediately("gui.Rack.Enabled")

        @JvmStatic
        fun RelayDisabled(): String = localizeImmediately("gui.Rack.Disabled")

        @JvmStatic
        fun RelayModeTooltip(): String = localizeImmediately("gui.Rack.RelayModeTooltip")

        @JvmStatic
        fun OrientationTooltip(): String = localizeImmediately("gui.Rack.OrientationTooltip")
    }

    object Switch {
        @JvmStatic
        fun TransferRate(): String = localizeImmediately("gui.Switch.TransferRate")

        @JvmStatic
        fun PacketsPerCycle(): String = localizeImmediately("gui.Switch.PacketsPerCycle")

        @JvmStatic
        fun QueueSize(): String = localizeImmediately("gui.Switch.QueueSize")
    }

    object Terminal {
        @JvmStatic
        fun InvalidKey(): ITextComponent = localizeLater("gui.Terminal.InvalidKey")

        @JvmStatic
        fun OutOfRange(): ITextComponent = localizeLater("gui.Terminal.OutOfRange")
    }

    object Tooltip {
        @JvmStatic
        fun DiskUsage(used: Long, capacity: Long): String = localizeImmediately("tooltip.diskusage", used.toString(), capacity.toString())

        @JvmStatic
        fun DiskMode(isUnmanaged: Boolean): String = localizeImmediately(if (isUnmanaged) "tooltip.diskmodeunmanaged" else "tooltip.diskmodemanaged")

        @JvmStatic
        fun Materials(): String = localizeImmediately("tooltip.materials")

        @JvmStatic
        fun DiskLock(lockInfo: String): String = if (lockInfo.isEmpty()) "" else localizeImmediately("tooltip.disklocked", lockInfo)

        @JvmStatic
        fun Tier(tier: Int): String = localizeImmediately("tooltip.tier", tier.toString())

        @JvmStatic
        fun PrintBeaconBase(): String = localizeImmediately("tooltip.print.BeaconBase")

        @JvmStatic
        fun PrintLightValue(level: Int): String = localizeImmediately("tooltip.print.LightValue", level.toString())

        @JvmStatic
        fun PrintRedstoneLevel(level: Int): String = localizeImmediately("tooltip.print.RedstoneLevel", level.toString())

        @JvmStatic
        fun MFULinked(isLinked: Boolean): String = localizeImmediately(if (isLinked) "tooltip.upgrademf.Linked" else "tooltip.upgrademf.Unlinked")

        @JvmStatic
        fun ExperienceLevel(level: Double): String = localizeImmediately("tooltip.robot_level", level.toString())
    }
}
