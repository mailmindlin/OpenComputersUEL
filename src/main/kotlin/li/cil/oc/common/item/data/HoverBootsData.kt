package li.cil.oc.common.item.data

import li.cil.oc.Constants
import li.cil.oc.Settings
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

class HoverBootsData : ItemData {
    constructor() : super(Constants.ItemName.HoverBoots)

    constructor(stack: ItemStack) : this() {
        load(stack)
    }

    var charge = 0.0

    private val ChargeTag = Settings.namespace + "charge"

    override fun load(nbt: NBTTagCompound) {
        charge = nbt.getDouble(ChargeTag)
    }

    override fun save(nbt: NBTTagCompound) {
        nbt.setDouble(ChargeTag, charge)
    }
}
