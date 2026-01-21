package li.cil.oc.common.tileentity.traits

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.InternalTextBuffer as InternalTextBuffer
import li.cil.oc.api.network.Node
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

abstract class TextBuffer : Environment, Tickable {
    val buffer: InternalTextBuffer by lazy {
        val screenItem = ApiItems.get(Constants.BlockName.ScreenTier1).createItemStack(1)
        val buf = Driver.driverFor(screenItem, javaClass).createEnvironment(screenItem, this) as InternalTextBuffer
        val (maxWidth, maxHeight) = Settings.screenResolutionsByTier(tier)
        buf.setMaximumResolution(maxWidth, maxHeight)
        buf.setMaximumColorDepth(Settings.screenDepthsByTier(tier))
        buf
    }

    override fun node(): Node = buffer.node()

    abstract val tier: Int

    override fun updateEntity() {
        super.updateEntity()
        if (isClient || isConnected) {
            buffer.update()
        }
    }

    // ----------------------------------------------------------------------- //

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)
        buffer.load(nbt)
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)
        buffer.save(nbt)
    }

    @SideOnly(Side.CLIENT)
    override fun readFromNBTForClient(nbt: NBTTagCompound) {
        super.readFromNBTForClient(nbt)
        buffer.load(nbt)
    }

    override fun writeToNBTForClient(nbt: NBTTagCompound) {
        super.writeToNBTForClient(nbt)
        buffer.save(nbt)
    }
}
