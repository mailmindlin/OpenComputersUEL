package li.cil.oc.common.container

import li.cil.oc.client.Textures
import li.cil.oc.common.InventorySlots.InventorySlot
import li.cil.oc.common.Slot as CommonSlot
import li.cil.oc.common.Tier as CommonTier
import li.cil.oc.common.template.AssemblerTemplates
import li.cil.oc.common.tileentity.Assembler as TEAssembler
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

class Assembler(playerInventory: InventoryPlayer, val assembler: TEAssembler) : Player(playerInventory, assembler) {
    init {
        // Computer case.
        run {
            val index = inventorySlots.size
            addSlotToContainer(object : StaticComponentSlot(this, otherInventory, index, 12, 12, "template", CommonTier.Any) {
                @SideOnly(Side.CLIENT)
                override fun isEnabled(): Boolean = !isAssembling() && super.isEnabled()

                override fun getBackgroundLocation() = if (isAssembling()) Textures.Icons.get(CommonTier.None) else super.getBackgroundLocation()
            })
        }

        // Component containers.
        for (i in 0 until 3) {
            addSlotToContainer(34 + i * slotSize, 70, ::slotInfo)
        }

        // Components.
        for (i in 0 until 9) {
            addSlotToContainer(34 + (i % 3) * slotSize, 12 + (i / 3) * slotSize, ::slotInfo)
        }

        // Cards.
        for (i in 0 until 3) {
            addSlotToContainer(104, 12 + i * slotSize, ::slotInfo)
        }

        // CPU.
        addSlotToContainer(126, 12, ::slotInfo)

        // RAM.
        for (i in 0 until 2) {
            addSlotToContainer(126, 30 + i * slotSize, ::slotInfo)
        }

        // Floppy/EEPROM + HDDs.
        for (i in 0 until 3) {
            addSlotToContainer(148, 12 + i * slotSize, ::slotInfo)
        }

        // Show the player's inventory.
        addPlayerInventorySlots(8, 110)
    }

    private fun slotInfo(slot: DynamicComponentSlot): InventorySlot {
        val template = AssemblerTemplates.select(getSlot(0).stack)
        return if (template != null) {
            val index = slot.slotIndex
            val tplSlot = when {
                index in 1 until 4 -> template.containerSlots[index - 1]
                index in 4 until 13 -> template.upgradeSlots[index - 4]
                index in 13 until 21 -> template.componentSlots[index - 13]
                else -> AssemblerTemplates.NoSlot
            }
            InventorySlot(tplSlot.kind, tplSlot.tier)
        } else {
            InventorySlot(CommonSlot.None, CommonTier.None)
        }
    }

    fun isAssembling(): Boolean = synchronizedData.getBoolean("isAssembling")

    fun assemblyProgress(): Double = synchronizedData.getDouble("assemblyProgress")

    fun assemblyRemainingTime(): Int = synchronizedData.getInteger("assemblyRemainingTime")

    override fun detectCustomDataChanges(nbt: NBTTagCompound) {
        synchronizedData.setBoolean("isAssembling", assembler.isAssembling)
        synchronizedData.setDouble("assemblyProgress", assembler.progress)
        synchronizedData.setInteger("assemblyRemainingTime", assembler.timeRemaining)
        super.detectCustomDataChanges(nbt)
    }
}
