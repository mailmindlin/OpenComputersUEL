package li.cil.oc.common.nanomachines.provider

import li.cil.oc.api.Nanomachines
import li.cil.oc.api.nanomachines.Controller
import li.cil.oc.api.prefab.AbstractBehavior
import li.cil.oc.common.nanomachines.ControllerImpl
import net.minecraft.entity.player.EntityPlayer

sealed class AbstractBehaviorKt(player: EntityPlayer): AbstractBehavior(player) {
    protected val controller: Controller = Nanomachines.getController(player)!!
}