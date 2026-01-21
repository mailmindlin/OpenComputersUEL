package li.cil.oc.common

import li.cil.oc.Constants
import li.cil.oc.Localization
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.detail.ItemInfo
import li.cil.oc.api.internal.Colored
import li.cil.oc.api.internal.Rack
import li.cil.oc.api.internal.Server
import li.cil.oc.api.machine.Machine
import li.cil.oc.api.machine.MachineHost
import li.cil.oc.api.network.Environment
import li.cil.oc.api.network.SidedComponent
import li.cil.oc.api.network.SidedEnvironment
import li.cil.oc.client.renderer.PetRenderer
import li.cil.oc.common.asm.ClassTransformer
import li.cil.oc.common.capabilities.CapabilityColored
import li.cil.oc.common.capabilities.CapabilityEnvironment
import li.cil.oc.common.capabilities.CapabilitySidedComponent
import li.cil.oc.common.capabilities.CapabilitySidedEnvironment
import li.cil.oc.common.component.TerminalServer
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.item.data.MicrocontrollerData
import li.cil.oc.common.item.data.RobotData
import li.cil.oc.common.item.data.TabletData
import li.cil.oc.common.item.traits.Chargeable
import li.cil.oc.common.recipe.Recipes
import li.cil.oc.common.tileentity.Robot
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.WirelessRedstone
import li.cil.oc.server.PacketSender as ServerPacketSender
import li.cil.oc.server.component.Keyboard
import li.cil.oc.server.component.RedstoneWireless
import li.cil.oc.server.machine.Callbacks
import li.cil.oc.server.machine.Machine
import li.cil.oc.server.machine.luac.LuaStateFactory
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.PlayerUtils
import li.cil.oc.util.SideTracker
import li.cil.oc.util.StackOption
import li.cil.oc.util.UpdateCheck
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.init.SoundEvents
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.SoundCategory
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.event.AttachCapabilitiesEvent
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.event.world.BlockEvent
import net.minecraftforge.event.world.ChunkEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.Optional
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent.ItemCraftedEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent.ItemPickupEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent
import java.util.Calendar
import java.util.Collections
import java.util.PriorityQueue
import java.util.WeakHashMap
import kotlin.concurrent.thread

object EventHandler {
    private var serverTicks = 0L
    private val pendingServerTimed = PriorityQueue<Pair<Long, () -> Unit>>(compareBy { -it.first })

    private val pendingServer = mutableListOf<() -> Unit>()

    private val pendingClient = mutableListOf<() -> Unit>()

    private val runningRobots = mutableSetOf<Robot>()

    private val keyboards: MutableSet<Keyboard> = Collections.newSetFromMap(WeakHashMap<Keyboard, Boolean>())

    private val machines = mutableSetOf<Machine>()

    @JvmStatic
    fun onRobotStart(robot: Robot) {
        runningRobots.add(robot)
    }

    @JvmStatic
    fun onRobotStopped(robot: Robot) {
        runningRobots.remove(robot)
    }

    @JvmStatic
    fun addKeyboard(keyboard: Keyboard) {
        keyboards.add(keyboard)
    }

    @JvmStatic
    fun scheduleClose(machine: Machine) {
        machines.add(machine)
    }

    @JvmStatic
    fun unscheduleClose(machine: Machine) {
        machines.remove(machine)
    }

    @JvmStatic
    fun scheduleServer(tileEntity: TileEntity) {
        if (SideTracker.isServer()) {
            synchronized(pendingServer) {
                pendingServer.add { Network.joinOrCreateNetwork(tileEntity) }
            }
        }
    }

    @JvmStatic
    fun scheduleServer(f: () -> Unit) {
        synchronized(pendingServer) {
            pendingServer.add(f)
        }
    }

    @JvmStatic
    fun scheduleServer(f: () -> Unit, delay: Int) {
        synchronized(pendingServerTimed) {
            pendingServerTimed.add((serverTicks + maxOf(delay, 0)) to f)
        }
    }

    @JvmStatic
    fun scheduleClient(f: () -> Unit) {
        synchronized(pendingClient) {
            pendingClient.add(f)
        }
    }

    @JvmStatic
    @Optional.Method(modid = Mods.IDs.IndustrialCraft2)
    fun scheduleIC2Add(tileEntity: power.IndustrialCraft2Experimental) {
        if (SideTracker.isServer()) {
            synchronized(pendingServer) {
                if (tileEntity is ic2.api.energy.tile.IEnergyTile) {
                    pendingServer.add {
                        if (!tileEntity.addedToIC2PowerGrid && !tileEntity.isInvalid) {
                            MinecraftForge.EVENT_BUS.post(ic2.api.energy.event.EnergyTileLoadEvent(tileEntity))
                            tileEntity.addedToIC2PowerGrid = true
                        }
                    }
                }
            }
        }
    }

