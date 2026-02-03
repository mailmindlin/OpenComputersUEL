package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.common.tileentity.DiskDrive
import li.cil.oc.common.tileentity.DiskDrive.Companion
import li.cil.oc.server.component.traits.WorldAware
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.Result
import li.cil.oc.util.result
import net.minecraft.entity.Entity
import net.minecraft.entity.IMerchant
import net.minecraft.util.math.Vec3d
import java.util.*

class UpgradeTrading(val host: EnvironmentHost) : ManagedEnvironmentKt(), WorldAware, DeviceInfo {
    override val node = nodeFactory(Visibility.Network)
        .withComponent("trading")
        .create()

    companion object {
        private val deviceInfo = mapOf(
            DeviceAttribute.Class to DeviceClass.Generic,
            DeviceAttribute.Description to "Trading upgrade",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "Capitalism H.O. 1200T"
        )
    }
    override fun getDeviceInfo() = Companion.deviceInfo

    override val position get() = BlockPosition(host)

    private val maxRange get() = Settings.get.tradingRange

    private fun isInRange(entity: Entity): Boolean =
        Vec3d(entity.posX, entity.posY, entity.posZ).distanceTo(position.toVec3()) <= maxRange

    @Suppress("unused", "unused_parameter")
    @Callback(doc = "function():table -- Returns a table of trades in range as userdata objects.")
    fun getTrades(context: Context, args: Arguments): Result {
        val merchants = entitiesInBounds(Entity::class.java, position.bounds.grow(maxRange, maxRange, maxRange))
            .filter { isInRange(it) }
            .filterIsInstance<IMerchant>()
        var nextId = 1
        val idMap = mutableMapOf<UUID, Int>()
        for (id in merchants.map { it.persistentID }.sorted()) {
            idMap[id] = nextId
            nextId += 1
        }
        // sorting the result is not necessary, but will help the merchant trades line up nicely by merchant
        return result(
            merchants
                .sortedBy { it.persistentID }
                .flatMap { merchant ->
                    merchant.getRecipes(null)?.indices?.map { index ->
                        Trade(this, merchant, index, idMap[merchant.persistentID]!!)
                    } ?: emptyList()
                }
        )
    }
}
private val IMerchant.persistentID: UUID
    get() = (this as Entity).persistentID