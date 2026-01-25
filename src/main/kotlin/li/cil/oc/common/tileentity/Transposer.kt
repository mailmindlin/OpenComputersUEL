package li.cil.oc.common.tileentity

import li.cil.oc.api.network.Node
import li.cil.oc.server.component.Transposer as ComponentTransposer
import net.minecraft.nbt.NBTTagCompound

class Transposer : TileEntityBase.TEEnvironmentBase() {
    private val transposer = ComponentTransposer.Block(this)

    override fun node(): Node = transposer.node

    // Used on client side to check whether to render activity indicators.
    @JvmField
    var lastOperation = 0L

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)
        transposer.load(nbt)
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)
        transposer.save(nbt)
    }
}
