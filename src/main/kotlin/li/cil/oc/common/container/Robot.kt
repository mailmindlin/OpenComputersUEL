package li.cil.oc.common.container

import li.cil.oc.api
import li.cil.oc.client.Textures
import li.cil.oc.common
import li.cil.oc.common.tileentity
import li.cil.oc.util.SideTracker
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

class Robot(playerInventory: InventoryPlayer, val robot: tileentity.Robot) : Player(playerInventory, robot) {
    val hasScreen: Boolean = robot.components.any { it is api.internal.TextBuffer }
    private val withScreenHeight = 256
    private val noScreenHeight = 108
    val deltaY: Int = if (hasScreen) 0 else withScreenHeight - noScreenHeight

    // This factor is used to make the energy values transferable using
    // MCs 'progress bar' stuff, even though those internally send the
    // values as shorts over the net (for whatever reason).
    private val factor = 100

    private var lastSentBuffer = -1
    private var lastSentBufferSize = -1

    init {
        addSlotToContainer(170 + 0 * slotSize, 232 - deltaY, common.Slot.Tool)
        addSlotToContainer(170 + 1 * slotSize, 232 - deltaY, robot.containerSlotType(1), robot.containerSlotTier(1))
        addSlotToContainer(170 + 2 * slotSize, 232 - deltaY, robot.containerSlotType(2), robot.containerSlotTier(2))
        addSlotToContainer(170 + 3 * slotSize, 232 - deltaY, robot.containerSlotType(3), robot.containerSlotTier(3))

        for (i in 0..3) {
            val y = 156 + i * slotSize - deltaY
            for (j in 0..3) {
                val x = 170 + j * slotSize
                addSlotToContainer(InventorySlot(this, otherInventory, inventorySlots.size, x, y))
            }
        }
        for (i in 16 until 64) {
            addSlotToContainer(InventorySlot(this, otherInventory, inventorySlots.size, -10000, -10000))
        }

        addPlayerInventorySlots(6, 174 - deltaY)
    }

    @SideOnly(Side.CLIENT)
    override fun updateProgressBar(id: Int, value: Int) {
        super.updateProgressBar(id, value)
        if (id == 0) {
            robot.globalBuffer = (value * factor).toDouble()
        }

        if (id == 1) {
            robot.globalBufferSize = (value * factor).toDouble()
        }
    }

    override fun detectAndSendChanges() {
        super.detectAndSendChanges()
        if (SideTracker.isServer) {
            val currentBuffer = robot.globalBuffer.toInt() / factor
            if (currentBuffer != lastSentBuffer) {
                lastSentBuffer = currentBuffer
                sendWindowProperty(0, lastSentBuffer)
            }

            val currentBufferSize = robot.globalBufferSize.toInt() / factor
            if (currentBufferSize != lastSentBufferSize) {
                lastSentBufferSize = currentBufferSize
                sendWindowProperty(1, lastSentBufferSize)
            }
        }
    }

    inner class InventorySlot(container: Player, inventory: IInventory, index: Int, x: Int, y: Int)
        : StaticComponentSlot(container, inventory, index, x, y, common.Slot.Any, common.Tier.Any) {

        val isValid: Boolean
            get() = robot.isInventorySlot(slotIndex)

        @SideOnly(Side.CLIENT)
        override fun isEnabled(): Boolean = isValid && super.isEnabled()

        override fun getBackgroundLocation(): ResourceLocation? =
            if (isValid) super.getBackgroundLocation()
            else Textures.Icons.get(common.Tier.None)

        override fun getStack(): ItemStack =
            if (isValid) super.getStack()
            else ItemStack.EMPTY
    }
}
