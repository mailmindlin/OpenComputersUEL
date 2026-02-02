package li.cil.oc.common.event

import li.cil.oc.Settings
import li.cil.oc.api.event.FileSystemAccessEvent
import li.cil.oc.api.internal.Rack
import li.cil.oc.common.tileentity.Case
import li.cil.oc.common.tileentity.DiskDrive
import li.cil.oc.common.tileentity.Raid
import li.cil.oc.server.component.DiskDriveMountable
import li.cil.oc.server.component.Server
import net.minecraft.util.ResourceLocation
import net.minecraft.util.SoundCategory
import net.minecraft.util.SoundEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object FileSystemAccessHandler {
    @JvmStatic
    @SubscribeEvent
    fun onFileSystemAccess(e: FileSystemAccessEvent.Server) {
        val tileEntity = e.tileEntity
        if (tileEntity is Rack) {
            for (slot in 0 until tileEntity.sizeInventory) {
                when (val mountable = tileEntity.getMountable(slot)) {
                    is Server -> {
                        val containsNode = mountable.componentSlot(e.node.address()!!) >= 0
                        if (containsNode) {
                            mountable.lastFileSystemAccess = System.currentTimeMillis()
                            tileEntity.markChanged(slot)
                        }
                    }
                    is DiskDriveMountable -> {
                        val containsNode = mountable.filesystemNode?.equals(e.node) == true
                        if (containsNode) {
                            mountable.lastAccess = System.currentTimeMillis()
                            tileEntity.markChanged(slot)
                        }
                    }
                }
            }
        }
    }

    @JvmStatic
    @SubscribeEvent
    fun onFileSystemAccess(e: FileSystemAccessEvent.Client) {
        val volume = Settings.get.soundVolume
        val sound = SoundEvent(ResourceLocation(e.sound))
        e.world.playSound(e.x, e.y, e.z, sound, SoundCategory.BLOCKS, volume.toFloat(), 1f, false)
        when (val tileEntity = e.tileEntity) {
            is DiskDrive -> tileEntity.lastAccess = System.currentTimeMillis()
            is Case -> tileEntity.lastFileSystemAccess = System.currentTimeMillis()
            is Raid -> tileEntity.lastAccess = System.currentTimeMillis()
        }
    }
}
