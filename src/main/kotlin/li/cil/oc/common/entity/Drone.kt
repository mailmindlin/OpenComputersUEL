package li.cil.oc.common.entity

import li.cil.oc.Constants
import li.cil.oc.Localization
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.api.Network
import li.cil.oc.api.internal.MultiTank
import li.cil.oc.api.internal.Rotatable
import li.cil.oc.api.machine.Context
import li.cil.oc.api.machine.Machine
import li.cil.oc.api.machine.MachineHost
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Node
import li.cil.oc.common.EventHandler
import li.cil.oc.common.GuiType
import li.cil.oc.common.inventory.ComponentInventory
import li.cil.oc.common.inventory.Inventory
import li.cil.oc.common.item.data.DroneData
import li.cil.oc.integration.util.Wrench
import li.cil.oc.server.agent.Player
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.getBlockSafe
import li.cil.oc.util.setNewCompoundTag
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.entity.Entity
import net.minecraft.entity.MoverType
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.datasync.DataParameter
import net.minecraft.network.datasync.DataSerializers
import net.minecraft.network.datasync.EntityDataManager
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import net.minecraftforge.fluids.IFluidTank
import java.util.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round
import li.cil.oc.api.Machine as MachineFactory
import li.cil.oc.server.agent.Player as AgentPlayer
import li.cil.oc.server.component.Drone as ComponentDrone

// internal.Rotatable is also in internal.Drone, but it wasn't since the start
// so this is to ensure it is implemented here, in the very unlikely case that
// someone decides to ship that specific version of the API.
class Drone(world: World) : Entity(world), MachineHost, li.cil.oc.api.internal.Drone, Rotatable, Analyzable, Context {
    override fun world(): World = entityWorld

    // Some basic constants.
    val gravity = 0.05f
    // low for slow fall (float down)
    val drag = 0.8f
    val maxAcceleration = 0.1f
    val maxVelocity = 0.4f
    val maxInventorySize = 8

    init {
        setSize(12 / 16f, 6 / 16f)
        isImmuneToFire = true
    }

    // Rendering stuff, purely eyecandy.
    val targetFlapAngles: Array<FloatArray> = Array(4) { FloatArray(2) { 0f } }
    val flapAngles: Array<FloatArray> = Array(4) { FloatArray(2) { 0f } }
    var nextFlapChange = 0
    var bodyAngle: Float = (Math.random() * 90).toFloat()
    var angularVelocity = 0f
    var nextAngularVelocityChange = 0
    var lastEnergyUpdate = 0

    // Logic stuff, components, machine and such.
    val info = DroneData()
    val machine: Machine? = if (!world.isRemote) {
        val m = MachineFactory.create(this)!!
        (m.node() as Connector).setLocalBufferSize(0.0)
        m
    } else null
    override fun machine(): Machine? = machine

    val control: ComponentDrone? = if (!world.isRemote) ComponentDrone(this) else null

    val components = object : ComponentInventory {
        override val componentInventoryDelegate = ComponentInventory.State()
        override val host: Drone get() = this@Drone
        override val items: Array<ItemStack> get() = info.components

        override fun getSizeInventory(): Int = info.components.size

        override fun markDirty() {}

        override fun isItemValidForSlot(slot: Int, stack: ItemStack) = true

        override fun isUsableByPlayer(player: EntityPlayer) = true

        override fun node(): Node? = machine?.node()

        override fun onConnect(node: Node) {}

        override fun onDisconnect(node: Node) {}

        override fun onMessage(message: Message) {}
    }

    object EquiptmentInventory: Inventory {
        override val items = emptyArray<ItemStack>()

        override fun getSizeInventory() = 0

        override fun getInventoryStackLimit() = 0

        override fun markDirty() {}

        override fun isItemValidForSlot(slot: Int, stack: ItemStack) = false

        override fun isUsableByPlayer(player: EntityPlayer) = false
    }

    val equipmentInventory = EquiptmentInventory
    override fun equipmentInventory(): IInventory = equipmentInventory

