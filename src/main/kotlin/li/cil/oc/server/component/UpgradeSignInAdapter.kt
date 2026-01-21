package li.cil.oc.server.component

import li.cil.oc.api.Network
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import li.cil.oc.util.ExtendedArguments.checkSideAny

class UpgradeSignInAdapter(override val host: EnvironmentHost) : UpgradeSign() {
    override val node = Network.newNode(this, Visibility.Network)
        .withComponent("sign", Visibility.Network)
        .withConnector()
        .create()

    // ----------------------------------------------------------------------- //

    @Callback(doc = "function(side:number):string -- Get the text on the sign on the specified side of the adapter.")
    fun getValue(context: Context, args: Arguments): Array<Any?> =
        super.getValue(findSign(args.checkSideAny(0)))

    @Callback(doc = "function(side:number, value:string):string -- Set the text on the sign on the specified side of the adapter.")
    fun setValue(context: Context, args: Arguments): Array<Any?> =
        super.setValue(findSign(args.checkSideAny(0)), args.checkString(1))
}
