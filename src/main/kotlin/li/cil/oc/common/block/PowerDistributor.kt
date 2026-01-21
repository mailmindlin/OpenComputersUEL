package li.cil.oc.common.block

import li.cil.oc.common.tileentity.PowerDistributor as TEPowerDistributor
import net.minecraft.world.World

class PowerDistributor : SimpleBlock() {
    override fun createNewTileEntity(world: World, metadata: Int) = TEPowerDistributor()
}
