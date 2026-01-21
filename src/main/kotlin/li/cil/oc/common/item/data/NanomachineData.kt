package li.cil.oc.common.item.data

import li.cil.oc.common.nanomachines.ControllerImpl
import li.cil.oc.Constants
import li.cil.oc.Settings
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

class NanomachineData : ItemData {
    constructor() : super(Constants.ItemName.Nanomachines)

    constructor(stack: ItemStack) : this() {
        load(stack)
    }

    constructor(controller: ControllerImpl) : this() {
        uuid = controller.uuid
        val nbt = NBTTagCompound()
        controller.configuration.save(nbt, true)
        configuration = nbt
    }

    var uuid = ""
    var configuration: NBTTagCompound? = null

    private val UUIDTag = Settings.namespace + "uuid"
    private val ConfigurationTag = Settings.namespace + "configuration"

    override fun load(nbt: NBTTagCompound) {
        uuid = nbt.getString(UUIDTag)
        configuration = if (nbt.hasKey(ConfigurationTag)) {
            nbt.getCompoundTag(ConfigurationTag)
        } else {
            null
        }
    }

    override fun save(nbt: NBTTagCompound) {
        nbt.setString(UUIDTag, uuid)
        configuration?.let { nbt.setTag(ConfigurationTag, it) }
    }
}
