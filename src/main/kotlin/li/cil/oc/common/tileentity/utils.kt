package li.cil.oc.common.tileentity

import net.minecraft.tileentity.TileEntity

val TileEntity.x: Int get() = pos.x
val TileEntity.y: Int get() = pos.y
val TileEntity.z: Int get() = pos.z