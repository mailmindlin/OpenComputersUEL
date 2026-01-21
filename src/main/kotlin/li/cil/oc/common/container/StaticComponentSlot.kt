package li.cil.oc.common.container

import li.cil.oc.client.Textures
import li.cil.oc.common.Slot as CommonSlot
import net.minecraft.inventory.IInventory
import net.minecraft.util.ResourceLocation

open class StaticComponentSlot(
    override val container: Player,
    inventory: IInventory,
    index: Int,
    x: Int,
    y: Int,
    override val slot: String,
    override val tier: Int
) : ComponentSlot(inventory, index, x, y) {

    init {
        if (container.playerInventory.player.entityWorld.isRemote) {
            setBackgroundLocation(Textures.Icons.get(slot))
        }
    }

    override val tierIcon: ResourceLocation? = Textures.Icons.get(tier)

    override fun getSlotStackLimit(): Int = when (slot) {
        CommonSlot.Tool, CommonSlot.Any, CommonSlot.Filtered -> super.getSlotStackLimit()
        CommonSlot.None -> 0
        else -> 1
    }
}
