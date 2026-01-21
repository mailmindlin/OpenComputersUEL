package li.cil.oc.server.agent

import com.mojang.authlib.GameProfile
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.event.RobotAttackEntityEvent
import li.cil.oc.api.event.RobotBreakBlockEvent
import li.cil.oc.api.event.RobotExhaustionEvent
import li.cil.oc.api.event.RobotPlaceBlockEvent
import li.cil.oc.api.event.RobotUsedToolEvent
import li.cil.oc.api.internal.Agent
import li.cil.oc.api.network.Connector
import li.cil.oc.common.EventHandler
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.OCObfuscationReflectionHelper
import net.minecraft.block.BlockPistonBase
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.IMerchant
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.network.NetHandlerPlayServer
import net.minecraft.potion.PotionEffect
import net.minecraft.server.management.UserListOpsEntry
import net.minecraft.tileentity.TileEntityCommandBlock
import net.minecraft.tileentity.TileEntitySign
import net.minecraft.util.DamageSource
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.text.ITextComponent
import net.minecraft.util.text.TextComponentString
import net.minecraft.world.IInteractionObject
import net.minecraft.world.World
import net.minecraft.world.WorldServer
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.event.ForgeEventFactory
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.fml.common.eventhandler.Event
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.items.wrapper.CombinedInvWrapper
import net.minecraftforge.items.wrapper.PlayerArmorInvWrapper
import net.minecraftforge.items.wrapper.PlayerInvWrapper
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper
import net.minecraftforge.items.wrapper.PlayerOffhandInvWrapper
import java.util.UUID

object PlayerCompanion {
    fun profileFor(agent: Agent): GameProfile {
        val uuid = agent.ownerUUID()
        val randomId = (agent.world().rand.nextInt(0xFFFFFF) + 1).toString()
        val name = Settings.get.nameFormat
            .replace("\$player\$", agent.ownerName())
            .replace("\$random\$", randomId)
        return GameProfile(uuid, name)
    }

    fun determineUUID(playerUUID: UUID? = null): UUID {
        val format = Settings.get.uuidFormat
        val randomUUID = UUID.randomUUID()
        return try {
            UUID.fromString(
                format
                    .replace("\$random\$", randomUUID.toString())
                    .replace("\$player\$", (playerUUID ?: randomUUID).toString())
            )
        } catch (t: Throwable) {
            OpenComputers.log.warn("Failed determining robot UUID, check your config's `uuidFormat` entry!", t)
            randomUUID
        }
    }

    fun updatePositionAndRotation(player: Player, facing: EnumFacing, side: EnumFacing) {
        player.facing = facing
        player.side = side
        val direction = Vec3d(
            (facing.xOffset + side.xOffset).toDouble(),
            (facing.yOffset + side.yOffset).toDouble(),
            (facing.zOffset + side.zOffset).toDouble()
        ).normalize()
        val yaw = Math.toDegrees(-Math.atan2(direction.x, direction.z)).toFloat()
        val pitch = (Math.toDegrees(-Math.atan2(direction.y, Math.sqrt((direction.x * direction.x) + (direction.z * direction.z)))).toFloat() * 0.99f)
        player.setLocationAndAngles(player.agent.xPosition(), player.agent.yPosition(), player.agent.zPosition(), yaw, pitch)
        player.prevRotationPitch = player.rotationPitch
        player.prevRotationYaw = player.rotationYaw
    }

    fun setInventoryPlayerItems(player: Player) {
        // the offhand is simply the agent's tool item
        val agent = player.agent
        fun setCopyOrNull(inv: net.minecraft.util.NonNullList<ItemStack>, agentInv: IInventory, slot: Int) {
            val item = agentInv.getStackInSlot(slot)
            inv[slot] = item?.copy() ?: ItemStack.EMPTY
        }

        for (i in 0 until 4) {
            setCopyOrNull(player.inventory.armorInventory, agent.equipmentInventory(), i)
        }

        // mainInventory is 36 items
        // the agent inventory is 100 items with some space for components
        // leaving us 88..we'll copy what we can
        val size = minOf(player.inventory.mainInventory.size, agent.mainInventory().sizeInventory)
        for (i in 0 until size) {
            setCopyOrNull(player.inventory.mainInventory, agent.mainInventory(), i)
        }
        player.inventoryContainer.detectAndSendChanges()
    }

