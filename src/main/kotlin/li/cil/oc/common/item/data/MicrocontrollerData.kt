package li.cil.oc.common.item.data

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.common.Tier
import li.cil.oc.util.ExtendedNBT.toArray
import li.cil.oc.util.setNewTagList
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.util.Constants.NBT

open class MicrocontrollerData : ItemData {
    constructor(itemName: String = Constants.BlockName.Microcontroller) : super(itemName)

    constructor(stack: ItemStack) : this() {
        load(stack)
    }

    var tier = Tier.One

    var components: Array<ItemStack> = arrayOf(ItemStack.EMPTY)

    var storedEnergy = 0

    private val TierTag = Settings.namespace + "tier"
    private val ComponentsTag = Settings.namespace + "components"
    private val StoredEnergyTag = Settings.namespace + "storedEnergy"

    override fun load(nbt: NBTTagCompound) {
        tier = nbt.getByte(TierTag).toInt()
        components = nbt.getTagList(ComponentsTag, NBT.TAG_COMPOUND)
            .toArray<NBTTagCompound>()
            .map { ItemStack(it) }
            .filter { !it.isEmpty }
            .toTypedArray()
        storedEnergy = nbt.getInteger(StoredEnergyTag)

        // Reserve slot for EEPROM if necessary, avoids having to resize the
        // components array in the MCU tile entity, which isn't possible currently.
        if (!components.any { stack -> ApiItems.get(stack) == ApiItems.get(Constants.ItemName.EEPROM) }) {
            components = components + ItemStack.EMPTY
        }
    }

    override fun save(nbt: NBTTagCompound) {
        nbt.setByte(TierTag, tier.toByte())
        nbt.setNewTagList(ComponentsTag, components.filter { !it.isEmpty }.asIterable())
        nbt.setInteger(StoredEnergyTag, storedEnergy)
    }

    fun copyItemStack(): ItemStack {
        val stack = createItemStack()
        val newInfo = MicrocontrollerData(stack)
        newInfo.save(stack)
        return stack
    }
}
