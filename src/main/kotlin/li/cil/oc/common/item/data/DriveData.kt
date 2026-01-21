package li.cil.oc.common.item.data

import li.cil.oc.Settings
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import li.cil.oc.server.fs
import net.minecraft.entity.player.EntityPlayer

class DriveData : ItemData {
    constructor() : super(null)

    constructor(stack: ItemStack) : this() {
        load(stack)
    }

    var isUnmanaged = false
    var lockInfo: String = ""

    val isLocked: Boolean
        get() = lockInfo.isNotEmpty()

    private val UnmanagedTag = Settings.namespace + "unmanaged"
    private val LockTag = Settings.namespace + "lock"

    override fun load(nbt: NBTTagCompound) {
        isUnmanaged = nbt.getBoolean(UnmanagedTag)
        lockInfo = if (nbt.hasKey(LockTag)) {
            nbt.getString(LockTag)
        } else ""
    }

    override fun save(nbt: NBTTagCompound) {
        nbt.setBoolean(UnmanagedTag, isUnmanaged)
        nbt.setString(LockTag, lockInfo)
    }

    companion object {
        @JvmStatic
        fun lock(stack: ItemStack, player: EntityPlayer) {
            val key = player.name
            val data = DriveData(stack)
            if (!data.isLocked) {
                data.lockInfo = when {
                    !key.isNullOrEmpty() -> key
                    else -> "notch" // meaning: "unknown"
                }
                data.save(stack)
            }
        }

        @JvmStatic
        fun setUnmanaged(stack: ItemStack, unmanaged: Boolean) {
            val data = DriveData(stack)
            if (data.isUnmanaged != unmanaged) {
                fs.FileSystem.removeAddress(stack)
                data.lockInfo = ""
            }
            data.isUnmanaged = unmanaged
            data.save(stack)
        }
    }
}
