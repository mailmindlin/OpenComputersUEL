package li.cil.oc.common.item

import com.google.common.cache.CacheBuilder
import com.google.common.cache.RemovalListener
import com.google.common.cache.RemovalNotification
import com.google.common.collect.ImmutableMap
import li.cil.oc.Constants
import li.cil.oc.Localization
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.api.Items
import li.cil.oc.api.Network
import li.cil.oc.api.Machine as MachineFactory
import li.cil.oc.api.machine.Machine
import li.cil.oc.api.driver.item.Container
import li.cil.oc.api.internal.Keyboard
import li.cil.oc.api.internal.TextBuffer
import li.cil.oc.api.machine.MachineHost
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Node
import li.cil.oc.client.KeyBindings
import li.cil.oc.common.GuiType
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.inventory.ComponentInventory
import li.cil.oc.common.item.data.TabletData
import li.cil.oc.common.item.traits.Chargeable
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.server.component.Tablet as ComponentTablet
import li.cil.oc.integration.opencomputers.DriverScreen
import li.cil.oc.util.*
import li.cil.oc.server.PacketSender as ServerPacketSender
import li.cil.oc.client.PacketSender as ClientPacketSender
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.block.model.ModelBakery
import net.minecraft.client.renderer.block.model.ModelResourceLocation
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.EnumRarity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.server.integrated.IntegratedServer
import net.minecraft.util.ActionResult
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.ResourceLocation
import net.minecraft.world.World
import net.minecraftforge.common.util.Constants as NBTConstants
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

class Tablet(override val parent: Delegator) : Delegate, CustomModel, Chargeable {
    // Must be assembled to be usable so we hide it in the item list.
    override var showInItemList: Boolean = false
    override val itemId: Int = 0

    override val maxStackSize: Int = 1

    // ----------------------------------------------------------------------- //

    override fun tooltipExtended(stack: ItemStack, tooltip: MutableList<String>) {
        if (KeyBindings.showExtendedTooltips) {
            val info = TabletData(stack)
            // Ignore/hide the screen.
            val components = info.items.drop(1)
            if (components.size > 1) {
                tooltip.addAll(Tooltip.get("server.Components"))
                for (component in components) {
                    if (!component.isEmpty) {
                        tooltip.add("- " + component.displayName)
                    }
                }
            }
        }
    }

    override fun rarity(stack: ItemStack): EnumRarity {
        val data = TabletData(stack)
        return Rarity.byTier(data.tier)
    }

    override fun showDurabilityBar(stack: ItemStack): Boolean = true

    override fun durability(stack: ItemStack): Double {
        return if (stack.hasTagCompound()) {
            val data = Client.getWeak(stack)?.data ?: TabletData(stack)
            1 - data.energy / data.maxEnergy
        } else 1.0
    }

    // ----------------------------------------------------------------------- //

    @SideOnly(Side.CLIENT)
    private fun modelLocationFromState(running: Boolean?): ModelResourceLocation {
        val suffix = when (running) {
            true -> "_on"
            false -> "_off"
            null -> ""
        }
        return ModelResourceLocation(Settings.resourceDomain + ":" + Constants.ItemName.Tablet + suffix, "inventory")
    }

    @SideOnly(Side.CLIENT)
    override fun getModelLocation(stack: ItemStack): ModelResourceLocation {
        val wrapper = Tablet.Client.getWeak(stack)
        val running = wrapper?.data?.isRunning
        return modelLocationFromState(running)
    }

    @SideOnly(Side.CLIENT)
    override fun registerModelLocations() {
        for (state in listOf(null, true, false)) {
            val location = modelLocationFromState(state)
            ModelBakery.registerItemVariants(parent, ResourceLocation(location.namespace + ":" + location.path))
        }
    }

    override fun canCharge(stack: ItemStack): Boolean = true

    override fun charge(stack: ItemStack, amount: Double, simulate: Boolean): Double {
        if (amount < 0) return amount
        val data = TabletData(stack)
        return Chargeable.applyCharge(amount, data.energy, data.maxEnergy) { used ->
            if (!simulate) {
                data.energy += used
                data.save(stack)
            }
        }
    }

    // ----------------------------------------------------------------------- //