    @JvmStatic
    @Optional.Method(modid = Mods.IDs.AppliedEnergistics2)
    fun scheduleAE2Add(tileEntity: power.AppliedEnergistics2) {
        if (SideTracker.isServer()) {
            synchronized(pendingServer) {
                pendingServer.add { tileEntity.updateGridNodeState() }
            }
        }
    }

    @JvmStatic
    fun scheduleWirelessRedstone(rs: RedstoneWireless) {
        if (SideTracker.isServer()) {
            synchronized(pendingServer) {
                pendingServer.add {
                    if (rs.node.network() != null) {
                        WirelessRedstone.addReceiver(rs)
                        WirelessRedstone.updateOutput(rs)
                    }
                }
            }
        }
    }

    @SubscribeEvent
    fun onAttachCapabilitiesItemStack(event: AttachCapabilitiesEvent<ItemStack>) {
        if (!event.capabilities.containsKey(Chargeable.KEY)) {
            val stack = event.`object`
            val item = stack.item
            when {
                item is Chargeable -> event.addCapability(Chargeable.KEY, Chargeable.Provider(stack, item))
                item is li.cil.oc.api.driver.item.Chargeable -> {
                    val subItem = Delegator.subItem(stack)
                    if (subItem is Chargeable) {
                        event.addCapability(Chargeable.KEY, Chargeable.Provider(stack, subItem))
                    }
                }
            }
        }
    }

    @SubscribeEvent
    fun onAttachCapabilities(event: AttachCapabilitiesEvent<TileEntity>) {
        val obj = event.`object`

        if (obj is Environment) {
            event.addCapability(CapabilityEnvironment.ProviderEnvironment, CapabilityEnvironment.Provider(obj))
        }

        when {
            obj is Environment && obj is SidedComponent -> {
                event.addCapability(CapabilitySidedComponent.SidedComponent, CapabilitySidedComponent.Provider(obj))
            }
            obj is SidedEnvironment -> {
                event.addCapability(CapabilitySidedEnvironment.ProviderSidedEnvironment, CapabilitySidedEnvironment.Provider(obj))
            }
        }

        if (obj is Colored) {
            event.addCapability(CapabilityColored.ProviderColored, CapabilityColored.Provider(obj))
        }
    }

    @SubscribeEvent
    fun onServerTick(e: ServerTickEvent) {
        if (e.phase == TickEvent.Phase.START) {
            val adds = synchronized(pendingServer) {
                val result = pendingServer.toTypedArray()
                pendingServer.clear()
                result
            }
            for (callback in adds) {
                try {
                    callback()
                } catch (t: Throwable) {
                    OpenComputers.log.warn("Error in scheduled tick action.", t)
                }
            }

            serverTicks += 1
            while (pendingServerTimed.isNotEmpty() && pendingServerTimed.peek().first < serverTicks) {
                val (_, callback) = pendingServerTimed.poll()
                try {
                    callback()
                } catch (t: Throwable) {
                    OpenComputers.log.warn("Error in scheduled tick action.", t)
                }
            }

            val invalid = mutableListOf<Robot>()
            for (robot in runningRobots) {
                if (robot.isInvalid) {
                    invalid.add(robot)
                } else if (robot.world != null) {
                    robot.machine.update()
                }
            }
            runningRobots.removeAll(invalid)
        } else if (e.phase == TickEvent.Phase.END) {
            // Clean up machines *after* a tick, to allow stuff to be saved, first.
            val closed = mutableListOf<Machine>()
            for (machine in machines) {
                if (machine.tryClose()) {
                    closed.add(machine)
                    if (machine.host.world() == null || !machine.host.world().isBlockLoaded(BlockPosition(machine.host).toBlockPos())) {
                        machine.node?.remove()
                    }
                }
            }
            machines.removeAll(closed)
        }
    }

    @SubscribeEvent
    fun onClientTick(e: ClientTickEvent) {
        if (e.phase == TickEvent.Phase.START) {
            val adds = synchronized(pendingClient) {
                val result = pendingClient.toTypedArray()
                pendingClient.clear()
                result
            }
            for (callback in adds) {
                try {
                    callback()
                } catch (t: Throwable) {
                    OpenComputers.log.warn("Error in scheduled tick action.", t)
                }
            }
        }
    }

