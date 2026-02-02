package li.cil.oc.common.tileentity

import li.cil.oc.api.network.Node
import li.cil.oc.common.tileentity.traits.isServer
import li.cil.oc.server.component.MotionSensor as MotionSensorComponent
import net.minecraft.nbt.NBTTagCompound
import li.cil.oc.common.tileentity.traits.Environment as TraitEnvironment
import li.cil.oc.common.tileentity.traits.Tickable as TraitTickable

class MotionSensor : TileEntityBase.TEEnvironmentBase(), TraitTickable {
    @JvmField
    val motionSensor = MotionSensorComponent(this)

    override fun node() = motionSensor.node()

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