    fun detectInventoryPlayerChanges(player: Player) {
        val agent = player.agent
        player.inventoryContainer.detectAndSendChanges()
        // The follow code will set agent.inventories = FakePlayer's inv.stack
        fun setCopy(inv: IInventory, index: Int, item: ItemStack?) {
            val result = item?.copy() ?: ItemStack.EMPTY
            val current = inv.getStackInSlot(index)
            if (!ItemStack.areItemStacksEqual(result, current)) {
                inv.setInventorySlotContents(index, result)
            }
        }
        for (i in 0 until 4) {
            setCopy(agent.equipmentInventory(), i, player.inventory.armorInventory[i])
        }
        val size = minOf(player.inventory.mainInventory.size, agent.mainInventory().sizeInventory)
        for (i in 0 until size) {
            setCopy(agent.mainInventory(), i, player.inventory.mainInventory[i])
        }
    }
}

class Player(val agent: Agent) : FakePlayer(agent.world() as WorldServer, PlayerCompanion.profileFor(agent)) {
    init {
        connection = NetHandlerPlayServer(server, FakeNetworkManager, this)

        capabilities.allowFlying = true
        capabilities.disableDamage = true
        capabilities.isFlying = true
        onGround = true

        setSize(1f, 1f)

        this.inventory = Inventory(this, agent)
        this.inventory.player = this
        // because the inventory was just overwritten, the container is now detached
        this.inventoryContainer = AgentContainer(this)
        this.openContainer = this.inventoryContainer

        try {
            OCObfuscationReflectionHelper.setPrivateValue(EntityPlayer::class.java, this, PlayerMainInvWrapper(inventory), "playerMainHandler")
            OCObfuscationReflectionHelper.setPrivateValue(EntityPlayer::class.java, this, CombinedInvWrapper(PlayerArmorInvWrapper(inventory), PlayerOffhandInvWrapper(inventory)), "playerEquipmentHandler")
            OCObfuscationReflectionHelper.setPrivateValue(EntityPlayer::class.java, this, PlayerInvWrapper(inventory), "playerJoinedHandler")
        } catch (_: Exception) {
        }

        interactionManager.setBlockReachDistance(1)
    }

    override fun getYOffset(): Float = 0.5f

    override fun getEyeHeight(): Float = 0f

    var facing: EnumFacing = EnumFacing.SOUTH
    var side: EnumFacing = EnumFacing.SOUTH

    override fun getPosition(): BlockPos = BlockPos(posX, posY, posZ)

    override fun getDefaultEyeHeight(): Float = 0f

    override fun getDisplayName(): ITextComponent = TextComponentString(agent.name())

    // ----------------------------------------------------------------------- //

    fun <Type : Entity> closestEntity(clazz: Class<Type>, side: EnumFacing = facing): Entity? {
        val bounds = BlockPosition(agent).offset(side).bounds
        return world.findNearestEntityWithinAABB(clazz, bounds, this)
    }

    fun <Type : Entity> entitiesOnSide(clazz: Class<Type>, side: EnumFacing): MutableList<Type> =
        entitiesInBlock(clazz, BlockPosition(agent).offset(side))

    fun <Type : Entity> entitiesInBlock(clazz: Class<Type>, blockPos: BlockPosition): MutableList<Type> =
        world.getEntitiesWithinAABB(clazz, blockPos.bounds)

    private val adjacentItems: MutableList<EntityItem>
        get() = world.getEntitiesWithinAABB(EntityItem::class.java, BlockPosition(agent).bounds.grow(2.0, 2.0, 2.0))

