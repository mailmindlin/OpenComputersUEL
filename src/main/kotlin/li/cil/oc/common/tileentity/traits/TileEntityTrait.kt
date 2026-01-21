package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.SideTracker
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Base trait interface for all OpenComputers tile entities.
 * Provides coordinate access and client/server detection.
 */
interface TileEntityTrait {
    companion object {
        @JvmField
        val IsServerDataTag = Settings.namespace + "isServerData"
    }

    fun asTileEntity(): TileEntity

    val x: Int get() = asTileEntity().pos.x
    val y: Int get() = asTileEntity().pos.y
    val z: Int get() = asTileEntity().pos.z
    val pos: BlockPos get() = asTileEntity().pos
    val world: World? get() = asTileEntity().world
    val position: BlockPosition get() = BlockPosition(x, y, z, world)

    val isClient: Boolean get() = !isServer
    val isServer: Boolean get() = world?.let { !it.isRemote } ?: SideTracker.isServer()

    // Helper to get the block type
    val blockType: net.minecraft.block.Block get() = asTileEntity().blockType
}
