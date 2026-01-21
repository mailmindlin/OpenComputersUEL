package li.cil.oc.common.tileentity

import li.cil.oc.api.network.Node
import li.cil.oc.server.component
import net.minecraft.nbt.NBTTagCompound

class MotionSensor : traits.Environment(), traits.Tickable {
    @JvmField
    val motionSensor = component.MotionSensor(this)

    override fun getNode(): Node = motionSensor.node()

    override fun updateEntity() {
        super.updateEntity()
        if (isServer) {
            motionSensor.update()
        }
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)
        motionSensor.load(nbt)
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)
        motionSensor.save(nbt)
    }
}