    private fun collectDroppedItems(itemsBefore: Iterable<EntityItem>) {
        val itemsAfter = adjacentItems
        val itemsDropped = itemsAfter - itemsBefore.toSet()
        if (itemsDropped.isNotEmpty()) {
            for (drop in itemsDropped) {
                drop.setNoPickupDelay()
                drop.onCollideWithPlayer(this)
            }
        }
    }

    // ----------------------------------------------------------------------- //

    override fun attackTargetEntityWithCurrentItem(entity: Entity) {
        callUsingItemInSlot(agent.equipmentInventory(), 0) { _ ->
            when (entity) {
                is EntityPlayer -> if (!canAttackPlayer(entity)) return@callUsingItemInSlot Unit // Avoid player damage.
            }
            val event = RobotAttackEntityEvent.Pre(agent, entity)
            MinecraftForge.EVENT_BUS.post(event)
            if (!event.isCanceled) {
                super.attackTargetEntityWithCurrentItem(entity)
                MinecraftForge.EVENT_BUS.post(RobotAttackEntityEvent.Post(agent, entity))
            }
        }
    }

    override fun interactOn(entity: Entity, hand: EnumHand): EnumActionResult {
        val cancel = try {
            MinecraftForge.EVENT_BUS.post(PlayerInteractEvent.EntityInteract(this, hand, entity))
        } catch (t: Throwable) {
            if (!t.stackTrace.any { it.className.startsWith("mods.battlegear2.") }) {
                OpenComputers.log.warn("Some event handler screwed up!", t)
            }
            false
        }
        return if (!cancel && callUsingItemInSlot(agent.equipmentInventory(), 0) { stack ->
            val result = isItemUseAllowed(stack) && (entity.processInitialInteract(this, hand) || (if (entity is EntityLivingBase && !heldItemMainhand.isEmpty) {
                heldItemMainhand.interactWithEntity(this, entity, hand)
            } else false))
            if (!heldItemMainhand.isEmpty) {
                if (heldItemMainhand.count <= 0) {
                    val orig = heldItemMainhand
                    this.inventory.setInventorySlotContents(this.inventory.currentItem, ItemStack.EMPTY)
                    ForgeEventFactory.onPlayerDestroyItem(this, orig, hand)
                } else {
                    // because of various hacks for IC2, we expect the in-hand result to be moved to our offhand buffer
                    this.inventory.offHandInventory[0] = heldItemMainhand
                    this.inventory.setInventorySlotContents(this.inventory.currentItem, ItemStack.EMPTY)
                }
            }
            result
        }) EnumActionResult.SUCCESS else EnumActionResult.PASS
    }

    fun activateBlockOrUseItem(pos: BlockPos, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float, duration: Double): ActivationType {
        return callUsingItemInSlot(agent.equipmentInventory(), 0) { stack ->
            if (shouldCancel { fireRightClickBlock(pos, side) }) {
                return@callUsingItemInSlot ActivationType.None
            }

            val item = if (!stack.isEmpty) stack.item else null
            if (item != null && item.onItemUseFirst(this, world, pos, side, hitX, hitY, hitZ, EnumHand.OFF_HAND) == EnumActionResult.SUCCESS) {
                return@callUsingItemInSlot ActivationType.ItemUsed
            }

            val state = world.getBlockState(pos)
            val block = state.block
            val canActivate = block != Blocks.AIR && Settings.get.allowActivateBlocks
            val shouldActivate = canActivate && (!isSneaking || (item == null || item.doesSneakBypassUse(stack, world, pos, this)))
            when {
                shouldActivate && block.onBlockActivated(world, pos, state, this, EnumHand.OFF_HAND, side, hitX, hitY, hitZ) ->
                    ActivationType.BlockActivated
                duration <= Double.MIN_VALUE && isItemUseAllowed(stack) && tryPlaceBlockWhileHandlingFunnySpecialCases(stack, pos, side, hitX, hitY, hitZ) ->
                    ActivationType.ItemPlaced
                useEquippedItem(duration, stack) ->
                    ActivationType.ItemUsed
                else ->
                    ActivationType.None
            }
        }
    }