    override fun update(stack: ItemStack, world: World, entity: Entity, slot: Int, selected: Boolean) {
        if (entity is EntityPlayer) {
            // Play an audio cue to let players know when they finished analyzing a block.
            if (world.isRemote && entity.itemInUseCount == TimeToAnalyze && Items.get(entity.activeItemStack) == Constants.ItemInfo.Tablet) {
                Audio.play(entity.posX.toFloat(), entity.posY.toFloat() + 2, entity.posZ.toFloat(), ".")
            }
            Tablet.get(stack, entity).update(world, entity, slot, selected)
        }
    }

    override fun onItemUseFirst(stack: ItemStack, player: EntityPlayer, position: BlockPosition, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): EnumActionResult {
        Tablet.currentlyAnalyzing = Triple(position, side, Triple(hitX, hitY, hitZ))
        return super.onItemUseFirst(stack, player, position, side, hitX, hitY, hitZ)
    }

    override fun onItemUse(stack: ItemStack, player: EntityPlayer, position: BlockPosition, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean {
        player.setActiveHand(if (player.heldItemMainhand == stack) EnumHand.MAIN_HAND else EnumHand.OFF_HAND)
        return true
    }

    override fun onItemRightClick(stack: ItemStack, world: World, player: EntityPlayer): ActionResult<ItemStack> {
        player.setActiveHand(if (player.heldItemMainhand == stack) EnumHand.MAIN_HAND else EnumHand.OFF_HAND)
        return ActionResult.newResult(EnumActionResult.SUCCESS, stack)
    }

    override fun getMaxItemUseDuration(stack: ItemStack): Int = 72000

    override fun onPlayerStoppedUsing(stack: ItemStack, entity: EntityLivingBase, duration: Int) {
        if (entity is EntityPlayer) {
            val world = entity.entityWorld
            val didAnalyze = getMaxItemUseDuration(stack) - duration >= TimeToAnalyze
            if (didAnalyze) {
                if (!world.isRemote) {
                    val analyzing = Tablet.currentlyAnalyzing
                    if (analyzing != null) {
                        try {
                            val computer = Tablet.get(stack, entity).machine!!
                            if (computer.isRunning) {
                                val data = NBTTagCompound()
                                val (position, side, hit) = analyzing
                                computer.node()!!.sendToReachable("tablet.use", data, stack, entity, position, side, java.lang.Float.valueOf(hit.first), java.lang.Float.valueOf(hit.second), java.lang.Float.valueOf(hit.third))
                                if (!data.isEmpty) {
                                    computer.signal("tablet_use", data)
                                }
                            }
                        } catch (t: Throwable) {
                            OpenComputers.log.warn("Block analysis on tablet right click failed gloriously!", t)
                        }
                    }
                }
            } else {
                if (entity.isSneaking) {
                    if (!world.isRemote) {
                        val tablet = Tablet.Server.get(stack, entity)
                        tablet.machine!!.stop()
                        if (tablet.data.tier > Tier.One) {
                            entity.openGui(OpenComputers, GuiType.TabletInner.id, world, 0, 0, 0)
                        }
                    }
                } else {
                    if (!world.isRemote) {
                        val computer = Tablet.get(stack, entity).machine!!
                        computer.start()
                        val lastError = computer.lastError()
                        if (lastError != null) {
                            entity.sendMessage(Localization.Analyzer.LastError(lastError))
                        }
                    } else {
                        entity.openGui(OpenComputers, GuiType.Tablet.id, world, 0, 0, 0)
                    }
                }
            }
        }
    }

    override fun maxCharge(stack: ItemStack): Double = TabletData(stack).maxEnergy

    override fun getCharge(stack: ItemStack): Double = TabletData(stack).energy

    override fun setCharge(stack: ItemStack, amount: Double) {
        val data = TabletData(stack)
        data.energy = (0.0.coerceAtLeast(amount)).coerceAtMost(maxCharge(stack))
        data.save(stack)
    }

    companion object {
        const val TimeToAnalyze = 10
        // This is super-hacky, but since it's only used on the client we get away
        // with storing context information for analyzing a block in the singleton.
        @JvmField
        var currentlyAnalyzing: Triple<BlockPosition, EnumFacing, Triple<Float, Float, Float>>? = null

        @JvmStatic
        fun getId(stack: ItemStack): String? {
            if (stack.hasTagCompound() && stack.tagCompound!!.hasKey(Settings.namespace + "tablet", NBTConstants.NBT.TAG_STRING)) {
                return stack.tagCompound!!.getString(Settings.namespace + "tablet")
            }
            return null
        }

        @JvmStatic
        fun getOrCreateId(stack: ItemStack): String {
            if (!stack.hasTagCompound()) {
                stack.tagCompound = NBTTagCompound()
            }
            if (!stack.tagCompound!!.hasKey(Settings.namespace + "tablet")) {
                stack.tagCompound!!.setString(Settings.namespace + "tablet", UUID.randomUUID().toString())
            }
            return stack.tagCompound!!.getString(Settings.namespace + "tablet")
        }

        @JvmStatic
        fun get(stack: ItemStack, holder: EntityPlayer): TabletWrapper {
            return if (holder.world.isRemote) Client.get(stack, holder)
            else Server.get(stack, holder)
        }

        @JvmStatic
        @SubscribeEvent
        fun onWorldSave(e: WorldEvent.Save) {
            Server.saveAll(e.world)
        }

        @JvmStatic
        @SubscribeEvent
        fun onWorldUnload(e: WorldEvent.Unload) {
            Client.clear(e.world)
            Server.clear(e.world)
        }

        @JvmStatic
        @SubscribeEvent
        fun onClientTick(e: ClientTickEvent) {
            Client.cleanUp()
            val server = FMLCommonHandler.instance().minecraftServerInstance
            if (server is IntegratedServer && Minecraft.getMinecraft().isGamePaused) {
                // While the game is paused, manually keep all tablets alive, to avoid
                // them being cleared from the cache, causing them to stop.
                Client.keepAlive()
                Server.keepAlive()
            }
        }

        @JvmStatic
        @SubscribeEvent
        fun onServerTick(e: ServerTickEvent) {
            Server.cleanUp()
        }

        abstract class Cache : Callable<TabletWrapper>, RemovalListener<String, TabletWrapper> {
            protected open val timeout: Int = 10

            val cache: com.google.common.cache.Cache<String, TabletWrapper> = CacheBuilder.newBuilder()
                .expireAfterAccess(timeout.toLong(), TimeUnit.SECONDS)
                .removalListener(this)
                .build()

            // To allow access in cache entry init.
            private var currentStack: ItemStack? = null
            private var currentHolder: EntityPlayer? = null

            fun get(stack: ItemStack, holder: EntityPlayer): TabletWrapper {
                val id = getOrCreateId(stack)
                return cache.synchronized {
                    currentStack = stack
                    currentHolder = holder

                    // if the item is still cached, we can detect if it is dirty (client side only)
                    if (holder.world.isRemote) {
                        val weak = Client.getWeak(stack)
                        if (weak != null) {
                            val timesChanged = holder.inventory.timesChanged
                            if (timesChanged != weak.timesChanged) {
                                if (!weak.isDirty) {
                                    weak.isDirty = true
                                    ClientPacketSender.sendMachineItemStateRequest(stack)
                                }
                                weak.timesChanged = timesChanged
                            }
                        }
                    }

                    var wrapper = cache.get(id, this)

                    // Force re-load on world change, in case some components store a
                    // reference to the world object.
                    if (holder.world != wrapper.world) {
                        wrapper.writeToNBT(clearState = false)
                        wrapper.autoSave = false
                        cache.invalidate(id)
                        cache.cleanUp()
                        wrapper = cache.get(id, this)
                    }

                    currentStack = null
                    currentHolder = null

                    wrapper.stack = stack
                    wrapper.player = holder
                    wrapper
                }
            }

            override fun call(): TabletWrapper {
                return TabletWrapper(currentStack!!, currentHolder!!)
            }

            override fun onRemoval(e: RemovalNotification<String, TabletWrapper>) {
                val tablet = e.value ?: return
                if (tablet.node() != null) {
                    // Server.
                    if (tablet.autoSave) tablet.writeToNBT()
                    tablet.machine!!.stop()
                    for (node in tablet.machine!!.node()!!.network().nodes()) {
                        node.remove()
                    }
                    if (tablet.autoSave) tablet.writeToNBT()
                    tablet.markDirty()
                }
            }

            fun clear(world: World) {
                cache.synchronized {
                    val tabletsInWorld = cache.asMap().filter { it.value.world == world }
                    cache.invalidateAll(tabletsInWorld.keys)
                    cache.cleanUp()
                }
            }

            fun cleanUp() {
                cache.synchronized { cache.cleanUp() }
            }

            fun keepAlive(): ImmutableMap<String, TabletWrapper> {
                // Just touching to update last access time.
                return cache.getAllPresent(cache.asMap().keys)
            }

            private inline fun <T> com.google.common.cache.Cache<*, *>.synchronized(block: () -> T): T {
                return synchronized(this) { block() }
            }
        }

    }
    object Client : Cache() {
        override val timeout: Int = 5

        fun getWeak(stack: ItemStack): TabletWrapper? {
            val key = getId(stack) ?: return null
            val map = cache.asMap()
            return map[key]
        }

        fun get(stack: ItemStack): TabletWrapper? {
            val id = getId(stack) ?: return null
            return synchronized(cache) { cache.getIfPresent(id) }
        }
    }

    object Server : Cache() {
        fun saveAll(world: World) {
            synchronized(cache) {
                for (tablet in cache.asMap().values) {
                    if (tablet.world == world) {
                        tablet.writeToNBT()
                    }
                }
            }
        }
    }
}

class TabletWrapper(var stack: ItemStack, var player: EntityPlayer) : ComponentInventory, MachineHost, li.cil.oc.api.internal.Tablet {
    // Remember our *original* world, so we know which tablets to clear on dimension
    // changes of players holding tablets - since the player entity instance may be
    // kept the same and components are not required to properly handle world changes.
    val world: World = player.world
    override fun world(): World = world

