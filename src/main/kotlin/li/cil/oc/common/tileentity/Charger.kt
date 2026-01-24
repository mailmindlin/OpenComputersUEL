package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.nanomachines.Controller
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.util.StateAware
import li.cil.oc.common.Slot
import li.cil.oc.common.entity.Drone
import li.cil.oc.integration.util.ItemCharge
import li.cil.oc.server.PacketSender as ServerPacketSender
import li.cil.oc.server.component.DeviceInfoKt
import li.cil.oc.util.BlockPosition
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumParticleTypes
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.util.EnumSet
import li.cil.oc.common.tileentity.traits.Environment as TraitEnvironment
import li.cil.oc.common.tileentity.traits.PowerAcceptor as TraitPowerAcceptor
import li.cil.oc.common.tileentity.traits.RedstoneAware as TraitRedstoneAware
import li.cil.oc.common.tileentity.traits.Rotatable as TraitRotatable
import li.cil.oc.common.tileentity.traits.ComponentInventory as TraitComponentInventory
import li.cil.oc.common.tileentity.traits.Tickable as TraitTickable
import li.cil.oc.common.tileentity.traits.StateAware as TraitStateAware

class Charger : TileEntityBase(), TraitEnvironment, TraitPowerAcceptor, TraitRedstoneAware, TraitRotatable, TraitComponentInventory, TraitTickable, Analyzable, TraitStateAware, DeviceInfoKt {
    @JvmField
    val node: Connector = ApiNetwork.newNode(this, Visibility.None)
        .withConnector(Settings.get.bufferConverter)
        .create()

    override fun getNode(): Node = node

    @JvmField
    val connectors: MutableSet<Chargeable> = mutableSetOf()

    @JvmField
    val equipment: MutableSet<ItemStack> = mutableSetOf()

    @JvmField
    var chargeSpeed = 0.0

    @JvmField
    var hasPower = false

    @JvmField
    var invertSignal = false

    override val deviceInfo: Map<String, String> by lazy {
        mapOf(
            DeviceAttribute.Class to DeviceClass.Generic,
            DeviceAttribute.Description to "Charger",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "PowerUpper"
        )
    }

    // ----------------------------------------------------------------------- //

    @SideOnly(Side.CLIENT)
    override fun hasConnector(side: EnumFacing): Boolean = side != facing()

    override fun connector(side: EnumFacing): Connector? = if (side != facing()) node else null

    override fun energyThroughput(): Double = Settings.get.chargerRate

    override fun getCurrentState(): EnumSet<StateAware.State> {
        // TODO Refine to only report working if present robots/drones actually *need* power.
        return when {
            connectors.isNotEmpty() -> {
                if (hasPower) EnumSet.of(StateAware.State.IsWorking)
                else EnumSet.of(StateAware.State.CanWork)
            }
            else -> EnumSet.noneOf(StateAware.State::class.java)
        }
    }

