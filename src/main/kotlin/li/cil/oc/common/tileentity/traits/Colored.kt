package li.cil.oc.common.tileentity.traits

import li.cil.oc.api.internal.Colored as InternalColored
import li.cil.oc.common.tileentity.traits.delegates.ColoredDelegate

/**
 * Interface for tile entities that have a color.
 * Implementations must provide a ColoredDelegate instance.
 */
interface Colored : TileEntityTrait, InternalColored {
    val colorDelegate: ColoredDelegate

    override fun getColor(): Int = colorDelegate.color

    override fun setColor(value: Int) {
        colorDelegate.setColor(value)
    }

    override fun controlsConnectivity(): Boolean = false

    val consumesDye: Boolean get() = false
}
