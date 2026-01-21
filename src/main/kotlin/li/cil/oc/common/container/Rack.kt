package li.cil.oc.common.container

import li.cil.oc.api.component.RackMountable
import li.cil.oc.common.Slot
import li.cil.oc.common.tileentity
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagIntArray
import net.minecraft.nbt.NBTTagList
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.util.Constants.NBT

class Rack(playerInventory: InventoryPlayer, val rack: tileentity.Rack) : Player(playerInventory, rack) {
    companion object {
        const val MaxConnections = 4
    }

    val nodePresence: Array<BooleanArray> = Array(4) { BooleanArray(4) { false } }

    init {
        addSlotToContainer(20, 23, Slot.RackMountable)
        addSlotToContainer(20, 43, Slot.RackMountable)
        addSlotToContainer(20, 63, Slot.RackMountable)
        addSlotToContainer(20, 83, Slot.RackMountable)
        addPlayerInventorySlots(8, 128)
    }

    override fun updateCustomData(nbt: NBTTagCompound) {
        super.updateCustomData(nbt)

        val nodeMappingList = nbt.getTagList("nodeMapping", NBT.TAG_INT_ARRAY)
        for (i in 0 until nodeMappingList.tagCount()) {
            val sidesTag = nodeMappingList.get(i) as? NBTTagIntArray ?: continue
            val sides = sidesTag.intArray.map { side ->
                if (side >= 0) EnumFacing.byIndex(side) else null
            }.toTypedArray()
            if (i < rack.nodeMapping.size) {
                rack.nodeMapping[i] = sides
            }
        }

        val nodePresenceArray = nbt.getIntArray("nodePresence")
        val boolArray = nodePresenceArray.map { it != 0 }.toBooleanArray()
        for (i in 0 until minOf(nodePresence.size, boolArray.size / MaxConnections)) {
            for (j in 0 until MaxConnections) {
                val idx = i * MaxConnections + j
                if (idx < boolArray.size) {
                    nodePresence[i][j] = boolArray[idx]
                }
            }
        }

        rack.isRelayEnabled = nbt.getBoolean("isRelayEnabled")
    }

    override fun detectCustomDataChanges(nbt: NBTTagCompound) {
        super.detectCustomDataChanges(nbt)

        val nodeMappingList = NBTTagList()
        for (sides in rack.nodeMapping) {
            val intArray = sides.map { side -> side?.ordinal ?: -1 }.toIntArray()
            nodeMappingList.appendTag(NBTTagIntArray(intArray))
        }
        nbt.setTag("nodeMapping", nodeMappingList)

        val nodePresenceList = mutableListOf<Int>()
        for (slot in 0 until rack.sizeInventory) {
            val mountable = rack.getMountable(slot)
            if (mountable is RackMountable) {
                nodePresenceList.add(1) // true for mountable itself
                val connectableCount = minOf(MaxConnections - 1, mountable.connectableCount)
                for (index in 0 until connectableCount) {
                    nodePresenceList.add(if (mountable.getConnectableAt(index) != null) 1 else 0)
                }
                // Pad to MaxConnections
                repeat(MaxConnections - 1 - connectableCount) {
                    nodePresenceList.add(0)
                }
            } else {
                repeat(MaxConnections) {
                    nodePresenceList.add(0)
                }
            }
        }
        nbt.setIntArray("nodePresence", nodePresenceList.toIntArray())

        nbt.setBoolean("isRelayEnabled", rack.isRelayEnabled)
    }
}
