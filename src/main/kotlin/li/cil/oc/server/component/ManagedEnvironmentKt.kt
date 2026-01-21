package li.cil.oc.server.component

import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.network.Node
import li.cil.oc.api.prefab.AbstractManagedEnvironment

abstract class ManagedEnvironmentKt: AbstractManagedEnvironment(), DeviceInfo {
    internal abstract val node: Node
    override fun node(): Node = this.node
}

interface DeviceInfoKt: DeviceInfo {
    val deviceInfo: Map<String, String>
    override fun getDeviceInfo(): MutableMap<String, String> = deviceInfo.toMutableMap()
}