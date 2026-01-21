package li.cil.oc.server.component

import li.cil.oc.api.Network
import li.cil.oc.api.internal.Rotatable
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility

class UpgradeSignInRotatable(private val rotatableHost: EnvironmentHost) : UpgradeSign() {
    override val host: EnvironmentHost
        get() = rotatableHost

    private val rotatable: Rotatable
        get() = rotatableHost as Rotatable

    override val node = Network.newNode(this, Visibility.Network)
        .withComponent("sign", Visibility.Neighbors)
        .withConnector()
        .create()

    // ----------------------------------------------------------------------- //

    @Callback(doc = "function():string -- Get the text on the sign in front of the host.")
    fun getValue(context: Context, args: Arguments): Array<Any?> =
        super.getValue(findSign(rotatable.facing()))

    @Callback(doc = "function(value:string):string -- Set the text on the sign in front of the host.")
    fun setValue(context: Context, args: Arguments): Array<Any?> =
        super.setValue(findSign(rotatable.facing()), args.checkString(0))
}