    @SubscribeEvent
    fun playerLoggedIn(e: PlayerLoggedInEvent) {
        if (SideTracker.isServer()) {
            val player = e.player
            if (player is FakePlayer) return
            if (player is EntityPlayerMP) {
                if (!LuaStateFactory.isAvailable && !LuaStateFactory.luajRequested) {
                    player.sendMessage(Localization.Chat.WarningLuaFallback)
                }
                if (Recipes.hadErrors) {
                    player.sendMessage(Localization.Chat.WarningRecipes)
                }
                if (ClassTransformer.hadErrors) {
                    player.sendMessage(Localization.Chat.WarningClassTransformer)
                }
                if (ClassTransformer.hadSimpleComponentErrors) {
                    player.sendMessage(Localization.Chat.WarningSimpleComponent)
                }
                // Gaaah, MC 1.8 y u do this to me? Sending the packets here directly can lead to them
                // arriving on the client before it has a world and player instance, which causes all
                // sorts of trouble. It worked perfectly fine in MC 1.7.10... oSWDEG'PIl;dg'poinEG\a'pi=
                scheduleServer {
                    ServerPacketSender.sendPetVisibility(null, player)
                    ServerPacketSender.sendLootDisks(player)
                }
                // Do update check in local games and for OPs.
                val server = FMLCommonHandler.instance().minecraftServerInstance
                if (!server.isDedicatedServer || server.playerList.canSendCommands(player.gameProfile)) {
                    thread {
                        UpdateCheck.info()?.let { release ->
                            player.sendMessage(Localization.Chat.InfoNewVersion(release.tag_name))
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    fun clientLoggedIn(e: ClientConnectedToServerEvent) {
        PetRenderer.isInitialized = false
        PetRenderer.hidden.clear()
        Loot.disksForClient.clear()
        Loot.disksForCyclingClient.clear()

        client.Sound.startLoop(null, "computer_running", 0f, 0)
        scheduleServer { client.Sound.stopLoop(null) }
    }

    @SubscribeEvent
    fun onBlockBreak(e: BlockEvent.BreakEvent) {
        when (val te = e.world.getTileEntity(e.pos)) {
            is tileentity.Case -> {
                if (te.isCreative && (!e.player.capabilities.isCreativeMode || !te.canInteract(e.player.name))) {
                    e.isCanceled = true
                }
            }
            is tileentity.RobotProxy -> {
                val robot = te.robot
                if (robot.isCreative && (!e.player.capabilities.isCreativeMode || !robot.canInteract(e.player.name))) {
                    e.isCanceled = true
                }
            }
        }
    }

    @SubscribeEvent
    fun onPlayerRespawn(e: PlayerRespawnEvent) {
        keyboards.forEach { it.releasePressedKeys(e.player) }
    }

    @SubscribeEvent
    fun onPlayerChangedDimension(e: PlayerChangedDimensionEvent) {
        keyboards.forEach { it.releasePressedKeys(e.player) }
    }

    @SubscribeEvent
    fun onPlayerLogout(e: PlayerLoggedOutEvent) {
        keyboards.forEach { it.releasePressedKeys(e.player) }
    }

    @SubscribeEvent
    fun onEntityJoinWorld(e: EntityJoinWorldEvent) {
        if (Settings.get.giveManualToNewPlayers && !e.world.isRemote) {
            val entity = e.entity
            if (entity is EntityPlayer && entity !is FakePlayer) {
                val persistedData = PlayerUtils.persistedData(entity)
                if (!persistedData.getBoolean(Settings.namespace + "receivedManual")) {
                    persistedData.setBoolean(Settings.namespace + "receivedManual", true)
                    entity.inventory.addItemStackToInventory(api.Items.get(Constants.ItemName.Manual).createItemStack(1))
                }
            }
        }
    }

    private val drone by lazy { api.Items.get(Constants.ItemName.Drone) }
    private val eeprom by lazy { api.Items.get(Constants.ItemName.EEPROM) }
    private val mcu by lazy { api.Items.get(Constants.BlockName.Microcontroller) }
    private val navigationUpgrade by lazy { api.Items.get(Constants.ItemName.NavigationUpgrade) }
    private val robot by lazy { api.Items.get(Constants.BlockName.Robot) }
    private val tablet by lazy { api.Items.get(Constants.ItemName.Tablet) }

    @SubscribeEvent
    fun onCrafting(e: ItemCraftedEvent) {
        var didRecraft = false

        didRecraft = recraft(e, navigationUpgrade) { stack ->
            // Restore the map currently used in the upgrade.
            val driver = api.Driver.driverFor(e.crafting)
            if (driver != null) {
                StackOption(ItemStack(driver.dataTag(stack).getCompoundTag(Settings.namespace + "map")))
            } else {
                StackOption.Empty
            }
        } || didRecraft

        didRecraft = recraft(e, mcu) { stack ->
            // Restore EEPROM currently used in microcontroller.
            StackOption(MicrocontrollerData(stack).components.find { api.Items.get(it) == eeprom })
        } || didRecraft

        didRecraft = recraft(e, drone) { stack ->
            // Restore EEPROM currently used in drone.
            StackOption(MicrocontrollerData(stack).components.find { api.Items.get(it) == eeprom })
        } || didRecraft

        didRecraft = recraft(e, robot) { stack ->
            // Restore EEPROM currently used in robot.
            StackOption(RobotData(stack).components.find { api.Items.get(it) == eeprom })
        } || didRecraft

        didRecraft = recraft(e, tablet) { stack ->
            // Restore EEPROM currently used in tablet.
            StackOption(TabletData(stack).items.filterNot { it.isEmpty }.find { api.Items.get(it) == eeprom })
        } || didRecraft

        // Presents?
        val player = e.player
        when {
            player is FakePlayer -> {} // No presents for you, automaton. Such discrimination. Much bad conscience.
            player is EntityPlayerMP && player.entityWorld != null && !player.entityWorld.isRemote -> {
                // Presents!? If we didn't recraft, it's an OC item, and the time is right...
                if (Settings.get.presentChance > 0 && !didRecraft && api.Items.get(e.crafting) != null &&
                    player.rng.nextFloat() < Settings.get.presentChance && timeForPresents) {
                    // Presents!
                    val present = api.Items.get(Constants.ItemName.Present).createItemStack(1)
                    player.world.playSound(player, player.posX, player.posY, player.posZ, SoundEvents.BLOCK_NOTE_PLING, SoundCategory.MASTER, 0.2f, 1f)
                    InventoryUtils.addToPlayerInventory(present, player)
                }
            }
        }

        Achievement.onCraft(e.crafting, e.player)
    }

    @SubscribeEvent
    fun onPickup(e: ItemPickupEvent) {
        val entity = e.originalEntity
        val stack = entity?.item
        if (stack != null) {
            Achievement.onAssemble(stack, e.player)
            Achievement.onCraft(stack, e.player)
        }
    }

    private val timeForPresents: Boolean
        get() {
            val now = Calendar.getInstance()
            val month = now.get(Calendar.MONTH)
            val dayOfMonth = now.get(Calendar.DAY_OF_MONTH)
            // On the 12th day of Christmas, my robot brought to me~
            return (month == Calendar.DECEMBER && dayOfMonth > 24) || (month == Calendar.JANUARY && dayOfMonth < 7) ||
                (month == Calendar.FEBRUARY && dayOfMonth == 14) ||
                (month == Calendar.APRIL && dayOfMonth == 22) ||
                (month == Calendar.MAY && dayOfMonth == 1) ||
                (month == Calendar.OCTOBER && dayOfMonth == 3) ||
                (month == Calendar.DECEMBER && dayOfMonth == 14)
        }

    @JvmStatic
    val isItTime: Boolean
        get() {
            val now = Calendar.getInstance()
            val month = now.get(Calendar.MONTH)
            val dayOfMonth = now.get(Calendar.DAY_OF_MONTH)
            return month == Calendar.APRIL && dayOfMonth == 1
        }

    private fun recraft(e: ItemCraftedEvent, item: ItemInfo, callback: (ItemStack) -> StackOption): Boolean {
        if (api.Items.get(e.crafting) == item) {
            for (slot in 0 until e.craftMatrix.sizeInventory) {
                val stack = e.craftMatrix.getStackInSlot(slot)
                if (api.Items.get(stack) == item) {
                    callback(stack).stack?.let { extra ->
                        InventoryUtils.addToPlayerInventory(extra, e.player)
                    }
                }
            }
            return true
        }
        return false
    }

    // This is called from the ServerThread *and* the ClientShutdownThread, which
    // can potentially happen at the same time... for whatever reason. So let's
    // synchronize what we're doing here to avoid race conditions (e.g. when
    // disposing networks, where this actually triggered an assert).
    @SubscribeEvent
    @Synchronized
    fun onWorldUnload(e: WorldEvent.Unload) {
        if (!e.world.isRemote) {
            for (te in e.world.loadedTileEntityList) {
                if (te is tileentity.traits.TileEntity) {
                    te.dispose()
                }
            }
            for (entity in e.world.loadedEntityList) {
                if (entity is MachineHost) {
                    entity.machine().stop()
                }
            }

            Callbacks.clear()
        } else {
            TerminalServer.loaded.clear()
        }
    }

    @SubscribeEvent
    fun onChunkUnload(e: ChunkEvent.Unload) {
        if (!e.world.isRemote) {
            for (entityList in e.chunk.entityLists) {
                for (entity in entityList) {
                    when (entity) {
                        is MachineHost -> {
                            val machine = entity.machine()
                            if (machine is Machine) {
                                scheduleClose(machine)
                            }
                        }
                        is Rack -> {
                            for (i in 0 until entity.sizeInventory) {
                                val mountable = entity.getMountable(i)
                                if (mountable is Server && mountable.machine() != null) {
                                    mountable.machine().stop()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
