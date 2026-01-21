package li.cil.oc.common.item.data

import li.cil.oc.server.component.DebugCard.AccessContext
import li.cil.oc.Constants
import li.cil.oc.Settings
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

class DebugCardData : ItemData {
    constructor() : super(Constants.ItemName.DebugCard)

    constructor(stack: ItemStack) : this() {
        load(stack)
    }

    var access: AccessContext? = null

    private val DataTag = Settings.namespace + "data"

    override fun load(nbt: NBTTagCompound) {
        access = AccessContext.load(dataTag(nbt))
    }

    override fun save(nbt: NBTTagCompound) {
        val tag = dataTag(nbt)
        AccessContext.remove(tag)
        access?.save(tag)
    }

    private fun dataTag(nbt: NBTTagCompound): NBTTagCompound {
        if (!nbt.hasKey(DataTag)) {
            nbt.setTag(DataTag, NBTTagCompound())
        }
        return nbt.getCompoundTag(DataTag)
    }
}
