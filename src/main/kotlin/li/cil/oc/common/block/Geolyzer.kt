package li.cil.oc.common.block

import li.cil.oc.common.tileentity.Geolyzer as TEGeolyzer
import net.minecraft.world.World

class Geolyzer : SimpleBlock() {
    override fun createNewTileEntity(world: World, metadata: Int) = TEGeolyzer()
}
