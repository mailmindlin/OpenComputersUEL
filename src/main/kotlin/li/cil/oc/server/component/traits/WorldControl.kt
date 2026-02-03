package li.cil.oc.server.component.traits

import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.util.Result
import li.cil.oc.util.result

interface WorldControl : WorldAware, SideRestricted {
    @Callback(doc = "function(side:number):boolean, string -- Checks the contents of the block on the specified sides and returns the findings.")
    fun detect(context: Context, args: Arguments): Result {
        val side = checkSideForAction(args, 0)
        val (something, what) = blockContent(side)
        return result(something, what)
    }
}
