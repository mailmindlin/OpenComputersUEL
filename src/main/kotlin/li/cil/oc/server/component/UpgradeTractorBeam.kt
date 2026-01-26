package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.internal.Agent
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.InventoryUtils
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.math.BlockPos
import kotlin.math.floor

object UpgradeTractorBeam {

    abstract class Common : ManagedEnvironmentKt(), DeviceInfoKt {
        override val node = nodeFactory(Visibility.Network)
            .withComponent("tractor_beam")
            .create()

        private val pickupRadius = 3

        override val deviceInfo = mapOf(
            DeviceAttribute.Class to DeviceClass.Generic,
            DeviceAttribute.Description to "Tractor beam",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "T313-K1N.3515"
        )

        protected abstract val position: BlockPosition

        protected abstract fun collectItem(item: EntityItem)

        private val world get() = position.world!!

        @Callback(doc = "function():boolean -- Tries to pick up a random item in the robots' vicinity.")
        fun suck(context: Context, args: Arguments): Array<Any?> {
            val items = world.getEntitiesWithinAABB(
                EntityItem::class.java,
                position.bounds.grow(pickupRadius.toDouble(), pickupRadius.toDouble(), pickupRadius.toDouble())
            ).filter { item -> item.isEntityAlive && !item.cannotPickup() }

            if (items.isNotEmpty()) {
                val item = items[world.rand.nextInt(items.size)]
                val stack = item.item
                val size = stack.count
                collectItem(item)
                if (stack.count < size || item.isDead) {
                    context.pause(Settings.get.suckDelay)
                    world.playEvent(
                        2003,
                        BlockPos(floor(item.posX).toInt(), floor(item.posY).toInt(), floor(item.posZ).toInt()),
                        0
                    )
                    return result(true)
                }
            }
            return result(false)
        }
    }

    class Player(val owner: EnvironmentHost, val player: () -> EntityPlayer) : Common() {
        override val position get() = BlockPosition(owner)

        override fun collectItem(item: EntityItem) {
            item.onCollideWithPlayer(player())
        }
    }

    class Drone(val owner: Agent) : Common() {
        override val position get() = BlockPosition(owner)

        override fun collectItem(item: EntityItem) {
            InventoryUtils.insertIntoInventory(
                item.item,
                owner.mainInventory(),
                null,
                64,
                simulate = false,
                insertionSlots
            )
        }

        private val insertionSlots: Iterable<Int>
            get() = (owner.selectedSlot() until owner.mainInventory().sizeInventory) + (0 until owner.selectedSlot())
    }
}
