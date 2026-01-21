package li.cil.oc.client

import net.minecraft.world.World

internal object ComponentTracker: li.cil.oc.common.ComponentTracker() {
  override fun clear(world: World) {
    if (world.isRemote) super.clear(world)
  }
}