    override fun onAnalyze(player: EntityPlayer, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Array<Node>? {
        player.sendMessage(Localization.Analyzer.ChargerSpeed(chargeSpeed))
        return null
    }

    // ----------------------------------------------------------------------- //

    private fun chargeStack(stack: ItemStack, charge: Double) {
        if (!stack.isEmpty && charge > 0) {
            val missing = node.changeBuffer(-charge)
            val surplus = ItemCharge.charge(stack, charge + missing) // missing is negative
            node.changeBuffer(surplus)
        }
    }

    override fun updateEntity() {
        super.updateEntity()

        // Offset by hashcode to avoid all chargers ticking at the same time.
        if ((world.worldInfo.worldTotalTime + kotlin.math.abs(hashCode())) % 20 == 0L) {
            updateConnectors()
        }

        if (isServer && Settings.get.isTickMultiple(world.worldInfo.worldTotalTime)) {
            var canCharge = Settings.get.ignorePower

            // Charging of external devices.
            run {
                val charge = Settings.get.chargeRateExternal * chargeSpeed * Settings.get.tickFrequency
                canCharge = canCharge || (charge > 0 && node.globalBuffer() >= charge * 0.5)
                if (canCharge) {
                    for (connector in connectors) {
                        val missing = node.changeBuffer(-charge)
                        val surplus = connector.changeBuffer(charge + missing) // missing is negative
                        node.changeBuffer(surplus)
                    }
                }
            }

            // Charging of internal devices.
            run {
                val charge = Settings.get.chargeRateTablet * chargeSpeed * Settings.get.tickFrequency
                canCharge = canCharge || (charge > 0 && node.globalBuffer() >= charge * 0.5)
                if (canCharge) {
                    for (slot in 0 until sizeInventory) {
                        chargeStack(getStackInSlot(slot), charge)
                    }
                }
            }

            // Charging of equipment
            run {
                val charge = Settings.get.chargeRateTablet * chargeSpeed * Settings.get.tickFrequency
                canCharge = canCharge || (charge > 0 && node.globalBuffer() >= charge * 0.5)
                if (canCharge) {
                    for (stack in equipment) {
                        chargeStack(stack, charge)
                    }
                }
            }

            if (hasPower && !canCharge) {
                hasPower = false
                ServerPacketSender.sendChargerState(this)
            }
            if (!hasPower && canCharge) {
                hasPower = true
                ServerPacketSender.sendChargerState(this)
            }
        }

        if (isClient && chargeSpeed > 0 && hasPower && world.worldInfo.worldTotalTime % 10 == 0L) {
            for (connector in connectors) {
                val position = connector.pos
                val theta = world.rand.nextDouble() * Math.PI
                val phi = world.rand.nextDouble() * Math.PI * 2
                val dx = 0.45 * Math.sin(theta) * Math.cos(phi)
                val dy = 0.45 * Math.sin(theta) * Math.sin(phi)
                val dz = 0.45 * Math.cos(theta)
                world.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, position.x + dx, position.y + dz, position.z + dy, 0.0, 0.0, 0.0)
            }
        }
    }

