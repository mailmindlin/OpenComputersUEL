package li.cil.oc.common.item.data

import com.google.common.base.Strings
import li.cil.oc.Constants
import li.cil.oc.util.ItemUtils
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

class DroneData : MicrocontrollerData {
    constructor() : super(Constants.ItemName.Drone)

    constructor(stack: ItemStack) : this() {
        load(stack)
    }

    var name = ""

    override fun load(nbt: NBTTagCompound) {
        super.load(nbt)
        name = ItemUtils.getDisplayName(nbt) ?: ""
        if (Strings.isNullOrEmpty(name)) {
            name = RobotData.randomName
        }
    }

    override fun save(nbt: NBTTagCompound) {
        super.save(nbt)
        if (!Strings.isNullOrEmpty(name)) {
            ItemUtils.setDisplayName(nbt, name)
        }
    }
}
