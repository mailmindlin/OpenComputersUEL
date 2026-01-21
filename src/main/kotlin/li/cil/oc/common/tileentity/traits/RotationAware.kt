package li.cil.oc.common.tileentity.traits

import net.minecraft.util.EnumFacing

interface RotationAware {
    fun toLocal(value: EnumFacing): EnumFacing? = value
    fun toGlobal(value: EnumFacing): EnumFacing? = value
}
