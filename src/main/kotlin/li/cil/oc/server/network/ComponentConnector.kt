package li.cil.oc.server.network

import net.minecraft.nbt.NBTTagCompound
import li.cil.oc.api.network.ComponentConnector as NetComponentConnector

interface ComponentConnector : NetComponentConnector, Component, Connector {
    override fun save(nbt: NBTTagCompound) {
        super<Component>.save(nbt)
        super<Connector>.save(nbt)
    }
    override fun load(nbt: NBTTagCompound) {
        super<Component>.load(nbt)
        super<Connector>.load(nbt)
    }
}