    inner class MainInventory: Inventory {
        override val items: Array<ItemStack> = Array(8) { ItemStack.EMPTY }

        override fun getSizeInventory(): Int = inventorySize

        override fun getInventoryStackLimit() = 64

        override fun markDirty() {} // TODO update client GUI?

        override fun isItemValidForSlot(slot: Int, stack: ItemStack): Boolean = slot >= 0 && slot < sizeInventory

        override fun isUsableByPlayer(player: EntityPlayer): Boolean = player.getDistanceSq(this@Drone) < 64
    }

    val mainInventory = MainInventory()
    override fun mainInventory(): IInventory = mainInventory

    val tank = object : MultiTank {
        override fun tankCount(): Int = components.components.count { it is IFluidTank }

        override fun getFluidTank(index: Int): IFluidTank = components.components
            .filterIsInstance<IFluidTank>()
            .elementAt(index)
    }
    override fun tank(): MultiTank = tank

    @JvmField
    var selectedTank = 0
    override fun selectedTank(): Int = selectedTank

    override fun setSelectedTank(index: Int) {
        selectedTank = index
    }

    override fun tier(): Int = info.tier

    override fun player(): EntityPlayer {
        Player.updatePositionAndRotation(player_, facing(), facing())
        Player.setInventoryPlayerItems(player_)
        return player_
    }

    override fun name(): String = info.name

    override fun setName(name: String) {
        info.name = name
    }

    var ownerName: String = Settings.get.fakePlayerName
    override fun ownerName(): String = ownerName

    var ownerUUID: UUID = Settings.get.fakePlayerProfile.id
    override fun ownerUUID(): UUID = ownerUUID

    private val player_ by lazy { AgentPlayer(this) }

    // ----------------------------------------------------------------------- //
    // Forward context stuff to our machine. Interface needed for some components
    // to work correctly (such as the chunkloader upgrade).

    override fun node(): Node? = machine?.node()

    override fun canInteract(player: String): Boolean = machine!!.canInteract(player)

    override fun isPaused(): Boolean = machine!!.isPaused

    override fun start(): Boolean {
        if (world.isRemote || machine!!.isRunning) {
            return false
        }
        preparePowerUp()
        return machine.start()
    }

    override fun pause(seconds: Double): Boolean = machine!!.pause(seconds)

    override fun stop(): Boolean = machine!!.stop()

    override fun consumeCallBudget(callCost: Double) = machine!!.consumeCallBudget(callCost)

    override fun signal(name: String, vararg args: Any?): Boolean = machine!!.signal(name, *args)

    // ----------------------------------------------------------------------- //

    override fun getTarget(): Vec3d = Vec3d(targetX.toDouble(), targetY.toDouble(), targetZ.toDouble())

    override fun setTarget(value: Vec3d) {
        targetX = value.x.toFloat()
        targetY = value.y.toFloat()
        targetZ = value.z.toFloat()
    }

    override fun getVelocity(): Vec3d = Vec3d(motionX, motionY, motionZ)

    // ----------------------------------------------------------------------- //

    override fun canBeCollidedWith() = true

    override fun canBePushed() = true

    // ----------------------------------------------------------------------- //

    override fun xPosition(): Double = posX

    override fun yPosition(): Double = posY

    override fun zPosition(): Double = posZ

    override fun markChanged() {}

    // ----------------------------------------------------------------------- //

    override fun facing(): EnumFacing = EnumFacing.SOUTH
    override fun toLocal(value: EnumFacing): EnumFacing = value
    override fun toGlobal(value: EnumFacing): EnumFacing = value

    // ----------------------------------------------------------------------- //

