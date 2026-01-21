package li.cil.oc.common.tileentity

import li.cil.oc.*
import li.cil.oc.api.Driver
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.driver.item.Container
import li.cil.oc.api.driver.item.Inventory as DriverInventory
import li.cil.oc.api.event.RobotAnalyzeEvent
import li.cil.oc.api.event.RobotMoveEvent
import li.cil.oc.api.internal.Keyboard as InternalKeyboard
import li.cil.oc.api.internal.MultiTank
import li.cil.oc.api.internal.Robot as InternalRobot
import li.cil.oc.api.internal.TextBuffer as InternalTextBuffer
import li.cil.oc.api.network.*
import li.cil.oc.client.gui.Robot as RobotGui
import li.cil.oc.common.EventHandler
import li.cil.oc.common.Slot
import li.cil.oc.common.Sound
import li.cil.oc.common.Tier
import li.cil.oc.common.block.RobotAfterimage
import li.cil.oc.common.block.RobotProxy as RobotProxyBlock
import li.cil.oc.common.inventory.InventoryProxy
import li.cil.oc.common.inventory.InventorySelection
import li.cil.oc.common.inventory.TankSelection
import li.cil.oc.common.item.data.RobotData
import li.cil.oc.common.tileentity.traits.Computer
import li.cil.oc.integration.opencomputers.DriverKeyboard
import li.cil.oc.integration.opencomputers.DriverRedstoneCard
import li.cil.oc.integration.opencomputers.DriverScreen
import li.cil.oc.server.agent
import li.cil.oc.server.agent.Player
import li.cil.oc.server.component.GraphicsCard
import li.cil.oc.server.component.Robot as RobotComponent
import li.cil.oc.server.PacketSender as ServerPacketSender
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedNBT.extendedNBT
import li.cil.oc.util.ExtendedWorld.extendedWorld
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.StackOption
import li.cil.oc.util.SomeStack
import li.cil.oc.util.EmptyStack
import net.minecraft.block.Block
import net.minecraft.block.BlockLiquid
import net.minecraft.client.Minecraft
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Blocks
import net.minecraft.init.SoundEvents
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.util.SoundCategory
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.fluids.*
import net.minecraftforge.fluids.capability.CapabilityFluidHandler
import net.minecraftforge.fluids.capability.FluidTankProperties
import net.minecraftforge.fluids.capability.IFluidHandler
import net.minecraftforge.fluids.capability.IFluidTankProperties
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.util.UUID

// Implementation note: this tile entity is never directly added to the world.
// It is always wrapped by a `RobotProxy` tile entity, which forwards any
// necessary calls to this class. This is done to make moves efficient: when a
// robot moves we only create a new proxy tile entity, hook the instance of this
// class that was held by the old proxy to it and can then safely forget the
// old proxy, which will be cleaned up by Minecraft like any other tile entity.
class Robot : Computer(), traits.PowerInformation, traits.RotatableTile, IFluidHandler, InternalRobot, InventorySelection, TankSelection {
    @JvmField
    var proxy: RobotProxy? = null

    @JvmField
    val info = RobotData()

    @JvmField
    val bot: RobotComponent? = if (isServer) RobotComponent(this) else null

    init {
        if (isServer) {
            machine.setCostPerTick(Settings.get.robotCost)
        }
    }

    // ----------------------------------------------------------------------- //

