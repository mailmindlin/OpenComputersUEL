package li.cil.oc.common.tileentity

import li.cil.oc.api.network.Node
import li.cil.oc.server.component.Geolyzer as ComponentGeolyzer
import net.minecraft.nbt.NBTTagCompound
import li.cil.oc.common.tileentity.traits.Environment as TraitEnvironment

class Geolyzer: TileEntityBase(), TraitEnvironment {
    @JvmField
    val geolyzer = ComponentGeolyzer(this)

    override fun getNode(): Node = geolyzer.node

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)
        geolyzer.load(nbt)
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)
        geolyzer.save(nbt)
    }
}
