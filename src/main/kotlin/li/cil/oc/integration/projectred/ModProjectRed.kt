package li.cil.oc.integration.projectred

import li.cil.oc.integration.Mod

import li.cil.oc.api.IMC
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.BundledRedstone
import li.cil.oc.integration.util.BundledRedstone.RedstoneProvider
import li.cil.oc.util.BlockPosition
import mrtjp.projectred.api.ProjectRedAPI
import net.minecraft.util.EnumFacing

internal object ModProjectRed : ModProxy, RedstoneProvider {
    override val mod: Mod = Mods.ProjectRedTransmission

    override fun initialize() {
        IMC.registerWrenchTool("li.cil.oc.integration.projectred.EventHandlerProjectRed.useWrench")
        IMC.registerWrenchToolCheck("li.cil.oc.integration.projectred.EventHandlerProjectRed.isWrench")

        BundledRedstone.addProvider(this)
    }

    override fun computeInput(pos: BlockPosition, side: EnumFacing): Int = 0

    override fun computeBundledInput(pos: BlockPosition, side: EnumFacing): IntArray? {
        val bundledInput = ProjectRedAPI.transmissionAPI.getBundledInput(pos.world().get(), pos.toBlockPos(), side)
        return bundledInput?.map { it and 0xFF }?.toIntArray()
    }
}
