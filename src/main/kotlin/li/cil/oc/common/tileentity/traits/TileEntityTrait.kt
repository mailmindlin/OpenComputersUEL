package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.SideTracker
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World


val TileEntityTrait.x: Int get() = asTileEntity().pos.x
val TileEntityTrait.y: Int get() = asTileEntity().pos.y
val TileEntityTrait.z: Int get() = asTileEntity().pos.z
val TileEntityTrait.pos: BlockPos get() = asTileEntity().pos
val TileEntityTrait.world: World? get() = asTileEntity().world
val TileEntityTrait.position: BlockPosition get() = BlockPosition(x, y, z, world)
val TileEntityTrait.isClient: Boolean get() = !isServer
val TileEntityTrait.isServer: Boolean get() = world?.let { !it.isRemote } ?: SideTracker.isServer()
// Helper to get the block type
val TileEntityTrait.blockType: net.minecraft.block.Block get() = asTileEntity().blockType

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
}
