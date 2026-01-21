package li.cil.oc.common.item.data

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.util.Constants.NBT

class RaidData : ItemData {
    constructor() : super(Constants.BlockName.Raid)

    constructor(stack: ItemStack) : this() {
        load(stack)
    }

    var disks = emptyArray<ItemStack>()

    var filesystem = NBTTagCompound()

    var label: String? = null

    private val DisksTag = Settings.namespace + "disks"
    private val FileSystemTag = Settings.namespace + "filesystem"
    private val LabelTag = Settings.namespace + "label"

    override fun load(nbt: NBTTagCompound) {
        disks = nbt.getTagList(DisksTag, NBT.TAG_COMPOUND)
            .toArray<NBTTagCompound>()
            .map { ItemStack(it) }
            .toTypedArray()
        filesystem = nbt.getCompoundTag(FileSystemTag)
        label = if (nbt.hasKey(LabelTag)) {
            nbt.getString(LabelTag)
        } else null
    }

    override fun save(nbt: NBTTagCompound) {
        nbt.setNewTagList(DisksTag, disks.asIterable())
        nbt.setTag(FileSystemTag, filesystem)
        label?.let { nbt.setString(LabelTag, it) }
    }
}