    override fun setItemStackToSlot(slotIn: EntityEquipmentSlot, stack: ItemStack) {
        var superCall: () -> Unit = { super.setItemStackToSlot(slotIn, stack) }
        if (slotIn == EntityEquipmentSlot.MAINHAND) {
            agent.equipmentInventory().setInventorySlotContents(0, stack)
            superCall = {
                val slot = inventory.currentItem
                // So, if we're not in the main inventory, currentItem is set to -1
                // for compatibility with mods that try accessing the inv directly
                // using inventory.currentItem. See li.cil.oc.server.agent.Inventory
                if (inventory.currentItem < 0) inventory.currentItem = inventory.currentItem.inv()
                super.setItemStackToSlot(slotIn, stack)
                inventory.currentItem = slot
            }
        } else if (slotIn == EntityEquipmentSlot.OFFHAND) {
            inventory.offHandInventory[0] = stack
        }
        superCall()
    }

    override fun getItemStackFromSlot(slotIn: EntityEquipmentSlot): ItemStack = when (slotIn) {
        EntityEquipmentSlot.MAINHAND -> agent.equipmentInventory().getStackInSlot(0)
        EntityEquipmentSlot.OFFHAND -> inventory.offHandInventory[0]
        else -> super.getItemStackFromSlot(slotIn)
    }

    fun fireRightClickBlock(pos: BlockPos, side: EnumFacing): PlayerInteractEvent.RightClickBlock {
        val hitVec = Vec3d(0.5 + side.directionVec.x * 0.5, 0.5 + side.directionVec.y * 0.5, 0.5 + side.directionVec.z * 0.5)
        val event = PlayerInteractEvent.RightClickBlock(this, EnumHand.OFF_HAND, pos, side, hitVec)
        MinecraftForge.EVENT_BUS.post(event)
        return event
    }

    fun fireLeftClickBlock(pos: BlockPos, side: EnumFacing): PlayerInteractEvent.LeftClickBlock {
        val hitVec = Vec3d(0.5 + side.directionVec.x * 0.5, 0.5 + side.directionVec.y * 0.5, 0.5 + side.directionVec.z * 0.5)
        return net.minecraftforge.common.ForgeHooks.onLeftClickBlock(this, pos, side, hitVec)
    }

    fun fireRightClickAir(): PlayerInteractEvent.RightClickItem {
        val event = PlayerInteractEvent.RightClickItem(this, EnumHand.OFF_HAND)
        MinecraftForge.EVENT_BUS.post(event)
        return event
    }

    private fun trySetActiveHand(duration: Double): Boolean {
        stopActiveHand()
        val entity = this
        val durationHandler = object {
            @SubscribeEvent(priority = EventPriority.LOWEST)
            fun onItemUseStart(startUse: LivingEntityUseItemEvent.Start) {
                if (startUse.entityLiving == entity && !startUse.isCanceled) {
                    startUse.duration = duration.toInt()
                }
            }
        }
        MinecraftForge.EVENT_BUS.register(durationHandler)
        return try {
            setActiveHand(EnumHand.OFF_HAND)
            isHandActive
        } catch (_: Exception) {
            false
        } finally {
            MinecraftForge.EVENT_BUS.unregister(durationHandler)
        }
    }

    fun useItemWithHand(duration: Double, stack: ItemStack): Boolean {
        if (!trySetActiveHand(duration)) {
            if (duration > 0) {
                return false
            }
        }

        val oldStack = stack.copy()
        if (!isItemUseAllowed(stack)) {
            return false
        }

        val maxDuration = stack.maxItemUseDuration
        val heldTicks = maxOf(0, minOf(maxDuration, (duration * 20).toInt()))
        agent.machine().pause(heldTicks / 20.0)

        // setting the active hand will also set its initial duration
        val useItemResult = stack.useItemRightClick(world, this, EnumHand.OFF_HAND)
        stopActiveHand()

        if (useItemResult.type != EnumActionResult.SUCCESS) {
            return false
        }

        val newStack = useItemResult.result
        val stackChanged = !ItemStack.areItemStacksEqual(oldStack, newStack) ||
            !ItemStack.areItemStacksEqual(oldStack, stack)

        if (stackChanged) {
            inventory.offHandInventory[0] = newStack
        }
        return stackChanged
    }

