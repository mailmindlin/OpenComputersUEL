package li.cil.oc.common.block

import li.cil.oc.Settings
import li.cil.oc.common.tileentity.PowerConverter as TEPowerConverter
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.ItemBlacklist
import li.cil.oc.util.Tooltip
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import java.text.DecimalFormat
import li.cil.oc.common.block.traits.PowerAcceptor as TraitPowerAcceptor

class PowerConverter : SimpleBlock(), TraitPowerAcceptor {
    init {
        if (Settings.get.ignorePower) {
            setCreativeTab(null)
            ItemBlacklist.hide(this)
        }
    }

    private val formatter = DecimalFormat("#.#")

    // ----------------------------------------------------------------------- //

    override fun tooltipTail(metadata: Int, stack: ItemStack, world: World, tooltip: MutableList<String>, advanced: ITooltipFlag) {
        super.tooltipTail(metadata, stack, world, tooltip, advanced)
        // TODO more generic way of integration modules of power providing mods to provide tooltip lines
        // if (Mods.Factorization.isAvailable) {
        //     addRatio(tooltip, "Factorization", Settings.get.ratioFactorization)
        // }
        if (Mods.IndustrialCraft2.isModAvailable) {
            addRatio(tooltip, "IndustrialCraft2", Settings.get.ratioIndustrialCraft2)
        }
    }

    private fun addExtension(x: Double): String =
        when {
            x >= 1e9 -> formatter.format(x / 1e9) + "G"
            x >= 1e6 -> formatter.format(x / 1e6) + "M"
            x >= 1e3 -> formatter.format(x / 1e3) + "K"
            else -> formatter.format(x)
        }

    private fun addRatio(tooltip: MutableList<String>, name: String, ratio: Double) {
        val (a, b) = if (ratio > 1) Pair(1.0, ratio) else Pair(1.0 / ratio, 1.0)
        tooltip.addAll(Tooltip.get(javaClass.simpleName.lowercase() + "." + name, addExtension(a), addExtension(b)))
    }

    // ----------------------------------------------------------------------- //

    override val energyThroughput: Double get() = Settings.get.powerConverterRate

    override fun createNewTileEntity(world: World, metadata: Int) = TEPowerConverter()
}
