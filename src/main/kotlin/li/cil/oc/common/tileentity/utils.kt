package li.cil.oc.common.tileentity

import net.minecraft.tileentity.TileEntity
import li.cil.oc.util.BlockPosition

val TileEntity.x: Int get() = pos.x
val TileEntity.y: Int get() = pos.y
val TileEntity.z: Int get() = pos.z
val TileEntity.position: BlockPosition get() = BlockPosition(x, y, z, world)