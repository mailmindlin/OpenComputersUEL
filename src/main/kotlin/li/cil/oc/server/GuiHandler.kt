package li.cil.oc.server

import li.cil.oc.common.GuiHandler as CommonGuiHandler
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.world.World

object GuiHandler: CommonGuiHandler() {
  override fun getClientGuiElement(id: Int, player: EntityPlayer, world: World, x: Int, y: Int, z: Int): Any? = null
}