    fun useEquippedItem(duration: Double, stackOption: ItemStack? = null): Boolean {
        if (stackOption == null) {
            return callUsingItemInSlot(agent.equipmentInventory(), 0) { item ->
                if (item != null && !item.isEmpty) useEquippedItem(duration, item) else false
            }
        }

        if (shouldCancel { fireRightClickAir() }) {
            return false
        }

        // Change the offset at which items are used, to avoid hitting
        // the robot itself (e.g. with bows, potions, mining laser, ...).
        posX += facing.xOffset / 2.0
        posZ += facing.zOffset / 2.0

        return try {
            useItemWithHand(duration, stackOption)
        } finally {
            posX -= facing.xOffset / 2.0
            posZ -= facing.zOffset / 2.0
        }
    }

    fun placeBlock(slot: Int, pos: BlockPos, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean {
        return callUsingItemInSlot(agent.mainInventory(), slot, repair = false) { stack ->
            if (shouldCancel { fireRightClickBlock(pos, side) }) {
                return@callUsingItemInSlot false
            }
            tryPlaceBlockWhileHandlingFunnySpecialCases(stack, pos, side, hitX, hitY, hitZ)
        }
    }

    fun clickBlock(pos: BlockPos, side: EnumFacing): Double = callUsingItemInSlot(agent.equipmentInventory(), 0) { _ ->
        val state = world.getBlockState(pos)
        val block = state.block

        if (!block.canHarvestBlock(world, pos, this)) return@callUsingItemInSlot 0.0

        val hardness = block.getBlockHardness(state, world, pos)
        val cobwebOverride = block == Blocks.WEB && Settings.get.screwCobwebs

        val strength = getDigSpeed(state, pos)
        val breakTime = if (cobwebOverride) Settings.get.swingDelay
        else hardness * 1.5 / strength

        if (breakTime.isInfinite()) return@callUsingItemInSlot 0.0
        if (breakTime < 0) return@callUsingItemInSlot breakTime

        val preEvent = RobotBreakBlockEvent.Pre(agent, world, pos, breakTime * Settings.get.harvestRatio)
        MinecraftForge.EVENT_BUS.post(preEvent)
        if (preEvent.isCanceled) return@callUsingItemInSlot 0.0
        val adjustedBreakTime = maxOf(0.05, preEvent.breakTime)

        if (!PlayerInteractionManagerHelper.onBlockClicked(this, pos, side)) {
            if (world.isAirBlock(pos)) {
                return@callUsingItemInSlot 1.0 / 20.0
            }
            return@callUsingItemInSlot 0.0
        }

        EventHandler.scheduleServer { DamageOverTime(this, pos, side, (adjustedBreakTime * 20).toInt()).tick() }

        adjustedBreakTime
    }

    private fun isItemUseAllowed(stack: ItemStack) = stack.isEmpty || run {
        (Settings.get.allowUseItemsWithDuration || stack.maxItemUseDuration <= 0) && !stack.isItemEqual(ItemStack(Items.LEAD))
    }

    override fun dropItem(stack: ItemStack, dropAround: Boolean, traceItem: Boolean): EntityItem? =
        InventoryUtils.spawnStackInWorld(BlockPosition(agent), stack, if (dropAround) null else facing)

    private fun shouldCancel(f: () -> PlayerInteractEvent): Boolean {
        return try {
            val event = f()
            event.isCanceled || when (event) {
                is PlayerInteractEvent.RightClickBlock -> event.useBlock == Event.Result.DENY || event.useItem == Event.Result.DENY
                is PlayerInteractEvent.LeftClickBlock -> event.useBlock == Event.Result.DENY || event.useItem == Event.Result.DENY
                is PlayerInteractEvent.RightClickItem -> event.result == Event.Result.DENY
                else -> false
            }
        } catch (t: Throwable) {
            if (!t.stackTrace.any { it.className.startsWith("mods.battlegear2.") }) {
                OpenComputers.log.warn("Some event handler screwed up!", t)
            }
            false
        }
    }

    private fun <T> callUsingItemInSlot(inventory: IInventory, slot: Int, repair: Boolean = true, f: (ItemStack) -> T): T {
        val itemsBefore = adjacentItems
        val stack = inventory.getStackInSlot(slot)
        val oldStack = stack.copy()
        this.inventory.currentItem = if (inventory == agent.mainInventory()) slot else slot.inv()
        this.inventory.offHandInventory[0] = inventory.getStackInSlot(slot)
        return try {
            f(stack)
        } finally {
            this.inventory.currentItem = 0
            inventory.setInventorySlotContents(slot, this.inventory.offHandInventory[0])
            this.inventory.offHandInventory[0] = ItemStack.EMPTY
            val newStack = inventory.getStackInSlot(slot)
            // this is only possible if f() modified the stack object in-place
            // looking at you, ic2
            if (ItemStack.areItemStacksEqual(oldStack, newStack) &&
                !ItemStack.areItemStacksEqual(oldStack, stack)) {
                inventory.setInventorySlotContents(slot, stack)
            }
            if (!newStack.isEmpty) {
                if (newStack.count <= 0) {
                    inventory.setInventorySlotContents(slot, ItemStack.EMPTY)
                }
                if (repair) {
                    if (newStack.count > 0) tryRepair(newStack, oldStack)
                    else ForgeEventFactory.onPlayerDestroyItem(this, newStack, EnumHand.OFF_HAND)
                }
            }
            collectDroppedItems(itemsBefore)
        }
    }

    private fun tryRepair(stack: ItemStack, oldStack: ItemStack) {
        // Only if the underlying type didn't change.
        if (!stack.isEmpty && !oldStack.isEmpty && stack.item == oldStack.item) {
            val damageRate = RobotUsedToolEvent.ComputeDamageRate(agent, oldStack, stack, Settings.get.itemDamageRate)
            MinecraftForge.EVENT_BUS.post(damageRate)
            if (damageRate.damageRate < 1) {
                MinecraftForge.EVENT_BUS.post(RobotUsedToolEvent.ApplyDamageRate(agent, oldStack, stack, damageRate.damageRate))
            }
        }
    }

    private fun tryPlaceBlockWhileHandlingFunnySpecialCases(stack: ItemStack, pos: BlockPos, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean {
        return !stack.isEmpty && stack.count > 0 && run {
            val event = RobotPlaceBlockEvent.Pre(agent, stack, world, pos)
            MinecraftForge.EVENT_BUS.post(event)
            if (event.isCanceled) false
            else {
                val fakeEyeHeight = if (rotationPitch < 0 && isSomeKindOfPiston(stack)) 1.82 else 0.0
                setPosition(posX, posY - fakeEyeHeight, posZ)
                PlayerCompanion.setInventoryPlayerItems(this)
                val didPlace = stack.onItemUse(this, world, pos, EnumHand.OFF_HAND, side, hitX, hitY, hitZ)
                PlayerCompanion.detectInventoryPlayerChanges(this)
                setPosition(posX, posY + fakeEyeHeight, posZ)
                if (didPlace == EnumActionResult.SUCCESS) {
                    MinecraftForge.EVENT_BUS.post(RobotPlaceBlockEvent.Post(agent, stack, world, pos))
                }
                didPlace == EnumActionResult.SUCCESS
            }
        }
    }

    private fun isSomeKindOfPiston(stack: ItemStack): Boolean {
        val item = stack.item
        return if (item is ItemBlock) {
            val block = item.block
            block != null && block is BlockPistonBase
        } else false
    }

    // ----------------------------------------------------------------------- //

    override fun addExhaustion(amount: Float) {
        if (Settings.get.robotExhaustionCost > 0) {
            val node = agent.machine().node()
            if (node is Connector) {
                node.changeBuffer(-Settings.get.robotExhaustionCost * amount)
            }
        }
        MinecraftForge.EVENT_BUS.post(RobotExhaustionEvent(agent, amount))
    }

    override fun closeScreen() {}

    override fun swingArm(hand: EnumHand) {}

    override fun canUseCommand(level: Int, command: String): Boolean {
        return ("seed" == command && !server.isDedicatedServer) ||
            "tell" == command ||
            "help" == command ||
            "me" == command || run {
            val config = server.playerList
            config.canSendCommands(gameProfile) && run {
                val entry = config.oppedPlayers.getEntry(gameProfile)
                if (entry is UserListOpsEntry) entry.permissionLevel >= level
                else server.opPermissionLevel >= level
            }
        }
    }

    override fun canAttackPlayer(player: EntityPlayer): Boolean = Settings.get.canAttackPlayers

    override fun canEat(value: Boolean): Boolean = false

    override fun isPotionApplicable(effect: PotionEffect): Boolean = false

    override fun attackEntityAsMob(entity: Entity): Boolean = false

    override fun attackEntityFrom(source: DamageSource, damage: Float): Boolean = false

    override fun heal(amount: Float) {}

    override fun setHealth(value: Float) {}

    override fun setDead() {
        isDead = true
    }

    override fun onLivingUpdate() {}

    override fun onItemPickup(entity: Entity, count: Int) {}

    override fun setRevengeTarget(entity: EntityLivingBase?) {}

    override fun setLastAttackedEntity(entity: Entity) {}

    override fun startRiding(entityIn: Entity, force: Boolean): Boolean = false

    override fun trySleep(bedLocation: BlockPos): SleepResult = SleepResult.OTHER_PROBLEM

    override fun sendMessage(message: ITextComponent) {}

    override fun displayGUIChest(inventory: IInventory) {}

    override fun displayGuiCommandBlock(commandBlock: TileEntityCommandBlock) {}

    override fun displayVillagerTradeGui(villager: IMerchant) {
        villager.customer = null
    }

    override fun displayGui(guiOwner: IInteractionObject) {}

    override fun displayGuiEditCommandCart(thing: net.minecraft.command.CommandBlockBaseLogic) {}

    override fun openEditSign(signTile: TileEntitySign) {}

    // ----------------------------------------------------------------------- //

    inner class DamageOverTime(val player: Player, val pos: BlockPos, val side: EnumFacing, val ticksTotal: Int) {
        val world: World = player.world
        var ticks = 0
        var lastDamageSent = 0

        fun tick() {
            // Cancel if the agent stopped or our action is invalidated some other way.
            if (world != player.world || !world.isBlockLoaded(pos) || world.isAirBlock(pos) || !player.agent.machine().isRunning) {
                player.interactionManager.cancelDestroyingBlock()
                return
            }

            val damage = 10 * ticks / maxOf(ticksTotal, 1)
            if (damage < 10) {
                ticks += 1
                if (damage != lastDamageSent) {
                    lastDamageSent = damage
                    if (!PlayerInteractionManagerHelper.updateBlockRemoving(player))
                        return
                }
                EventHandler.scheduleServer { tick() }
            } else {
                callUsingItemInSlot(player.agent.equipmentInventory(), 0) { _ ->
                    this@Player.posX -= side.xOffset / 2.0
                    this@Player.posZ -= side.zOffset / 2.0
                    val expGained = PlayerInteractionManagerHelper.blockRemoving(player, pos)
                    this@Player.posX += side.xOffset / 2.0
                    this@Player.posZ += side.zOffset / 2.0
                    if (expGained >= 0) {
                        MinecraftForge.EVENT_BUS.post(RobotBreakBlockEvent.Post(agent, expGained))
                    }
                }
            }
        }
    }
}

enum class ActivationType {
    None,
    BlockActivated,
    ItemPlaced,
    ItemUsed
}
