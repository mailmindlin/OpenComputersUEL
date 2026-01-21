package li.cil.oc.common.block

import li.cil.oc.common.tileentity
import net.minecraft.world.World

class MotionSensor : SimpleBlock() {
    override fun createNewTileEntity(world: World, metadata: Int) = tileentity.MotionSensor()
}
