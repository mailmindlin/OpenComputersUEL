package li.cil.oc.common.nanomachines.provider

import li.cil.oc.Settings
import li.cil.oc.api.Nanomachines as ApiNanomachines
import li.cil.oc.api.nanomachines.Behavior
import li.cil.oc.api.prefab.AbstractBehavior
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.Vec3d

object MagnetProvider : ScalaProvider("9324d5ec-71f1-41c2-b51c-406e527668fc") {
    override fun createScalaBehaviors(player: EntityPlayer): Iterable<Behavior> = listOf(MagnetBehavior(player))

    override fun readBehaviorFromNBT(player: EntityPlayer, nbt: NBTTagCompound): Behavior = MagnetBehavior(player)

    class MagnetBehavior(player: EntityPlayer) : AbstractBehavior(player) {
        override fun getNameHint(): String = "magnet"

        override fun update() {
            val world = player.entityWorld
            if (!world.isRemote) {
                val actualRange = Settings.get.nanomachineMagnetRange * ApiNanomachines.getController(player).getInputCount(this)
                val items = world.getEntitiesWithinAABB(EntityItem::class.java, player.entityBoundingBox.grow(actualRange.toDouble(), actualRange.toDouble(), actualRange.toDouble()))
                for (item in items) {
                    if (!item.cannotPickup() && !item.item.isEmpty) {
                        val canPickUp = player.inventory.mainInventory.any { stack ->
                            stack.isEmpty || (stack.count < stack.maxStackSize && stack.isItemEqual(item.item))
                        }
                        if (canPickUp) {
                            val dx = player.posX - item.posX
                            val dy = player.posY - item.posY
                            val dz = player.posZ - item.posZ
                            val delta = Vec3d(dx, dy, dz).normalize()
                            item.addVelocity(delta.x * 0.1, delta.y * 0.1, delta.z * 0.1)
                        }
                    }
                }
            }
        }
    }
}
