package li.cil.oc.integration.computercraft

import dan200.computercraft.api.filesystem.IMount
import dan200.computercraft.api.filesystem.IWritableMount
import dan200.computercraft.api.media.IMedia
import li.cil.oc.Settings
import li.cil.oc.api.fs.FileSystem
import li.cil.oc.api.fs.Label
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.integration.opencomputers.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

object DriverComputerCraftMedia : Item() {
    override fun worksWith(stack: ItemStack): Boolean = stack.item is IMedia

    override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) = if (!host.world().isRemote) {
        val address = addressFromTag(dataTag(stack))
        val mount = fromComputerCraft((stack.item as IMedia).createDataMount(stack, host.world()))
        val environment = li.cil.oc.api.FileSystem.asManagedEnvironment(
            mount,
            ComputerCraftLabel(stack),
            host,
            Settings.resourceDomain + ":floppy_access"
        )
        if (environment != null) {
            (environment.node() as li.cil.oc.server.network.Node).address = address
            environment
        } else {
            null
        }
    } else {
        null
    }

    @JvmStatic
    fun fromComputerCraft(mount: Any?): FileSystem? = createFileSystem(mount)

    override fun slot(stack: ItemStack) = Slot.Floppy

    fun createFileSystem(mount: Any?): FileSystem? = when (mount) {
        is IWritableMount -> ComputerCraftWritableFileSystem(mount)
        is IMount -> ComputerCraftFileSystem(mount)
        else -> null
    }

    private fun addressFromTag(tag: NBTTagCompound): String =
        if (tag.hasKey("node") && tag.getCompoundTag("node").hasKey("address")) {
            tag.getCompoundTag("node").getString("address")
        } else {
            java.util.UUID.randomUUID().toString()
        }

    class ComputerCraftLabel(val stack: ItemStack) : Label {
        val media = stack.item as IMedia

        override fun getLabel(): String? = media.getLabel(stack as ItemStack)

        override fun setLabel(value: String?) {
            media.setLabel(stack, value)
        }

        override fun load(nbt: NBTTagCompound) {}

        override fun save(nbt: NBTTagCompound) {}
    }
}