    override val componentInventoryDelegate: ComponentInventory.State = ComponentInventory.State()

    val machine: Machine? by lazy {
        if (world.isRemote) throw IllegalStateException("Machine not available on client")
        return@lazy MachineFactory.create(this)
    }
    override fun machine(): Machine? = machine
    override fun player(): EntityPlayer = player

    val data = TabletData()

    val tablet: ComponentTablet? = if (world.isRemote) null else ComponentTablet(this)

    //// Client side only
    private var isInitialized = !world.isRemote

    var timesChanged: Int = 0

    var isDirty: Boolean = true
    ////

    // Server side only
    private var lastRunning = false

    var autoSave = true
    ////

    val isCreative: Boolean get() = data.tier == Tier.Four

    override val items: Array<ItemStack> get() = data.items

    override fun facing(): EnumFacing = RotationHelper.fromYaw(player.rotationYaw)

    override fun toLocal(value: EnumFacing): EnumFacing =
        RotationHelper.toLocal(EnumFacing.NORTH, facing(), value)

    override fun toGlobal(value: EnumFacing): EnumFacing =
        RotationHelper.toGlobal(EnumFacing.NORTH, facing(), value)

    fun readFromNBT() {
        if (stack.hasTagCompound()) {
            val nbt = stack.tagCompound!!
            load(nbt)
            if (!world.isRemote) {
                tablet?.load(nbt.getCompoundTag(Settings.namespace + "component"))
                machine!!.load(nbt.getCompoundTag(Settings.namespace + "data"))
            }
        }
    }

