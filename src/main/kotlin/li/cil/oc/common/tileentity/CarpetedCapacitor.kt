package li.cil.oc.common.tileentity

import li.cil.oc.Settings
import li.cil.oc.common.tileentity.traits.Tickable as TraitTickable

class CarpetedCapacitor : Capacitor(), TraitTickable {
    private val carpetBonus: Double
        get() = Settings.get.bufferCapacitor * 0.5

    override val maxCapacity: Double
        get() = super.maxCapacity + carpetBonus

    override fun updateEntity() {
        super.updateEntity()
        if (isServer && Settings.get.isTickMultiple(world)) {
            val entity = world.findNearestEntityWithinAABB(
                net.minecraft.entity.passive.EntityOcelot::class.java,
                net.minecraft.util.math.AxisAlignedBB(pos).grow(3.0),
                null
            )
            if (entity != null) {
                node.changeBuffer(Settings.get.ocelotPower)
            }
        }
    }
}