    override fun <T> getCapability(capability: Capability<T>, facing: EnumFacing?): T? {
        return if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            capability.cast(this as T)
        } else {
            super.getCapability(capability, facing)
        }
    }

    override fun tier(): Int = info.tier

    val isCreative: Boolean get() = tier() == Tier.Four

    @JvmField
    val equipmentInventory = object : InventoryProxy {
        override val inventory: Robot get() = this@Robot
        override fun getSizeInventory(): Int = 4
    }

    // Wrapper for the part of the inventory that is mutable.
    @JvmField
    val mainInventory = object : InventoryProxy {
        override val inventory: Robot get() = this@Robot
        override fun getSizeInventory(): Int = this@Robot.inventorySize
        override val offset: Int get() = equipmentInventory.getSizeInventory()
    }

    val actualInventorySize = 100

    val maxInventorySize: Int get() = actualInventorySize - equipmentInventory.getSizeInventory() - componentCount()

    @JvmField
    var inventorySize: Int = -1

    override var selectedSlot = 0

    override fun setSelectedSlot(index: Int) {
        selectedSlot = Math.max(0, Math.min(index, mainInventory.getSizeInventory() - 1))
        if (world != null) {
            ServerPacketSender.sendRobotSelectedSlotChange(this)
        }
    }

    @JvmField
    val tank = object : MultiTank {
        override fun tankCount(): Int = this@Robot.tankCount()
        override fun getFluidTank(index: Int): ManagedEnvironment? = this@Robot.getFluidTank(index)
    }

    override var selectedTank = 0

    // For client.
    @JvmField
    var renderingErrored = false

    override fun componentCount(): Int = info.components.size

    override fun getComponentInSlot(index: Int): ManagedEnvironment? = if (components.size > index) components[index] else null

    override fun player(): Player {
        agent.Player.updatePositionAndRotation(player_, facing, facing)
        agent.Player.setInventoryPlayerItems(player_)
        return player_
    }

    override fun synchronizeSlot(slot: Int) {
        if (slot >= 0 && slot < getSizeInventory()) synchronized(this) {
            val stack = getStackInSlot(slot)
            components[slot]?.let { component ->
                // We're guaranteed to have a driver for entries.
                save(component, Driver.driverFor(stack, javaClass), stack)
            }
            ServerPacketSender.sendRobotInventory(this, slot, stack)
        }
    }

    val containerSlots: IntRange get() = 1..info.containers.size

    val componentSlots: IntRange get() = (sizeInventory - componentCount()) until sizeInventory

    val inventorySlots: IntRange get() = equipmentInventory.sizeInventory until (equipmentInventory.sizeInventory + mainInventory.sizeInventory)

    fun setLightColor(value: Int) {
        info.lightColor = value
        ServerPacketSender.sendRobotLightChange(this)
    }

    override fun shouldAnimate(): Boolean = isRunning

    // ----------------------------------------------------------------------- //

    override fun getNode(): Node? = if (isServer) machine.node() else null

    @JvmField
    var globalBuffer = 0.0
    @JvmField
    var globalBufferSize = 0.0

    val maxComponents = 32

    @JvmField
    var ownerName: String = Settings.get.fakePlayerName

    @JvmField
    var ownerUUID: UUID = Settings.get.fakePlayerProfile.id

    @JvmField
    var animationTicksLeft = 0

    @JvmField
    var animationTicksTotal = 0

    @JvmField
    var moveFrom: BlockPos? = null

    @JvmField
    var swingingTool = false

    @JvmField
    var turnAxis = 0

    @JvmField
    var appliedToolEnchantments = false

    private val player_ by lazy { agent.Player(this) }

    // ----------------------------------------------------------------------- //

    override fun name(): String = info.name

    override fun setName(name: String) {
        info.name = name
    }

    override fun onAnalyze(player: EntityPlayer, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Array<Node>? {
        player.sendMessage(Localization.Analyzer.RobotOwner(ownerName))
        player.sendMessage(Localization.Analyzer.RobotName(player_.name))
        MinecraftForge.EVENT_BUS.post(RobotAnalyzeEvent(this, player))
        return super.onAnalyze(player, side, hitX, hitY, hitZ)
    }

    fun move(direction: EnumFacing): Boolean {
        val oldPosition = pos
        val newPosition = oldPosition.offset(direction)
        if (!world.isBlockLoaded(newPosition)) {
            return false // Don't fall off the earth.
        }

        if (isServer) {
            val event = RobotMoveEvent.Pre(this, direction)
            MinecraftForge.EVENT_BUS.post(event)
            if (event.isCanceled) return false
        }

        val blockRobotProxy = ApiItems.get(Constants.BlockName.Robot).block() as RobotProxyBlock
        val blockRobotAfterImage = ApiItems.get(Constants.BlockName.RobotAfterimage).block() as RobotAfterimage
        val wasAir = world.isAirBlock(newPosition)
        val state = world.getBlockState(newPosition)
        val block = state.block
        val metadata = block.getMetaFromState(state)
        try {
            // Setting this will make the tile entity created via the following call
            // to setBlock to re-use our "real" instance as the inner object, instead
            // of creating a new one.
            blockRobotProxy.moving.set(this)
            // Do *not* immediately send the change to clients to allow checking if it
            // worked before the client is notified so that we can use the same trick on
            // the client by sending a corresponding packet. This also saves us from
            // having to send the complete state again (e.g. screen buffer) each move.
            world.setBlockToAir(newPosition)
            // In some cases (though I couldn't quite figure out which one) setBlock
            // will return true, even though the block was not created / adjusted.
            val created = world.setBlockState(newPosition, world.getBlockState(oldPosition), 1) &&
                world.getTileEntity(newPosition) == proxy
            if (created) {
                assert(pos == newPosition)
                world.setBlockState(oldPosition, Blocks.AIR.defaultState, 1)
                world.setBlockState(oldPosition, blockRobotAfterImage.defaultState, 1)
                assert(world.getBlockState(oldPosition).block == blockRobotAfterImage)
                // Here instead of Lua callback so that it gets called on client, too.
                val moveTicks = Math.max((Settings.get.moveDelay * 20).toInt(), 1)
                setAnimateMove(oldPosition, moveTicks)
                if (isServer) {
                    ServerPacketSender.sendRobotMove(this, oldPosition, direction)
                    checkRedstoneInputChanged()
                    MinecraftForge.EVENT_BUS.post(RobotMoveEvent.Post(this, direction))
                } else {
                    // If we broke some replaceable block (like grass) play its break sound.
                    if (!wasAir) {
                        if (block != Blocks.AIR && block != blockRobotAfterImage) {
                            if (FluidRegistry.lookupFluidForBlock(block) == null &&
                                block !is BlockFluidBase &&
                                block !is BlockLiquid) {
                                world.playEvent(2001, newPosition, Block.getIdFromBlock(block) + (metadata shl 12))
                            } else {
                                val sx = newPosition.x + 0.5
                                val sy = newPosition.y + 0.5
                                val sz = newPosition.z + 0.5
                                world.playSound(sx, sy, sz, SoundEvents.BLOCK_WATER_AMBIENT, SoundCategory.BLOCKS,
                                    world.rand.nextFloat() * 0.25f + 0.75f, world.rand.nextFloat() * 1.0f + 0.5f, false)
                            }
                        }
                    }
                    world.notifyBlockUpdate(oldPosition)
                    world.notifyBlockUpdate(newPosition)
                }
                assert(!isInvalid)
            } else {
                world.setBlockToAir(newPosition)
            }
            return created && this.pos == newPosition
        } finally {
            blockRobotProxy.moving.set(null)
        }
    }

    // ----------------------------------------------------------------------- //

    val isAnimatingMove: Boolean get() = animationTicksLeft > 0 && moveFrom != null

    val isAnimatingSwing: Boolean get() = animationTicksLeft > 0 && swingingTool

    val isAnimatingTurn: Boolean get() = animationTicksLeft > 0 && turnAxis != 0

    fun animateSwing(duration: Double) {
        if (!items[0].isEmpty) {
            setAnimateSwing((duration * 20).toInt())
            ServerPacketSender.sendRobotAnimateSwing(this)
        }
    }

    fun animateTurn(clockwise: Boolean, duration: Double) {
        setAnimateTurn(if (clockwise) 1 else -1, (duration * 20).toInt())
        ServerPacketSender.sendRobotAnimateTurn(this)
    }

    fun setAnimateMove(fromPosition: BlockPos, ticks: Int) {
        animationTicksTotal = ticks + 2
        prepareForAnimation()
        moveFrom = fromPosition
    }

    fun setAnimateSwing(ticks: Int) {
        animationTicksTotal = Math.max(ticks, 5)
        prepareForAnimation()
        swingingTool = true
    }

    fun setAnimateTurn(axis: Int, ticks: Int) {
        animationTicksTotal = ticks
        prepareForAnimation()
        turnAxis = axis
    }

    private fun prepareForAnimation() {
        animationTicksLeft = animationTicksTotal
        moveFrom = null
        swingingTool = false
        turnAxis = 0
    }

    // ----------------------------------------------------------------------- //

    override fun shouldRenderInPass(pass: Int): Boolean = true

    override fun getRenderBoundingBox(): AxisAlignedBB =
        if (blockType != null && world != null)
            blockType.getCollisionBoundingBox(world.getBlockState(pos), world, pos)!!.grow(0.5, 0.5, 0.5).offset(pos)
        else
            AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0).offset(pos)

    // ----------------------------------------------------------------------- //

    override fun updateEntity() {
        if (animationTicksLeft > 0) {
            animationTicksLeft -= 1
            if (animationTicksLeft == 0) {
                moveFrom = null
                swingingTool = false
                turnAxis = 0
            }
        }
        super.updateEntity()
        if (isServer) {
            if (world.totalWorldTime % Settings.get.tickFrequency == 0L) {
                if (info.tier == 3) {
                    bot!!.node().changeBuffer(Double.POSITIVE_INFINITY)
                }
                globalBuffer = bot!!.node().globalBuffer()
                globalBufferSize = bot.node().globalBufferSize()
                info.totalEnergy = globalBuffer.toInt()
                info.robotEnergy = bot.node().localBuffer().toInt()
                updatePowerInformation()
            }
            if (!appliedToolEnchantments) {
                appliedToolEnchantments = true
                val stack = getStackInSlot(0)
                if (!stack.isEmpty) {
                    player_.attributeMap.applyAttributeModifiers(stack.getAttributeModifiers(EntityEquipmentSlot.MAINHAND))
                }
            }
        } else if (isRunning && isAnimatingMove) {
            client.Sound.updatePosition(this)
        }

        for (slot in 0 until equipmentInventory.sizeInventory + mainInventory.sizeInventory) {
            val stack = getStackInSlot(slot)
            if (!stack.isEmpty) {
                try {
                    stack.updateAnimation(world, if (!world.isRemote) player_ else null, slot, slot == 0)
                } catch (ignored: NullPointerException) {
                    // Client side item updates that need a player instance...
                }
            }
        }
    }

    // The robot's machine is updated in a tick handler, to avoid delayed tile
    // entity creation when moving, which would screw over all the things...
    override fun updateComputer() {}

    override fun onRunningChanged() {
        super.onRunningChanged()
        if (isRunning) EventHandler.onRobotStart(this)
        else EventHandler.onRobotStopped(this)
    }

    override fun initialize() {
        if (isServer) {
            // Ensure we have a node address, because the proxy needs this to initialize
            // its own node to the same address ours has.
            ApiNetwork.joinNewNetwork(node)
        }
    }

    override fun dispose() {
        super.dispose()
        if (isClient) {
            val screen = Minecraft.getMinecraft().currentScreen
            if (screen is RobotGui && screen.robot == this) {
                Minecraft.getMinecraft().displayGuiScreen(null)
            }
        } else EventHandler.onRobotStopped(this)
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private val RobotTag = Settings.namespace + "robot"
        private val OwnerTag = Settings.namespace + "owner"
        private val OwnerUUIDTag = Settings.namespace + "ownerUuid"
        private val SelectedSlotTag = Settings.namespace + "selectedSlot"
        private val SelectedTankTag = Settings.namespace + "selectedTank"
        private val AnimationTicksTotalTag = Settings.namespace + "animationTicksTotal"
        private val AnimationTicksLeftTag = Settings.namespace + "animationTicksLeft"
        private val MoveFromXTag = Settings.namespace + "moveFromX"
        private val MoveFromYTag = Settings.namespace + "moveFromY"
        private val MoveFromZTag = Settings.namespace + "moveFromZ"
        private val SwingingToolTag = Settings.namespace + "swingingTool"
        private val TurnAxisTag = Settings.namespace + "turnAxis"
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        updateInventorySize()
        machine.onHostChanged()

        bot!!.load(nbt.getCompoundTag(RobotTag))
        if (nbt.hasKey(OwnerTag)) {
            ownerName = nbt.getString(OwnerTag)
        }
        if (nbt.hasKey(OwnerUUIDTag)) {
            ownerUUID = UUID.fromString(nbt.getString(OwnerUUIDTag))
        }
        if (inventorySize > 0) {
            selectedSlot = Math.max(0, Math.min(nbt.getInteger(SelectedSlotTag), mainInventory.sizeInventory - 1))
        }
        selectedTank = nbt.getInteger(SelectedTankTag)
        animationTicksTotal = nbt.getInteger(AnimationTicksTotalTag)
        animationTicksLeft = nbt.getInteger(AnimationTicksLeftTag)
        if (animationTicksLeft > 0) {
            if (nbt.hasKey(MoveFromXTag)) {
                val moveFromX = nbt.getInteger(MoveFromXTag)
                val moveFromY = nbt.getInteger(MoveFromYTag)
                val moveFromZ = nbt.getInteger(MoveFromZTag)
                moveFrom = BlockPos(moveFromX, moveFromY, moveFromZ)
            }
            swingingTool = nbt.getBoolean(SwingingToolTag)
            turnAxis = nbt.getByte(TurnAxisTag).toInt()
        }

        // Normally set in superclass, but that's not called directly, only in the
        // robot's proxy instance.
        _isOutputEnabled = hasRedstoneCard
        if (isRunning) EventHandler.onRobotStart(this)
    }

    // Side check for Waila (and other mods that may call this client side).
    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        if (isServer) synchronized(this) {
            info.save(nbt)

            // Note: computer is saved when proxy is saved (in proxy's super writeToNBT)
            // which is a bit ugly, and may be refactored some day, but it works.
            nbt.setNewCompoundTag(RobotTag) { bot!!.save(it) }
            nbt.setString(OwnerTag, ownerName)
            nbt.setString(OwnerUUIDTag, ownerUUID.toString())
            nbt.setInteger(SelectedSlotTag, selectedSlot)
            nbt.setInteger(SelectedTankTag, selectedTank)
            if (isAnimatingMove || isAnimatingSwing || isAnimatingTurn) {
                nbt.setInteger(AnimationTicksTotalTag, animationTicksTotal)
                nbt.setInteger(AnimationTicksLeftTag, animationTicksLeft)
                moveFrom?.let { blockPos ->
                    nbt.setInteger(MoveFromXTag, blockPos.x)
                    nbt.setInteger(MoveFromYTag, blockPos.y)
                    nbt.setInteger(MoveFromZTag, blockPos.z)
                }
                nbt.setBoolean(SwingingToolTag, swingingTool)
                nbt.setByte(TurnAxisTag, turnAxis.toByte())
            }
        }
    }

    @SideOnly(Side.CLIENT)
    override fun readFromNBTForClient(nbt: NBTTagCompound) {
        super.readFromNBTForClient(nbt)
        load(nbt)
        info.load(nbt)

        updateInventorySize()

        selectedSlot = nbt.getInteger(SelectedSlotTag)
        animationTicksTotal = nbt.getInteger(AnimationTicksTotalTag)
        animationTicksLeft = nbt.getInteger(AnimationTicksLeftTag)
        if (animationTicksLeft > 0) {
            if (nbt.hasKey(MoveFromXTag)) {
                val moveFromX = nbt.getInteger(MoveFromXTag)
                val moveFromY = nbt.getInteger(MoveFromYTag)
                val moveFromZ = nbt.getInteger(MoveFromZTag)
                moveFrom = BlockPos(moveFromX, moveFromY, moveFromZ)
            }
            swingingTool = nbt.getBoolean(SwingingToolTag)
            turnAxis = nbt.getByte(TurnAxisTag).toInt()
        }
        connectComponents()
    }

    override fun writeToNBTForClient(nbt: NBTTagCompound) {
        synchronized(this) {
            super.writeToNBTForClient(nbt)
            save(nbt)
            info.save(nbt)

            nbt.setInteger(SelectedSlotTag, selectedSlot)
            if (isAnimatingMove || isAnimatingSwing || isAnimatingTurn) {
                nbt.setInteger(AnimationTicksTotalTag, animationTicksTotal)
                nbt.setInteger(AnimationTicksLeftTag, animationTicksLeft)
                moveFrom?.let { blockPos ->
                    nbt.setInteger(MoveFromXTag, blockPos.x)
                    nbt.setInteger(MoveFromYTag, blockPos.y)
                    nbt.setInteger(MoveFromZTag, blockPos.z)
                }
                nbt.setBoolean(SwingingToolTag, swingingTool)
                nbt.setByte(TurnAxisTag, turnAxis.toByte())
            }
        }
    }

    // ----------------------------------------------------------------------- //

    override fun onMachineConnect(node: Node) {
        super.onConnect(node)
        if (node == this.node) {
            node.connect(bot!!.node())
            (node as Connector).setLocalBufferSize(0.0)
        }
    }

    override fun onMachineDisconnect(node: Node) {
        super.onDisconnect(node)
        if (node == this.node) {
            node.remove()
            bot!!.node().remove()
            for (slot in componentSlots) {
                getComponentInSlot(slot)?.node()?.remove()
            }
        }
    }

    // ----------------------------------------------------------------------- //

    override fun onItemAdded(slot: Int, stack: ItemStack) {
        if (isServer) {
            if (isToolSlot(slot)) {
                player_.attributeMap.applyAttributeModifiers(stack.getAttributeModifiers(EntityEquipmentSlot.MAINHAND))
                ServerPacketSender.sendRobotInventory(this, slot, stack)
            }
            if (isUpgradeSlot(slot)) {
                ServerPacketSender.sendRobotInventory(this, slot, stack)
            }
            if (isFloppySlot(slot)) {
                Sound.playDiskInsert(this)
            }
            if (isComponentSlot(slot, stack)) {
                super.onItemAdded(slot, stack)
                world.notifyBlocksOfNeighborChange(position, blockType, false)
            }
            if (isInventorySlot(slot)) {
                machine.signal("inventory_changed", slot - equipmentInventory.sizeInventory + 1)
            }
        } else super.onItemAdded(slot, stack)
    }

    override fun onItemRemoved(slot: Int, stack: ItemStack) {
        super.onItemRemoved(slot, stack)
        if (isServer) {
            if (isToolSlot(slot)) {
                player_.attributeMap.removeAttributeModifiers(stack.getAttributeModifiers(EntityEquipmentSlot.MAINHAND))
                ServerPacketSender.sendRobotInventory(this, slot, ItemStack.EMPTY)
            }
            if (isUpgradeSlot(slot)) {
                ServerPacketSender.sendRobotInventory(this, slot, ItemStack.EMPTY)
            }
            if (isFloppySlot(slot)) {
                Sound.playDiskEject(this)
            }
            if (isInventorySlot(slot)) {
                machine.signal("inventory_changed", slot - equipmentInventory.sizeInventory + 1)
            }
            if (isComponentSlot(slot, stack)) {
                world.notifyBlocksOfNeighborChange(position, blockType, false)
            }
        }
    }

    override fun markDirty() {
        super.markDirty()
        // Avoid getting into a bad state on the client when updating before we
        // got the descriptor packet from the server. If we manage to open the
        // GUI before the descriptor packet arrived, close it again because it is
        // invalid anyway.
        if (inventorySize >= 0) {
            updateInventorySize()
        } else if (isClient) {
            val screen = Minecraft.getMinecraft().currentScreen
            if (screen is RobotGui && screen.robot == this) {
                Minecraft.getMinecraft().displayGuiScreen(null)
            }
        }
        renderingErrored = false
    }

    override fun connectItemNode(node: Node) {
        super.connectItemNode(node)
        if (node != null) {
            val host = node.host()
            when (host) {
                is InternalTextBuffer -> {
                    for (slot in componentSlots) {
                        when (val component = getComponentInSlot(slot)) {
                            is InternalKeyboard -> host.node().connect(component.node())
                            is GraphicsCard -> host.node().connect(component.node())
                        }
                    }
                }
                is InternalKeyboard -> {
                    for (slot in componentSlots) {
                        when (val component = getComponentInSlot(slot)) {
                            is InternalTextBuffer -> host.node().connect(component.node())
                        }
                    }
                }
            }
        }
    }

    override fun isComponentSlot(slot: Int, stack: ItemStack): Boolean = (containerSlots union componentSlots).contains(slot)

    fun containerSlotType(slot: Int): String = if (containerSlots.contains(slot)) {
        val stack = info.containers[slot - 1]
        val driver = Driver.driverFor(stack, javaClass)
        if (driver is Container) driver.providedSlot(stack) else Slot.None
    } else Slot.None

    fun containerSlotTier(slot: Int): Int = if (containerSlots.contains(slot)) {
        val stack = info.containers[slot - 1]
        val driver = Driver.driverFor(stack, javaClass)
        if (driver is Container) driver.providedTier(stack) else Tier.None
    } else Tier.None

    fun isToolSlot(slot: Int): Boolean = slot == 0

    fun isContainerSlot(slot: Int): Boolean = containerSlots.contains(slot)

    fun isInventorySlot(slot: Int): Boolean = inventorySlots.contains(slot)

    fun isFloppySlot(slot: Int): Boolean = !getStackInSlot(slot).isEmpty && isComponentSlot(slot, getStackInSlot(slot)) && run {
        val stack = getStackInSlot(slot)
        val driver = Driver.driverFor(stack, javaClass)
        driver?.slot(stack) == Slot.Floppy
    }

    fun isUpgradeSlot(slot: Int): Boolean = containerSlotType(slot) == Slot.Upgrade

    // ----------------------------------------------------------------------- //

    override fun componentSlot(address: String): Int = components.indexOfFirst { it?.node != null && it.node.address() == address }

    override fun hasRedstoneCard(): Boolean = (containerSlots union componentSlots).any { slot ->
        val stack = getStackInSlot(slot)
        !stack.isEmpty && DriverRedstoneCard.worksWith(stack, javaClass)
    }

    private fun computeInventorySize(): Int = Math.min(maxInventorySize, (containerSlots union componentSlots).fold(0) { acc, slot ->
        val stack = getStackInSlot(slot)
        acc + if (!stack.isEmpty) {
            val driver = Driver.driverFor(stack, javaClass)
            if (driver is DriverInventory) driver.inventoryCapacity(stack) else 0
        } else 0
    })

    private var updatingInventorySize = false

    fun updateInventorySize() {
        synchronized(this) {
            if (!updatingInventorySize) try {
                updatingInventorySize = true
                val newInventorySize = computeInventorySize()
                if (newInventorySize != inventorySize) {
                    inventorySize = newInventorySize
                    val realSize = equipmentInventory.sizeInventory + mainInventory.sizeInventory
                    val oldSelected = selectedSlot
                    val removed = mutableListOf<ItemStack>()
                    for (slot in realSize until sizeInventory - componentCount()) {
                        val stack = getStackInSlot(slot)
                        setInventorySlotContents(slot, ItemStack.EMPTY)
                        if (!stack.isEmpty) removed.add(stack)
                    }
                    val copyComponentCount = Math.min(sizeInventory, componentCount())
                    System.arraycopy(components, sizeInventory - copyComponentCount, components, realSize, copyComponentCount)
                    for (slot in Math.max(0, sizeInventory - componentCount()) until sizeInventory) {
                        if (slot < realSize || slot >= realSize + componentCount()) {
                            components[slot] = null
                        }
                    }
                    sizeInventory = realSize + componentCount()
                    if (world != null && isServer) {
                        for (stack in removed) {
                            player().inventory.addItemStackToInventory(stack)
                            spawnStackInWorld(stack, facing)
                        }
                        setSelectedSlot(oldSelected)
                    } // else: save is screwed and we potentially lose items. Life is hard.
                }
            } finally {
                updatingInventorySize = false
            }
        }
    }

    // ----------------------------------------------------------------------- //

    @JvmField
    var sizeInventory: Int = actualInventorySize

    override fun getInventoryStackLimit(): Int = 64

    override fun getStackInSlot(slot: Int): ItemStack {
        return when {
            slot >= sizeInventory -> ItemStack.EMPTY
            slot >= sizeInventory - componentCount() -> info.components[slot - (sizeInventory - componentCount())]
            else -> super.getStackInSlot(slot)
        }
    }

    override fun setInventorySlotContents(slot: Int, stack: ItemStack) {
        if (slot < sizeInventory - componentCount() && (isItemValidForSlot(slot, stack) || stack.isEmpty)) {
            if (!stack.isEmpty && stack.count > 1 && isComponentSlot(slot, stack)) {
                super.setInventorySlotContents(slot, stack.splitStack(1))
                if (stack.count > 0 && isServer) {
                    player().inventory.addItemStackToInventory(stack)
                    spawnStackInWorld(stack, facing)
                }
            } else super.setInventorySlotContents(slot, stack)
        } else if (!stack.isEmpty && stack.count > 0 && !world.isRemote) {
            spawnStackInWorld(stack, EnumFacing.UP)
        }
    }

    override fun isUsableByPlayer(player: EntityPlayer): Boolean =
        super.isUsableByPlayer(player) && (!isCreative || player.capabilities.isCreativeMode)

    override fun isItemValidForSlot(slot: Int, stack: ItemStack): Boolean {
        val driver = Driver.driverFor(stack, javaClass)
        return when {
            slot == 0 -> true // Allow anything in the tool slot.
            isContainerSlot(slot) && driver != null -> {
                // Yay special cases! Dynamic screens kind of work, but are pretty derpy
                // because the item gets send around on changes, including the screen
                // state, which leads to weird effects. Also, it's really illogical that
                // a screen (and keyboard) could be attached to the robot on the fly.
                // Since these are very special (as they have special behavior in the
                // GUI) I feel it's OK to handle it like this, instead of some extra API
                // logic making the differentiation of assembler and containers generic.
                driver != DriverScreen &&
                    driver != DriverKeyboard &&
                    driver.slot(stack) == containerSlotType(slot) &&
                    driver.tier(stack) <= containerSlotTier(slot)
            }
            isInventorySlot(slot) -> true // Normal inventory.
            else -> false // Invalid slot.
        }
    }

    // ----------------------------------------------------------------------- //

    override fun dropSlot(slot: Int, count: Int, direction: EnumFacing?): Boolean =
        InventoryUtils.dropSlot(BlockPosition(x, y, z, world), mainInventory, slot, count, direction)

    override fun dropAllSlots() {
        InventoryUtils.dropSlot(BlockPosition(x, y, z, world), this, 0, Int.MAX_VALUE)
        for (slot in containerSlots) {
            InventoryUtils.dropSlot(BlockPosition(x, y, z, world), this, slot, Int.MAX_VALUE)
        }
        InventoryUtils.dropAllSlots(BlockPosition(x, y, z, world), mainInventory)
    }

    // ----------------------------------------------------------------------- //

    override fun canExtractItem(slot: Int, stack: ItemStack, side: EnumFacing): Boolean =
        getSlotsForFace(side).contains(slot)

    override fun canInsertItem(slot: Int, stack: ItemStack, side: EnumFacing): Boolean =
        getSlotsForFace(side).contains(slot) && isItemValidForSlot(slot, stack)

    override fun getSlotsForFace(side: EnumFacing): IntArray = when (toLocal(side)) {
        EnumFacing.WEST -> intArrayOf(0) // Tool
        EnumFacing.EAST -> containerSlots.toList().toIntArray()
        else -> inventorySlots.toList().toIntArray()
    }

    // ----------------------------------------------------------------------- //

    fun tryGetTank(tank: Int): IFluidTank? {
        val tanks = components.filterNotNull().filterIsInstance<IFluidTank>()
        return if (tank < 0 || tank >= tanks.size) null else tanks[tank]
    }

    fun tankCount(): Int = components.count { it is IFluidTank }

    fun getFluidTank(tank: Int): ManagedEnvironment? = tryGetTank(tank) as? ManagedEnvironment

    // ----------------------------------------------------------------------- //

    override fun fill(resource: FluidStack?, doFill: Boolean): Int =
        tryGetTank(selectedTank)?.fill(resource, doFill) ?: 0

    override fun drain(resource: FluidStack?, doDrain: Boolean): FluidStack? =
        tryGetTank(selectedTank)?.let { t ->
            if (t.fluid != null && t.fluid.isFluidEqual(resource)) t.drain(resource!!.amount, doDrain)
            else null
        }

    override fun drain(maxDrain: Int, doDrain: Boolean): FluidStack? =
        tryGetTank(selectedTank)?.drain(maxDrain, doDrain)

    fun canFill(fluid: Fluid): Boolean =
        tryGetTank(selectedTank)?.let { t -> t.fluid == null || t.fluid.fluid == fluid } ?: false

    fun canDrain(fluid: Fluid): Boolean =
        tryGetTank(selectedTank)?.let { t -> t.fluid != null && t.fluid.fluid == fluid } ?: false

    override fun getTankProperties(): Array<IFluidTankProperties> =
        FluidTankProperties.convert(components.filterNotNull().filterIsInstance<IFluidTank>().map { it.info }.toTypedArray())
}
