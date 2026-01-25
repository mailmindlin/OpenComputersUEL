package li.cil.oc.common.tileentity.traits

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.internal.TextBuffer as InternalTextBuffer
import li.cil.oc.api.network.Node
import li.cil.oc.common.tileentity.behaviors.Behavior
import li.cil.oc.common.tileentity.behaviors.BehaviorUpdate
import li.cil.oc.common.tileentity.behaviors.NbtSeriailzable
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

interface TextBuffer : Environment, Tickable {
    val textBufferDelegate: Delegate

    class Delegate(val tile: TextBuffer, val tier: Int): Behavior, NbtSeriailzable, BehaviorUpdate {
        val buffer: InternalTextBuffer by lazy {
            val screenItem = ApiItems.get(Constants.BlockName.ScreenTier1).createItemStack(1)
            val buf = Driver.driverFor(screenItem, tile.javaClass).createEnvironment(screenItem, tile) as InternalTextBuffer
            val (maxWidth, maxHeight) = Settings.screenResolutionsByTier[tier]
            buf.setMaximumResolution(maxWidth, maxHeight)
            buf.setMaximumColorDepth(Settings.screenDepthsByTier[tier])
            buf
        }

        override fun update() {
            if (tile.isClient || tile.isConnected) {
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

    override fun node(): Node = textBufferDelegate.buffer.node()
}
