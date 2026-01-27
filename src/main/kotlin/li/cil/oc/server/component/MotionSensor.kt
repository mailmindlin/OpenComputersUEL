package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import li.cil.oc.util.SideTracker
import net.minecraft.entity.EntityLivingBase
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.potion.Potion
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.Vec3d

class MotionSensor(val host: EnvironmentHost) : ManagedEnvironmentKt(), DeviceInfo {
    override val node = newComponentConnector(Visibility.Network, "motion_sensor")

    private val radius = 8

    private var sensitivity = 0.4

    private val trackedEntities = mutableMapOf<EntityLivingBase, Triple<Double, Double, Double>>()

    private val deviceInfo_ by lazy {
        mapOf(
            DeviceAttribute.Class to DeviceClass.Generic,
            DeviceAttribute.Description to "Motion sensor",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "Blinker M1K0",
            DeviceAttribute.Capacity to radius.toString()
        )
    }

    override fun getDeviceInfo() = deviceInfo_

    // ----------------------------------------------------------------------- //

    private val world get() = host.world()
    private val x get() = host.xPosition()
    private val y get() = host.yPosition()
    private val z get() = host.zPosition()

    private val isServer: Boolean
        get() = if (world != null) !world.isRemote else SideTracker.isServer()

    override fun canUpdate(): Boolean = isServer

    override fun update() {
        super.update()
        if (world.totalWorldTime % 10 == 0L) {
            // Get a list of all living entities we could possibly detect, using a rough
            // bounding box check, then refining it using the actual distance and an
            // actual visibility check.
            val entities = world.getEntitiesWithinAABB(EntityLivingBase::class.java, sensorBounds)
                .filterIsInstance<EntityLivingBase>()
                .filter { entity -> entity.isEntityAlive && isInRange(entity) && isVisible(entity) }
                .toSet()
            // Get rid of all tracked entities that are no longer visible.
            trackedEntities.keys.retainAll(entities)
            // Check for which entities we should generate a signal.
            for (entity in entities) {
                val prevPos = trackedEntities[entity]
                if (prevPos != null) {
                    val (prevX, prevY, prevZ) = prevPos
                    // Known entity, check if it moved enough to trigger.
                    if (entity.getDistanceSq(prevX, prevY, prevZ) > sensitivity * sensitivity * 2) {
                        sendSignal(entity)
                    }
                } else {
                    // New, unknown entity, always trigger.
                    sendSignal(entity)
                }
                // Update tracked position.
                trackedEntities[entity] = Triple(entity.posX, entity.posY, entity.posZ)
            }
        }
    }

    private val sensorBounds: AxisAlignedBB
        get() = AxisAlignedBB(
            x + 0.5 - radius, y + 0.5 - radius, z + 0.5 - radius,
            x + 0.5 + radius, y + 0.5 + radius, z + 0.5 + radius
        )

    private fun isInRange(entity: EntityLivingBase): Boolean =
        entity.getDistanceSq(x + 0.5, y + 0.5, z + 0.5) <= radius * radius

    private fun isClearPath(target: Vec3d): Boolean {
        val origin = Vec3d(x, y, z)
        val path = target.subtract(origin).normalize()
        val eye = origin.add(path)
        return world.rayTraceBlocks(eye, target) == null
    }

    private fun isVisible(entity: EntityLivingBase): Boolean =
        entity.getActivePotionEffect(Potion.getPotionFromResourceLocation("invisibility")) == null &&
                // Note: it only working in lit conditions works and is neat, but this
                // is pseudo-infrared driven (it only works for *living* entities, after
                // all), so I think it makes more sense for it to work in the dark, too.
                /* entity.getBrightness(0) > 0.2 && */ run {
            val target = entity.positionVector
            isClearPath(target) || isClearPath(target.add(0.0, entity.eyeHeight.toDouble(), 0.0))
        }

    private fun sendSignal(entity: EntityLivingBase) {
        if (Settings.get.inputUsername) {
            node.sendToReachable(
                "computer.signal", "motion",
                entity.posX - (x + 0.5), entity.posY - (y + 0.5), entity.posZ - (z + 0.5),
                entity.name
            )
        } else {
            node.sendToReachable(
                "computer.signal", "motion",
                entity.posX - (x + 0.5), entity.posY - (y + 0.5), entity.posZ - (z + 0.5)
            )
        }
    }

    // ----------------------------------------------------------------------- //

    @Callback(direct = true, doc = "function():number -- Gets the current sensor sensitivity.")
    fun getSensitivity(computer: Context, args: Arguments): Array<Any?> = result(sensitivity)

    @Callback(direct = true, doc = "function(value:number):number -- Sets the sensor's sensitivity. Returns the old value.")
    fun setSensitivity(computer: Context, args: Arguments): Array<Any?> {
        val oldValue = sensitivity
        sensitivity = maxOf(0.2, args.checkDouble(0))
        return result(oldValue)
    }

    // ---------------------------------------------------------------------- //

    private val SensitivityTag = Settings.namespace + "sensitivity"

    override fun load(nbt: NBTTagCompound) {
        super.load(nbt)
        sensitivity = nbt.getDouble(SensitivityTag)
    }

    override fun save(nbt: NBTTagCompound) {
        super.save(nbt)
        nbt.setDouble(SensitivityTag, sensitivity)
    }

    // ----------------------------------------------------------------------- //
}
