package li.cil.oc.common.tileentity

import li.cil.oc.api.network.Node
import net.minecraft.nbt.NBTTagCompound
import li.cil.oc.server.component.Geolyzer as ComponentGeolyzer

class Geolyzer: TileEntityBase.TEEnvironmentBase() {
    @JvmField
    val geolyzer = ComponentGeolyzer(this)

    override fun node() = geolyzer.node

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)
        geolyzer.load(nbt)
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)
        geolyzer.save(nbt)
    }
}
