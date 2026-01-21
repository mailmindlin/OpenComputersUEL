package li.cil.oc.server.component.traits

import li.cil.oc.api.machine.Arguments
import net.minecraft.util.EnumFacing

interface SideRestricted {
    fun checkSideForAction(args: Arguments, n: Int): EnumFacing
}
