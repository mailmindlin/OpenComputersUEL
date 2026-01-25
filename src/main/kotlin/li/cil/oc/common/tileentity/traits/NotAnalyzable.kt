package li.cil.oc.common.tileentity.traits

import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network.Node
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.EnumFacing

/** Mixin for types explicitly not analyzable */
interface NotAnalyzable : Analyzable {
    override fun onAnalyze(player: EntityPlayer, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Array<Node>? = null
}
