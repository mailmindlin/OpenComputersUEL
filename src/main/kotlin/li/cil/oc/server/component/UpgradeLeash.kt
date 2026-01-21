package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.common.EventHandler
import li.cil.oc.server.component.traits.WorldAware
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedArguments.checkSideAny
import li.cil.oc.util.ExtendedNBT.setNewTagList
import li.cil.oc.util.checkSideAny
import li.cil.oc.util.setNewTagList
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLiving
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagString
import net.minecraftforge.common.util.Constants as NBT
import java.util.*

class UpgradeLeash(val host: Entity) : ManagedEnvironmentKt(), WorldAware, DeviceInfo {
    override val node = Network.newNode(this, Visibility.Network)
        .withComponent("leash")
        .create()

    val MaxLeashedEntities = 8

    override val deviceInfo = mapOf(
        DeviceAttribute.Class to DeviceClass.Generic,
        DeviceAttribute.Description to "Leash",
        DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
        DeviceAttribute.Product to "FlockControl (FC-3LS)",
        DeviceAttribute.Capacity to MaxLeashedEntities.toString()
    )

    val leashedEntities = mutableSetOf<UUID>()

    override val position get() = BlockPosition.apply(host)

    @Callback(doc = "function(side:number):boolean -- Tries to put an entity on the specified side of the device onto a leash.")
    fun leash(context: Context, args: Arguments): Array<Any?>? {
        if (leashedEntities.size >= MaxLeashedEntities) return result(Unit, "too many leashed entities")
        val side = args.checkSideAny(0)
        val nearBounds = position.bounds
        val farBounds = nearBounds.offset(side.xOffset * 2.0, side.yOffset * 2.0, side.zOffset * 2.0)
        val bounds = nearBounds.union(farBounds)
        val entity = entitiesInBounds(EntityLiving::class.java, bounds).find { it.canBeLeashedTo(fakePlayer) }
        return if (entity != null) {
            entity.setLeashHolder(host, true)
            leashedEntities.add(entity.uniqueID)
            context.pause(0.1)
            result(true)
        } else {
            result(Unit, "no unleashed entity")
        }
    }

    @Callback(doc = "function() -- Unleashes all currently leashed entities.")
    fun unleash(context: Context, args: Arguments): Array<Any?>? {
        unleashAll()
        return null
    }

    override fun onDisconnect(node: Node) {
        super.onDisconnect(node)
        if (node == this.node) {
            unleashAll()
        }
    }

    private fun unleashAll() {
        entitiesInBounds(EntityLiving::class.java, position.bounds.grow(5.0, 5.0, 5.0)).forEach { entity ->
            if (leashedEntities.contains(entity.uniqueID) && entity.leashHolder == host) {
                entity.clearLeashed(true, false)
            }
        }
        leashedEntities.clear()
    }

    private val LeashedEntitiesTag = "leashedEntities"

    override fun load(nbt: NBTTagCompound) {
        super.load(nbt)
        val tagList = nbt.getTagList(LeashedEntitiesTag, NBT.NBT.TAG_STRING)
        for (i in 0 until tagList.tagCount()) {
            val tag = tagList.getStringTagAt(i)
            leashedEntities.add(UUID.fromString(tag))
        }
        // Re-acquire leashed entities. Need to do this manually because leashed
        // entities only remember their leashee if it's an EntityLivingBase...
        EventHandler.scheduleServer {
            val foundEntities = mutableSetOf<UUID>()
            entitiesInBounds(EntityLiving::class.java, position.bounds.grow(5.0, 5.0, 5.0)).forEach { entity ->
                if (leashedEntities.contains(entity.uniqueID)) {
                    entity.setLeashHolder(host, true)
                    foundEntities.add(entity.uniqueID)
                }
            }
            val missing = leashedEntities - foundEntities
            if (missing.isNotEmpty()) {
                OpenComputers.log.info("Could not find ${missing.size} leashed entities after loading!")
                leashedEntities.removeAll(missing)
            }
        }
    }

    override fun save(nbt: NBTTagCompound) {
        super.save(nbt)
        nbt.setNewTagList(LeashedEntitiesTag, leashedEntities.map { it.toString() })
    }
}