    fun writeToNBT(clearState: Boolean = true) {
        if (!stack.hasTagCompound()) {
            stack.tagCompound = NBTTagCompound()
        }
        val nbt = stack.tagCompound!!
        if (!world.isRemote) {
            if (!nbt.hasKey(Settings.namespace + "data")) {
                nbt.setTag(Settings.namespace + "data", NBTTagCompound())
            }
            nbt.setNewCompoundTag(Settings.namespace + "component") { tablet?.save(it) }
            nbt.setNewCompoundTag(Settings.namespace + "data") { machine!!.save(it) }

            if (clearState) {
                // Force tablets into stopped state to avoid errors when trying to
                // load deleted machine states.
                nbt.getCompoundTag(Settings.namespace + "data").removeTag("state")
            }
        }
        save(nbt)
    }

    init {
        readFromNBT()
        if (!world.isRemote) {
            Network.joinNewNetwork(machine!!.node())
            val tablet = tablet!!
            val charge = (data.energy - tablet.node().globalBuffer()).coerceAtLeast(0.0)
            tablet.node().changeBuffer(charge)
            writeToNBT()
        }
    }

    // ----------------------------------------------------------------------- //

    override fun onConnect(node: Node) {
        if (node == this.node()) {
            connectComponents()
            node.connect(tablet?.node())
        } else {
            val host = node.host()
            if (host is TextBuffer) {
                host.setMaximumColorDepth(TextBuffer.ColorDepth.FourBit)
                host.setMaximumResolution(80, 25)
            }
        }
    }