    override fun onAnalyze(player: EntityPlayer, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Array<Node> = arrayOf(machine!!.node()!!)

    // ----------------------------------------------------------------------- //

    override fun internalComponents(): Iterable<ItemStack> = info.components.asIterable()

    override fun componentSlot(address: String): Int = components.components.indexOfFirst { env ->
        env?.node() != null && env.node()?.address() == address
    }

    override fun onMachineConnect(node: Node) {}

    override fun onMachineDisconnect(node: Node) {}

    private fun computeInventorySize(): Int = minOf(maxInventorySize, info.components.sumOf { component ->
        if (component != null && !component.isEmpty) {
            val driver = Driver.driverFor(component, javaClass)
            if (driver is li.cil.oc.api.driver.item.Inventory) {
                maxOf(1, driver.inventoryCapacity(component) / 4)
            } else 0
        } else 0
    })

    // ----------------------------------------------------------------------- //

    override fun entityInit() {
        dataManager.register(DataRunning, java.lang.Boolean.FALSE)
        dataManager.register(DataTargetX, java.lang.Float.valueOf(0f))
        dataManager.register(DataTargetY, java.lang.Float.valueOf(0f))
        dataManager.register(DataTargetZ, java.lang.Float.valueOf(0f))
        dataManager.register(DataMaxAcceleration, java.lang.Float.valueOf(0f))
        dataManager.register(DataSelectedSlot, Integer.valueOf(0))
        dataManager.register(DataCurrentEnergy, Integer.valueOf(0))
        dataManager.register(DataMaxEnergy, Integer.valueOf(100))
        dataManager.register(DataStatusText, "")
        dataManager.register(DataInventorySize, Integer.valueOf(0))
        dataManager.register(DataLightColor, Integer.valueOf(0x66DD55))
    }

    fun initializeAfterPlacement(stack: ItemStack, player: EntityPlayer, position: Vec3d) {
        info.load(stack)
        val cNode = control!!.node()!!
        cNode.changeBuffer(info.storedEnergy - cNode.localBuffer())
        wireThingsTogether()
        inventorySize = computeInventorySize()
        setPosition(position.x, position.y, position.z)
    }

    fun preparePowerUp() {
        targetX = floor(posX).toFloat() + 0.5f
        targetY = round(posY).toFloat() + 0.5f
        targetZ = floor(posZ).toFloat() + 0.5f
        targetAcceleration = maxAcceleration

        wireThingsTogether()
    }

    private fun wireThingsTogether() {
        Network.joinNewNetwork(machine!!.node())
        machine.node()!!.connect(control!!.node())
        machine.setCostPerTick(Settings.get.droneCost)
        components.connectComponents()
    }

    var isRunning: Boolean
        @JvmName("getIsRunning")
        get() = dataManager.get(DataRunning)
        set(value) = dataManager.set(DataRunning, java.lang.Boolean.valueOf(value))

    override fun isRunning(): Boolean = this.isRunning

    var targetX: Float
        get() = dataManager.get(DataTargetX)
        // Round target values to low accuracy to avoid floating point errors accumulating.
        set(value) = dataManager.set(DataTargetX, java.lang.Float.valueOf(Math.round(value * 4) / 4f))

    var targetY: Float
        get() = dataManager.get(DataTargetY)
        set(value) = dataManager.set(DataTargetY, java.lang.Float.valueOf(Math.round(value * 4) / 4f))

    var targetZ: Float
        get() = dataManager.get(DataTargetZ)
        set(value) = dataManager.set(DataTargetZ, java.lang.Float.valueOf(Math.round(value * 4) / 4f))

    var targetAcceleration: Float
        get() = dataManager.get(DataMaxAcceleration)
        set(value) = dataManager.set(DataMaxAcceleration, java.lang.Float.valueOf(maxOf(0f, minOf(maxAcceleration, value))))

    var selectedSlot: Int
        get() = dataManager.get(DataSelectedSlot) and 0xFF
        @JvmName("_setSelectedSlot")
        set(value) = dataManager.set(DataSelectedSlot, Integer.valueOf(value.toByte().toInt()))

    override fun selectedSlot(): Int = selectedSlot
    override fun setSelectedSlot(index: Int) {
        selectedTank = index
    }

    var globalBuffer: Int
        get() = dataManager.get(DataCurrentEnergy)
        set(value) = dataManager.set(DataCurrentEnergy, Integer.valueOf(value))

    var globalBufferSize: Int
        get() = dataManager.get(DataMaxEnergy)
        set(value) = dataManager.set(DataMaxEnergy, Integer.valueOf(value))

    var statusText: String
        get() = dataManager.get(DataStatusText)
        set(value) = dataManager.set(DataStatusText, (value ?: "").lines().map { it.take(10) }.take(2).joinToString("\n"))

    var inventorySize: Int
        get() = dataManager.get(DataInventorySize) and 0xFF
        set(value) = dataManager.set(DataInventorySize, Integer.valueOf(value.toByte().toInt()))

    var lightColor: Int
        get() = dataManager.get(DataLightColor)
        set(value) = dataManager.set(DataLightColor, Integer.valueOf(value))

    override fun setPositionAndRotationDirect(x: Double, y: Double, z: Double, yaw: Float, pitch: Float, posRotationIncrements: Int, teleport: Boolean) {
        // Only set exact position if we're too far away from the server's
        // position, otherwise keep interpolating. This removes jitter and
        // is good enough for drones.
        if (!isRunning || getDistanceSq(x, y, z) > 1) {
            super.setPositionAndRotation(x, y, z, yaw, pitch)
        } else {
            targetX = x.toFloat()
            targetY = y.toFloat()
            targetZ = z.toFloat()
        }
    }

    override fun onUpdate() {
        super.onUpdate()

        if (!world.isRemote) {
            if (isInsideOfMaterial(Material.WATER) || isInsideOfMaterial(Material.LAVA)) {
                // We're not water-proof!
                machine!!.stop()
            }
            machine!!.update()
            components.updateComponents()
            isRunning = machine.isRunning

            val buffer = Math.round((machine.node() as Connector).globalBuffer()).toInt()
            if (abs(lastEnergyUpdate - buffer) > 1 || world.totalWorldTime % 200 == 0L) {
                lastEnergyUpdate = buffer
                globalBuffer = buffer
                globalBufferSize = (machine.node() as Connector).globalBufferSize().toInt()
            }
        } else {
            if (isRunning) {
                // Client side update; occasionally update wing pitch and rotation to
                // make the drones look a bit more dynamic.
                val rng = world.rand
                nextFlapChange -= 1
                nextAngularVelocityChange -= 1

                if (nextFlapChange < 0) {
                    nextFlapChange = 5 + rng.nextInt(10)
                    for (i in 0 until 2) {
                        val flap = rng.nextInt(targetFlapAngles.size)
                        targetFlapAngles[flap][0] = Math.toRadians((rng.nextFloat() * 4 - 2).toDouble()).toFloat()
                        targetFlapAngles[flap][1] = Math.toRadians((rng.nextFloat() * 4 - 2).toDouble()).toFloat()
                    }
                }

                if (nextAngularVelocityChange < 0) {
                    if (angularVelocity != 0f) {
                        angularVelocity = 0f
                        nextAngularVelocityChange = 20
                    } else {
                        angularVelocity = if (rng.nextBoolean()) 0.1f else -0.1f
                        nextAngularVelocityChange = 100
                    }
                }

                // Interpolate wing rotations.
                for (i in flapAngles.indices) {
                    flapAngles[i][0] = flapAngles[i][0] * 0.7f + targetFlapAngles[i][0] * 0.3f
                    flapAngles[i][1] = flapAngles[i][1] * 0.7f + targetFlapAngles[i][1] * 0.3f
                }

                // Update body rotation.
                bodyAngle += angularVelocity
            }
        }

        prevPosX = posX
        prevPosY = posY
        prevPosZ = posZ
        noClip = pushOutOfBlocks(posX, (entityBoundingBox.minY + entityBoundingBox.maxY) / 2, posZ)

        if (isRunning) {
            val toTarget = Vec3d(targetX - posX, targetY - posY, targetZ - posZ)
            val distance = toTarget.length()
            val velocity = Vec3d(motionX, motionY, motionZ)
            if (distance > 0 && (distance > 0.005f || velocity.dotProduct(velocity) > 0.005f)) {
                val acceleration = minOf(targetAcceleration, distance.toFloat()) / distance
                val velocityX = velocity.x + toTarget.x * acceleration
                val velocityY = velocity.y + toTarget.y * acceleration
                val velocityZ = velocity.z + toTarget.z * acceleration
                motionX = maxOf(-maxVelocity.toDouble(), minOf(maxVelocity.toDouble(), velocityX))
                motionY = maxOf(-maxVelocity.toDouble(), minOf(maxVelocity.toDouble(), velocityY))
                motionZ = maxOf(-maxVelocity.toDouble(), minOf(maxVelocity.toDouble(), velocityZ))
            } else {
                motionX = 0.0
                motionY = 0.0
                motionZ = 0.0
                posX = targetX.toDouble()
                posY = targetY.toDouble()
                posZ = targetZ.toDouble()
            }
        } else {
            // No power, free fall: engage!
            motionY -= gravity
        }

        move(MoverType.SELF, motionX, motionY, motionZ)

        // Make sure we don't get infinitely faster.
        if (isRunning) {
            motionX *= drag
            motionY *= drag
            motionZ *= drag
        } else {
            val groundDrag = world.getBlockSafe(BlockPosition(this as Entity).offset(EnumFacing.DOWN))!!.slipperiness * drag
            motionX *= groundDrag
            motionY *= drag
            motionZ *= groundDrag
            if (onGround) {
                motionY *= -0.5
            }
        }
    }

    override fun hitByEntity(entity: Entity): Boolean {
        if (isRunning) {
            val direction = Vec3d(entity.posX - posX, entity.posY + entity.eyeHeight - posY, entity.posZ - posZ).normalize()
            if (!world.isRemote) {
                if (Settings.get.inputUsername)
                    machine!!.signal("hit", java.lang.Double.valueOf(direction.x), java.lang.Double.valueOf(direction.z), java.lang.Double.valueOf(direction.y), entity.name)
                else
                    machine!!.signal("hit", java.lang.Double.valueOf(direction.x), java.lang.Double.valueOf(direction.z), java.lang.Double.valueOf(direction.y))
            }
            motionX = (motionX - direction.x) * 0.5f
            motionY = (motionY - direction.y) * 0.5f
            motionZ = (motionZ - direction.z) * 0.5f
        }
        return super.hitByEntity(entity)
    }

    override fun processInitialInteract(player: EntityPlayer, hand: EnumHand): Boolean {
        if (isDead) return false
        if (player.isSneaking) {
            if (Wrench.isWrench(player.heldItemMainhand)) {
                if (!world.isRemote) {
                    outOfWorld()
                }
            } else if (!world.isRemote && !machine!!.isRunning) {
                start()
            }
        } else if (!world.isRemote) {
            player.openGui(OpenComputers.INSTANCE, GuiType.Drone.id, world, entityId, 0, 0)
        }
        return true
    }

    // No step sounds. Except on that one day.
    override fun playStepSound(pos: BlockPos, block: Block) {
        if (EventHandler.isItTime) super.playStepSound(pos, block)
    }

    // ----------------------------------------------------------------------- //

    private var isChangingDimension = false

    override fun changeDimension(dimension: Int): Entity? {
        // Store relative target as target, to allow adding that in our "new self"
        // (entities get re-created after changing dimension).
        targetX = (targetX - posX).toFloat()
        targetY = (targetY - posY).toFloat()
        targetZ = (targetZ - posZ).toFloat()
        return try {
            isChangingDimension = true
            super.changeDimension(dimension)
        } finally {
            isChangingDimension = false
            setDead() // Again, to actually close old machine state after copying it.
        }
    }

    override fun copyDataFromOld(entity: Entity) {
        super.copyDataFromOld(entity)
        // Compute relative target based on old position and update, because our
        // frame of reference most certainly changed (i.e. we'll spawn at different
        // coordinates than the ones we started traveling from, e.g. when porting
        // to the nether it'll be oldpos / 8).
        when (entity) {
            is Drone -> {
                targetX = (posX + entity.targetX).toFloat()
                targetY = (posY + entity.targetY).toFloat()
                targetZ = (posZ + entity.targetZ).toFloat()
            }
            else -> {
                targetX = posX.toFloat()
                targetY = posY.toFloat()
                targetZ = posZ.toFloat()
            }
        }
    }

    override fun setDead() {
        super.setDead()
        if (!world.isRemote && !isChangingDimension) {
            machine!!.stop()
            machine.node()!!.remove()
            components.disconnectComponents()
            components.saveComponents()
        }
    }

    override fun outOfWorld() {
        if (isDead) return
        super.outOfWorld()
        if (!world.isRemote) {
            val stack = Constants.ItemInfo.Drone.createItemStack(1)
            info.storedEnergy = control!!.node()!!.localBuffer().toInt()
            info.save(stack)
            val entity = EntityItem(world, posX, posY, posZ, stack)
            entity.setPickupDelay(15)
            world.spawnEntity(entity)
            InventoryUtils.dropAllSlots(BlockPosition(this as Entity), mainInventory)
        }
    }

    override fun getName(): String = Localization.localizeImmediately("entity.oc.Drone.name")

    override fun handleWaterMovement(): Boolean {
        inWater = world.handleMaterialAcceleration(entityBoundingBox, Material.WATER, this)
        return inWater
    }

    override fun readEntityFromNBT(nbt: NBTTagCompound) {
        info.load(nbt.getCompoundTag("info"))
        inventorySize = computeInventorySize()
        if (!world.isRemote) {
            machine!!.load(nbt.getCompoundTag("machine"))
            control!!.load(nbt.getCompoundTag("control"))
            components.load(nbt.getCompoundTag("components"))
            mainInventory.load(nbt.getCompoundTag("inventory"))

            wireThingsTogether()
        }
        targetX = nbt.getFloat("targetX")
        targetY = nbt.getFloat("targetY")
        targetZ = nbt.getFloat("targetZ")
        targetAcceleration = nbt.getFloat("targetAcceleration")
        selectedSlot = nbt.getByte("selectedSlot").toInt() and 0xFF
        selectedTank = nbt.getByte("selectedTank").toInt() and 0xFF
        statusText = nbt.getString("statusText")
        lightColor = nbt.getInteger("lightColor")
        if (nbt.hasKey("owner")) {
            ownerName = nbt.getString("owner")
        }
        if (nbt.hasKey("ownerUuid")) {
            ownerUUID = UUID.fromString(nbt.getString("ownerUuid"))
        }
    }

    override fun writeEntityToNBT(nbt: NBTTagCompound) {
        if (world.isRemote) return
        components.saveComponents()
        info.storedEnergy = globalBuffer
        nbt.setNewCompoundTag("info", info::save)
        if (!world.isRemote) {
            nbt.setNewCompoundTag("machine", machine!!::save)
            nbt.setNewCompoundTag("control", control!!::save)
            nbt.setNewCompoundTag("components", components::save)
            nbt.setNewCompoundTag("inventory", mainInventory::save)
        }
        nbt.setFloat("targetX", targetX)
        nbt.setFloat("targetY", targetY)
        nbt.setFloat("targetZ", targetZ)
        nbt.setFloat("targetAcceleration", targetAcceleration)
        nbt.setByte("selectedSlot", selectedSlot.toByte())
        nbt.setByte("selectedTank", selectedTank.toByte())
        nbt.setString("statusText", statusText)
        nbt.setInteger("lightColor", lightColor)
        nbt.setString("owner", ownerName)
        nbt.setString("ownerUuid", ownerUUID.toString())
    }

    companion object {
        @JvmField
        val DataRunning: DataParameter<Boolean> = EntityDataManager.createKey(Drone::class.java, DataSerializers.BOOLEAN)
        @JvmField
        val DataTargetX: DataParameter<Float> = EntityDataManager.createKey(Drone::class.java, DataSerializers.FLOAT)
        @JvmField
        val DataTargetY: DataParameter<Float> = EntityDataManager.createKey(Drone::class.java, DataSerializers.FLOAT)
        @JvmField
        val DataTargetZ: DataParameter<Float> = EntityDataManager.createKey(Drone::class.java, DataSerializers.FLOAT)
        @JvmField
        val DataMaxAcceleration: DataParameter<Float> = EntityDataManager.createKey(Drone::class.java, DataSerializers.FLOAT)
        @JvmField
        val DataSelectedSlot: DataParameter<Int> = EntityDataManager.createKey(Drone::class.java, DataSerializers.VARINT)
        @JvmField
        val DataCurrentEnergy: DataParameter<Int> = EntityDataManager.createKey(Drone::class.java, DataSerializers.VARINT)
        @JvmField
        val DataMaxEnergy: DataParameter<Int> = EntityDataManager.createKey(Drone::class.java, DataSerializers.VARINT)
        @JvmField
        val DataStatusText: DataParameter<String> = EntityDataManager.createKey(Drone::class.java, DataSerializers.STRING)
        @JvmField
        val DataInventorySize: DataParameter<Int> = EntityDataManager.createKey(Drone::class.java, DataSerializers.VARINT)
        @JvmField
        val DataLightColor: DataParameter<Int> = EntityDataManager.createKey(Drone::class.java, DataSerializers.VARINT)
    }
}
