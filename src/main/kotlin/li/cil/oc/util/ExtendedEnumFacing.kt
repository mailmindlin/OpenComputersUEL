package li.cil.oc.util

import net.minecraft.util.EnumFacing

// Copy-pasta from old Forge's ForgeDirection, because MC's equivalent in EnumFacing is client side only \o/
private val ROTATION_MATRIX = arrayOf(
    intArrayOf(0, 1, 4, 5, 3, 2, 6),
    intArrayOf(0, 1, 5, 4, 2, 3, 6),
    intArrayOf(5, 4, 2, 3, 0, 1, 6),
    intArrayOf(4, 5, 2, 3, 1, 0, 6),
    intArrayOf(2, 3, 1, 0, 4, 5, 6),
    intArrayOf(3, 2, 0, 1, 4, 5, 6),
    intArrayOf(0, 1, 2, 3, 4, 5, 6)
)

fun EnumFacing.getRotation(axis: EnumFacing): EnumFacing
    = EnumFacing.byIndex(ROTATION_MATRIX[axis.ordinal][this.ordinal])
