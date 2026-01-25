package li.cil.oc.common.tileentity.traits

import net.minecraft.util.EnumFacing

interface RotationAware {
    /** Converts global face to local face */
    fun toLocal(value: EnumFacing): EnumFacing = value
    /** Converts local face to global face */
    fun toGlobal(value: EnumFacing): EnumFacing = value
}