    override fun connectItemNode(node: Node?) {
        super.connectItemNode(node)
        if (node != null) {
            when (val host = node.host()) {
                is TextBuffer -> {
                    for (comp in components) {
                        if (comp is Keyboard) {
                            host.node()!!.connect(comp.node())
                        }
                    }
                }
                is Keyboard -> {
                    for (comp in components) {
                        if (comp is TextBuffer) {
                            host.node()!!.connect(comp.node())
                        }
                    }
                }
            }
        }
    }

    override fun onDisconnect(node: Node) {
        if (node == this.node()) {
            disconnectComponents()
            tablet?.node()?.remove()
        }
    }

    override fun onMessage(message: Message) {}

    override val host: TabletWrapper
        get() = this

    override fun getSizeInventory(): Int = items.size

    override fun isItemValidForSlot(slot: Int, stack: ItemStack): Boolean {
        if (slot != sizeInventory - 1) return false
        val driver = Driver.driverFor(stack, javaClass) ?: return false
        return driver != DriverScreen &&
            driver.slot(stack) == containerSlotType &&
            driver.tier(stack) <= containerSlotTier
    }

    override fun isUsableByPlayer(player: EntityPlayer): Boolean = machine!!.canInteract(player.name)

    override fun markDirty() {
        data.save(stack)
        player.inventory.markDirty()
    }

    // ----------------------------------------------------------------------- //

    override fun xPosition(): Double = player.posX

    override fun yPosition(): Double = player.posY + player.eyeHeight

    override fun zPosition(): Double = player.posZ

    override fun markChanged() {}

    // ----------------------------------------------------------------------- //

    val containerSlotType: String
        get() = if (data.container.isEmpty) Slot.None
        else {
            val driver = Driver.driverFor(data.container, javaClass)
            if (driver is Container) driver.providedSlot(data.container) else Slot.None
        }

    val containerSlotTier: Int
        get() = if (data.container.isEmpty) Tier.None
        else {
            val driver = Driver.driverFor(data.container, javaClass)
            if (driver is Container) driver.providedTier(data.container) else Tier.None
        }

    override fun internalComponents(): Iterable<ItemStack> = (0 until sizeInventory)
        .filter { !getStackInSlot(it).isEmpty && isComponentSlot(it, getStackInSlot(it)) }
        .map { getStackInSlot(it) }

    override fun componentSlot(address: String): Int =
        components.indexOfFirst { it?.node()?.address() == address }

    override fun onMachineConnect(node: Node) = onConnect(node)

    override fun onMachineDisconnect(node: Node) = onDisconnect(node)

    // ----------------------------------------------------------------------- //

    override fun node(): Node? = machine!!.node()

    // ----------------------------------------------------------------------- //

    fun update(world: World, player: EntityPlayer, slot: Int, selected: Boolean) {
        this.player = player
        if (!isInitialized) {
            isInitialized = true
            // This delayed initialization on the client side is required to allow
            // the server to set up the tablet wrapper first (since packets generated
            // in the component setup would otherwise be queued before the events that
            // caused this wrapper's initialization).
            connectComponents()
            for (comp in components) {
                if (comp is TextBuffer) {
                    comp.setMaximumColorDepth(TextBuffer.ColorDepth.FourBit)
                    comp.setMaximumResolution(80, 25)
                }
            }

            ClientPacketSender.sendMachineItemStateRequest(stack)
        }
        if (!world.isRemote) {
            if (isCreative && Settings.get.isTickMultiple(world)) {
                (machine!!.node() as Connector).changeBuffer(Double.POSITIVE_INFINITY)
            }
            val machine = machine!!
            machine.update()
            updateComponents()
            data.isRunning = machine.isRunning
            data.energy = tablet!!.node().globalBuffer()
            data.maxEnergy = tablet.node().globalBufferSize()

            if (lastRunning != machine.isRunning) {
                lastRunning = machine.isRunning
                markDirty()

                if (player is EntityPlayerMP) {
                    ServerPacketSender.sendMachineItemState(player, stack, machine.isRunning)
                }

                if (machine.isRunning) {
                    for (comp in components) {
                        if (comp is TextBuffer) {
                            comp.setPowerState(true)
                        }
                    }
                }
            }
        }
    }

    // ----------------------------------------------------------------------- //

    override fun load(nbt: NBTTagCompound) {
        data.load(nbt)
    }

    override fun save(nbt: NBTTagCompound) {
        saveComponents()
        data.save(nbt)
    }
}