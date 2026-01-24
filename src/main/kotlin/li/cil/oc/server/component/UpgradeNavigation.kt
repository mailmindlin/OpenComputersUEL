package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.internal.Rotatable
import li.cil.oc.api.internal.Tablet
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.machine.Machine
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.common.Tier
import li.cil.oc.common.item.data.NavigationUpgradeData
import li.cil.oc.server.network.Waypoints
import li.cil.oc.util.BlockPosition
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing

class UpgradeNavigation(val host: EnvironmentHost) : ManagedEnvironmentKt(), DeviceInfo {
    private val rotatable: Rotatable
        get() = host as Rotatable

    override val node = Network.newNode(this, Visibility.Network)
        .withComponent("navigation", Visibility.Neighbors)
        .withConnector()
        .create()

    val data = NavigationUpgradeData()

    override val deviceInfo = mapOf(
        DeviceAttribute.Class to DeviceClass.Generic,
        DeviceAttribute.Description to "Navigation upgrade",
        DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
        DeviceAttribute.Product to "PathFinder v3",
        DeviceAttribute.Capacity to data.getSize(host.world()).toString()
    )

    // ----------------------------------------------------------------------- //

    @Callback(doc = "function():number, number, number -- Get the current relative position of the robot.")
    fun getPosition(context: Context, args: Arguments): Array<Any?> {
        val info = data.mapData(host.world())!!
        val size = data.getSize(host.world())
        val relativeX = host.xPosition() - info.xCenter
        val relativeZ = host.zPosition() - info.zCenter

        return if (Math.abs(relativeX) <= size / 2 && Math.abs(relativeZ) <= size / 2) {
            result(relativeX, host.yPosition(), relativeZ)
        } else {
            result(Unit, "out of range")
        }
    }

    @Callback(doc = "function():number -- Get the current orientation of the robot.")
    fun getFacing(context: Context, args: Arguments): Array<Any?> = result(rotatable.facing().ordinal)

    @Callback(doc = "function():number -- Get the operational range of the navigation upgrade.")
    fun getRange(context: Context, args: Arguments): Array<Any?> = result(data.getSize(host.world) / 2)

    @Callback(doc = "function(range:number):table -- Find waypoints in the specified range.")
    fun findWaypoints(context: Context, args: Arguments): Array<Any?> {
        val range = args.checkDouble(0).coerceIn(0.0, Settings.get.maxWirelessRange[Tier.Two])
        if (range <= 0)
            return result(emptyArray<Any>())
        if (!node.tryChangeBuffer(-range * Settings.get.wirelessCostPerRange[Tier.Two] * 0.25))
            return result(Unit, "not enough energy")
        }
        context.pause(0.5)
        val position = BlockPosition(host)
        val positionVec = position.toVec3()
        val rangeSq = range * range
        val waypoints = Waypoints.findWaypoints(position, range)
            .filter { waypoint -> waypoint.getDistanceSq(positionVec.x, positionVec.y, positionVec.z) <= rangeSq }
        return result(waypoints.map { waypoint ->
            val delta = waypoint.position.offset(waypoint.facing).toVec3().subtract(positionVec)
            mapOf(
                "position" to arrayOf(delta.x, delta.y, delta.z),
                "redstone" to waypoint.maxInput,
                "label" to waypoint.label,
                "address" to waypoint.node.address()
            )
        }.toTypedArray())
    }

    override fun onMessage(message: Message) {
        super.onMessage(message)
        val message = TabletUseMessage.tryParse(message) ?: return

        val nbt = message.nbt
        val blockPos = message.blockPos
        val info = this.data.mapData(host.world)!!

        nbt.setInteger("posX", blockPos.x - info.xCenter)
        nbt.setInteger("posY", blockPos.y)
        nbt.setInteger("posZ", blockPos.z - info.zCenter)
    }

    // ----------------------------------------------------------------------- //

    override fun load(nbt: NBTTagCompound) {
        super.load(nbt)
        data.load(nbt)
    }

    override fun save(nbt: NBTTagCompound) {
        super.save(nbt)
        data.save(nbt)
    }
}
