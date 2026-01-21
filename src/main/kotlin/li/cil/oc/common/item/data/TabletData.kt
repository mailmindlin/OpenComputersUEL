package li.cil.oc.common.item.data

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.common.Tier
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.util.Constants.NBT

class TabletData : ItemData {
    constructor() : super(Constants.ItemName.Tablet)

    constructor(stack: ItemStack) : this() {
        load(stack)
    }

    var items = Array(32) { ItemStack.EMPTY }
    var isRunning = false
    var energy = 0.0
    var maxEnergy = 0.0
    var tier = Tier.One
    var container: ItemStack = ItemStack.EMPTY

    private val ItemsTag = Settings.namespace + "items"
    private val SlotTag = "slot"
    private val ItemTag = "item"
    private val IsRunningTag = Settings.namespace + "isRunning"
    private val EnergyTag = Settings.namespace + "energy"
    private val MaxEnergyTag = Settings.namespace + "maxEnergy"
    private val TierTag = Settings.namespace + "tier"
    private val ContainerTag = Settings.namespace + "container"

    override fun load(nbt: NBTTagCompound) {
        nbt.getTagList(ItemsTag, NBT.TAG_COMPOUND).forEach<NBTTagCompound> { slotNbt ->
            val slot = slotNbt.getByte(SlotTag).toInt()
            if (slot >= 0 && slot < items.size) {
                items[slot] = ItemStack(slotNbt.getCompoundTag(ItemTag))
            }
        }
        isRunning = nbt.getBoolean(IsRunningTag)
        energy = nbt.getDouble(EnergyTag)
        maxEnergy = nbt.getDouble(MaxEnergyTag)
        tier = nbt.getInteger(TierTag)
        if (nbt.hasKey(ContainerTag)) {
            container = ItemStack(nbt.getCompoundTag(ContainerTag))
        }
    }

    override fun save(nbt: NBTTagCompound) {
        nbt.setNewTagList(ItemsTag,
            items.mapIndexedNotNull { slot, stack ->
                if (!stack.isEmpty) Pair(stack, slot) else null
            }.map { (stack, slot) ->
                val slotNbt = NBTTagCompound()
                slotNbt.setByte(SlotTag, slot.toByte())
                slotNbt.setNewCompoundTag(ItemTag, stack::writeToNBT)
                slotNbt
            }
        )
        nbt.setBoolean(IsRunningTag, isRunning)
        nbt.setDouble(EnergyTag, energy)
        nbt.setDouble(MaxEnergyTag, maxEnergy)
        nbt.setInteger(TierTag, tier)
        if (!container.isEmpty) nbt.setNewCompoundTag(ContainerTag, container::writeToNBT)
    }
}
