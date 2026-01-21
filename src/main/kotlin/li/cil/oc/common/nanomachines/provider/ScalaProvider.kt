package li.cil.oc.common.nanomachines.provider

import li.cil.oc.api.nanomachines.Behavior
import li.cil.oc.api.prefab.AbstractProvider
import net.minecraft.entity.player.EntityPlayer

abstract class ScalaProvider(id: String) : AbstractProvider(id) {
    abstract fun createScalaBehaviors(player: EntityPlayer): Iterable<Behavior>

    override fun createBehaviors(player: EntityPlayer): Iterable<Behavior> = createScalaBehaviors(player)
}
