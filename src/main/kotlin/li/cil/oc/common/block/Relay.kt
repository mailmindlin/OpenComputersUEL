package li.cil.oc.common.block

import li.cil.oc.Settings
import li.cil.oc.common.GuiType
import li.cil.oc.common.tileentity.Relay as TERelay
import net.minecraft.world.World

class Relay : SimpleBlock(), traits.GUI, traits.PowerAcceptor {
    override val guiType = GuiType.Relay

    override val energyThroughput: Double get() = Settings.get.accessPointRate

    override fun createNewTileEntity(world: World, metadata: Int) = TERelay()
}
