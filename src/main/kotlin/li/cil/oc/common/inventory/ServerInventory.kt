package li.cil.oc.common.inventory

import li.cil.oc.api.Driver
import li.cil.oc.api.internal
import li.cil.oc.common.InventorySlots
import li.cil.oc.util.ItemUtils
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import kotlin.math.max

abstract class ServerInventory : ItemStackInventory() {
    open val tier: Int
        get() = max(ItemUtils.caseTier(container), 0)

    override fun getSizeInventory(): Int = InventorySlots.server(tier).size

    override val inventoryName: String
        get() = "server"

    override fun getInventoryStackLimit(): Int = 1

    override fun isUsableByPlayer(player: EntityPlayer): Boolean = false

    override fun isItemValidForSlot(slot: Int, stack: ItemStack): Boolean {
        val driver = Driver.driverFor(stack, internal.Server::class.java)
        return if (driver != null) {
            val provided = InventorySlots.server(tier)[slot]
            driver.slot(stack) == provided.slot && driver.tier(stack) <= provided.tier
        } else {
            false
        }
    }
}
