package li.cil.oc.integration

import li.cil.oc.api.Network
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab.AbstractManagedEnvironment

open class ManagedTileEntityEnvironment<T>(@JvmField protected val tileEntity: T, name: String?) : AbstractManagedEnvironment() {
    init {
        setNode(
            Network.newNode(this, Visibility.Network)!!.withComponent
                (name).create
                ()
        )
    }
}
