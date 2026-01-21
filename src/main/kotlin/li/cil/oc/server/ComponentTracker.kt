package li.cil.oc.server

import net.minecraft.world.World

object ComponentTracker: li.cil.oc.common.ComponentTracker() {
  override fun clear(world: World) {
    if (!world.isRemote) super.clear(world)
  }
}
