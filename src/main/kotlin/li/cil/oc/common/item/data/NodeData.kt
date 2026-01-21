package li.cil.oc.common.item.data

import li.cil.oc.Settings
import li.cil.oc.api.network.Visibility
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

// Generic one for items that are used as components; gets the items node info.
class NodeData : ItemData {
    constructor() : super(null)

    constructor(stack: ItemStack) : this() {
        load(stack)
    }

    var address: String? = null
    var buffer: Double? = null
    var visibility: Visibility? = null

    private val DataTag = Settings.namespace + "data"

    override fun load(nbt: NBTTagCompound) {
        val nodeNbt = nbt.getCompoundTag(DataTag).getCompoundTag(NodeTag)
        address = if (nodeNbt.hasKey(AddressTag)) {
            nodeNbt.getString(AddressTag)
        } else null
        buffer = if (nodeNbt.hasKey(BufferTag)) {
            nodeNbt.getDouble(BufferTag)
        } else null
        visibility = if (nodeNbt.hasKey(VisibilityTag)) {
            Visibility.values()[nodeNbt.getInteger(VisibilityTag)]
        } else null
    }

    override fun save(nbt: NBTTagCompound) {
        if (!nbt.hasKey(DataTag)) {
            nbt.setTag(DataTag, NBTTagCompound())
        }
        val dataNbt = nbt.getCompoundTag(DataTag)
        if (!dataNbt.hasKey(NodeTag)) {
            dataNbt.setTag(NodeTag, NBTTagCompound())
        }
        val nodeNbt = dataNbt.getCompoundTag(NodeTag)
        address?.let { nodeNbt.setString(AddressTag, it) }
        buffer?.let { nodeNbt.setDouble(BufferTag, it) }
        visibility?.let { nodeNbt.setInteger(VisibilityTag, it.ordinal) }
    }

    companion object {
        @JvmField
        val NodeTag = "node"
        @JvmField
        val AddressTag = "address"
        @JvmField
        val BufferTag = "buffer"
        @JvmField
        val VisibilityTag = "visibility"
    }
}
