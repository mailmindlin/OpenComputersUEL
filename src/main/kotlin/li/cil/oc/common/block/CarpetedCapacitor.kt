package li.cil.oc.common.block

import li.cil.oc.common.tileentity.CarpetedCapacitor as TECarpetedCapacitor
import net.minecraft.world.World

class CarpetedCapacitor : Capacitor() {
    override fun createNewTileEntity(world: World, metadata: Int) = TECarpetedCapacitor()
}