    override fun onConnect(node: Node) {
        super.onConnect(node)
        if (node == this.node) {
            onNeighborChanged()
        }
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private val ChargeSpeedTag = Settings.namespace + "chargeSpeed"
        private const val ChargeSpeedTagCompat = "chargeSpeed"
        private val HasPowerTag = Settings.namespace + "hasPower"
        private const val HasPowerTagCompat = "hasPower"
        private val InvertSignalTag = Settings.namespace + "invertSignal"
        private const val InvertSignalTagCompat = "invertSignal"
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)
        chargeSpeed = if (nbt.hasKey(ChargeSpeedTagCompat)) {
            maxOf(0.0, minOf(1.0, nbt.getDouble(ChargeSpeedTagCompat)))
        } else {
            maxOf(0.0, minOf(1.0, nbt.getDouble(ChargeSpeedTag)))
        }
        hasPower = if (nbt.hasKey(HasPowerTagCompat)) {
            nbt.getBoolean(HasPowerTagCompat)
        } else {
            nbt.getBoolean(HasPowerTag)
        }
        invertSignal = if (nbt.hasKey(InvertSignalTagCompat)) {
            nbt.getBoolean(InvertSignalTagCompat)
        } else {
            nbt.getBoolean(InvertSignalTag)
        }
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)
        nbt.setDouble(ChargeSpeedTag, chargeSpeed)
        nbt.setBoolean(HasPowerTag, hasPower)
        nbt.setBoolean(InvertSignalTag, invertSignal)
    }

    @SideOnly(Side.CLIENT)
    override fun readFromNBTForClient(nbt: NBTTagCompound) {
        super.readFromNBTForClient(nbt)
        chargeSpeed = nbt.getDouble(ChargeSpeedTag)
        hasPower = nbt.getBoolean(HasPowerTag)
    }

    override fun writeToNBTForClient(nbt: NBTTagCompound) {
        super.writeToNBTForClient(nbt)
        nbt.setDouble(ChargeSpeedTag, chargeSpeed)
        nbt.setBoolean(HasPowerTag, hasPower)
    }

    // ----------------------------------------------------------------------- //

    override fun isComponentSlot(slot: Int, stack: ItemStack): Boolean {
        val driver = Driver.driverFor(stack, javaClass)
        return super.isComponentSlot(slot, stack) && driver != null && driver.slot(stack) == Slot.Tablet
    }

    override fun getSizeInventory(): Int = 1

    override fun isItemValidForSlot(slot: Int, stack: ItemStack): Boolean {
        if (slot == 0) {
            val driver = Driver.driverFor(stack, javaClass)
            if (driver != null && driver.slot(stack) == Slot.Tablet) return true
        }
        return ItemCharge.canCharge(stack)
    }

    // ----------------------------------------------------------------------- //

    override fun updateRedstoneInput(side: EnumFacing) {
        super.updateRedstoneInput(side)
        val signal = minOf(15, input.max())

        chargeSpeed = if (invertSignal) (15 - signal) / 15.0 else signal / 15.0
        if (isServer) {
            ServerPacketSender.sendChargerState(this)
        }
    }

    fun onNeighborChanged() {
        checkRedstoneInputChanged()
        updateConnectors()
    }

    fun updateConnectors() {
        val robots = EnumFacing.values().mapNotNull { side ->
            val blockPos = BlockPosition(this).offset(side)
            if (world.isBlockLoaded(blockPos.toBlockPos())) {
                world.getTileEntity(blockPos.toBlockPos()) as? RobotProxy
            } else null
        }.map { RobotChargeable(it.robot) }

        val bounds = BlockPosition(this).bounds.grow(1.0, 1.0, 1.0)
        val drones = world.getEntitiesWithinAABB(Drone::class.java, bounds).map { DroneChargeable(it) }

        val players = world.getEntitiesWithinAABB(EntityPlayer::class.java, bounds)

        val chargeablePlayers = players.filter { Nanomachines.hasController(it) }.map { PlayerChargeable(it) }

        // Only update list when we have to, keeps pointless block updates to a minimum.
        val newConnectors = robots + drones + chargeablePlayers
        if (connectors.size != newConnectors.size || (connectors.isNotEmpty() && (connectors - newConnectors.toSet()).isNotEmpty())) {
            connectors.clear()
            connectors.addAll(newConnectors)
            world.notifyNeighborsOfStateChange(pos, blockType, false)
        }

        // scan players for chargeable equipment
        equipment.clear()
        for (player in players) {
            for (stack in player.inventory.mainInventory) {
                val driver = Driver.driverFor(stack, javaClass)
                if ((driver != null && driver.slot(stack) == Slot.Tablet) || ItemCharge.canCharge(stack)) {
                    equipment.add(stack)
                }
            }
        }
    }

    interface Chargeable {
        val pos: Vec3d
        fun changeBuffer(delta: Double): Double
    }

    abstract class ConnectorChargeable(val connector: Connector) : Chargeable {
        override fun changeBuffer(delta: Double): Double = connector.changeBuffer(delta)

        override fun equals(other: Any?): Boolean =
            other is ConnectorChargeable && other.connector == connector

        override fun hashCode(): Int = connector.hashCode()
    }

    class RobotChargeable(val robot: Robot) : ConnectorChargeable(robot.node() as Connector) {
        override val pos: Vec3d
            get() = BlockPosition(robot).toVec3()

        override fun equals(other: Any?): Boolean =
            other is RobotChargeable && other.robot == robot

        override fun hashCode(): Int = robot.hashCode()
    }

    class DroneChargeable(val drone: Drone) : ConnectorChargeable(drone.components().node as Connector) {
        override val pos: Vec3d
            get() = Vec3d(drone.posX, drone.posY, drone.posZ)

        override fun equals(other: Any?): Boolean =
            other is DroneChargeable && other.drone == drone

        override fun hashCode(): Int = drone.hashCode()
    }

    class PlayerChargeable(val player: EntityPlayer) : Chargeable {
        override val pos: Vec3d
            get() = Vec3d(player.posX, player.posY, player.posZ)

        override fun changeBuffer(delta: Double): Double {
            val controller = Nanomachines.getController(player)
            return if (controller is Controller) {
                controller.changeBuffer(delta)
            } else {
                delta // Cannot charge.
            }
        }

        override fun equals(other: Any?): Boolean =
            other is PlayerChargeable && other.player == player

        override fun hashCode(): Int = player.hashCode()
    }
}
