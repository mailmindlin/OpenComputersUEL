package li.cil.oc.common.item.traits

import li.cil.oc.Settings
import li.cil.oc.util.PackedColor

interface GPULike : Delegate {
    val gpuTier: Int

    override val tooltipData: Array<Any>
        get() {
            val (w, h) = Settings.screenResolutionsByTier(gpuTier)
            val depth = PackedColor.Depth.bits(Settings.screenDepthsByTier(gpuTier))
            return arrayOf(w, h, depth,
                when (gpuTier) {
                    0 -> "1/1/4/2/2"
                    1 -> "2/4/8/4/4"
                    2 -> "4/8/16/8/8"
                    else -> ""
                })
        }
}
