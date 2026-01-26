package li.cil.oc.server.component

import li.cil.oc.api.detail.Builder.ComponentBuilder
import li.cil.oc.api.detail.Builder.NodeBuilder
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.network.ComponentConnector
import li.cil.oc.api.Network as NetworkFactory
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab.AbstractManagedEnvironment

abstract class ManagedEnvironmentKt: AbstractManagedEnvironment() {
    protected inline fun nodeFactory(visibility: Visibility = Visibility.Neighbors): NodeBuilder = NetworkFactory.newNode(this, visibility)!!
    protected inline fun nodeFactory(visibility: Visibility, component: String): ComponentBuilder = nodeFactory(visibility).withComponent(component)
    protected inline fun newComponentConnector(visibility: Visibility, component: String): ComponentConnector = nodeFactory(visibility).withComponent(component).withConnector().create()

    internal abstract val node: Node
    override fun node(): Node = this.node
}