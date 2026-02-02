package li.cil.oc.server.machine

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.api.detail.MachineAPI
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.item.CallBudget
import li.cil.oc.api.driver.item.Processor
import li.cil.oc.api.machine.*
import li.cil.oc.api.network.*
import li.cil.oc.common.EventHandler
import li.cil.oc.common.SaveHandler
import li.cil.oc.common.Slot
import li.cil.oc.common.tileentity.traits.Computer
import li.cil.oc.server.PacketSender
import li.cil.oc.server.component.ManagedEnvironmentKt
import li.cil.oc.server.component.world
import li.cil.oc.server.driver.Registry
import li.cil.oc.server.fs.FileSystem
import li.cil.oc.util.*
import li.cil.oc.util.Result
import li.cil.oc.util.Stack
import net.minecraft.client.Minecraft
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.nbt.*
import net.minecraft.server.integrated.IntegratedServer
import net.minecraftforge.common.util.Constants.NBT
import net.minecraftforge.fml.common.FMLCommonHandler
import java.util.concurrent.TimeUnit
import kotlin.jvm.Throws
import kotlin.math.max
import kotlin.math.min
import li.cil.oc.api.machine.Machine as APIMachine

class Machine(val host: MachineHost) : ManagedEnvironmentKt(), APIMachine, Runnable, DeviceInfo {
    override val node: ComponentConnector? = nodeFactory(Visibility.Network)
        .withComponent("computer", Visibility.Neighbors)
        .withConnector(Settings.get.bufferComputer)
        .create()

    override fun host(): MachineHost = host

    override fun architecture(): Architecture? = architecture

    override fun maxComponents(): Int = maxComponents

    override fun worldTime(): Long = worldTime

    val tmp = if (Settings.get.tmpSize > 0) {
        FileSystem.asManagedEnvironment(
            FileSystem.fromMemory(Settings.get.tmpSize.toLong() * 1024),
            "tmpfs", null, null, 5
        )
    } else null

    var architecture: Architecture? = null

    internal val state = Stack<State>()

    private val _components = mutableMapOf<String, String>()

    private val addedComponents = mutableSetOf<Component>()

    private val _users = mutableSetOf<String>()

    private val signals = java.util.ArrayDeque<Signal>()

    var maxComponents = 0

    private var maxCallBudget = 1.0

    private var hasMemory = false

    @Volatile
    private var callBudget = 0.0

    // We want to ignore the call limit in synchronized calls to avoid errors.
    private var inSynchronizedCall = false

    // ----------------------------------------------------------------------- //

    var worldTime = 0L // Game-world time for os.time().

    private var uptime = 0L // Game-world time [ticks] for os.uptime().

    private var cpuTotal = 0L // Pseudo-real-world time [ns] for os.clock().

    private var cpuStart = 0L // Pseudo-real-world time [ns] for os.clock().

    private var remainIdle = 0 // Ticks left to sleep before resuming.

    private var remainingPause = 0 // Ticks left to wait before resuming.

    private var usersChanged = false // Send updated users list to clients?

    private var message: String? = null // For error messages.

    private var cost = Settings.get.computerCost * Settings.get.tickFrequency

    private val maxSignalQueueSize = Settings.get.maxSignalQueueSize

    init {
        state.push(State.Stopped)
    }

    // ----------------------------------------------------------------------- //

    override fun onHostChanged() {
        val components = host.internalComponents()
        maxComponents = components.sumOf { item ->
            if (item != null) {
                val driver = Driver.driverFor(item, host.javaClass)
                if (driver is Processor) driver.supportedComponents(item) else 0
            } else 0
        }
        val callBudgets = components.mapNotNull { stack ->
            val driver = Driver.driverFor(stack, host.javaClass)
            if (driver is CallBudget) driver.getCallBudget(stack) else null
        }
        maxCallBudget = if (callBudgets.isEmpty()) 1.0 else callBudgets.sum() / callBudgets.size
        var newArchitecture: Architecture? = null
        components.find { stack ->
            if (stack != null) {
                val driver = Driver.driverFor(stack, host.javaClass)
                if (driver is Processor && driver.slot(stack) == Slot.CPU) {
                    val clazz = driver.architecture(stack)
                    if (clazz != null) {
                        if (architecture == null || architecture!!.javaClass != clazz) {
                            try {
                                newArchitecture = clazz.getConstructor(APIMachine::class.java).newInstance(this)
                            } catch (t: Throwable) {
                                OpenComputers.log.warn("Failed instantiating a CPU architecture.", t)
                            }
                        } else {
                            newArchitecture = architecture
                        }
                        true
                    } else false
                } else false
            } else false
        }
        // This needs to operate synchronized against the worker thread, to avoid the
        // architecture changing while it is currently being executed.
        if (newArchitecture != architecture) {
            synchronized(this) {
                architecture = newArchitecture
                if (architecture != null && node!!.network() != null) architecture!!.onConnect()
            }
        }
        hasMemory = architecture?.recomputeMemory(components) ?: false
    }

    override fun components(): MutableMap<String, String> = _components

    override fun componentCount(): Int {
        val baseCount = _components.values.sumOf { name -> if (name != "filesystem") 1.0 else 0.25 }
        val addedCount = addedComponents.sumOf { component -> if (component.name() != "filesystem") 1.0 else 0.25 }
        return (baseCount + addedCount - 1).toInt() // -1 = this computer
    }

    override fun tmpAddress(): String? = tmp?.node()?.address()

    override fun lastError(): String? = message

    override fun setCostPerTick(value: Double) {
        cost = value * Settings.get.tickFrequency
    }

    override fun getCostPerTick(): Double = cost / Settings.get.tickFrequency

    override fun users(): Array<String> = synchronized(_users) { _users.toTypedArray() }

    override fun upTime(): Double {
        // Convert from old saves (set to -timeStarted on load).
        if (uptime < 0) {
            uptime = worldTime + uptime
        }
        // World time is in ticks, and each second has 20 ticks. Since we
        // want uptime() to return real seconds, though, we'll divide it
        // accordingly.
        return uptime / 20.0
    }

    override fun cpuTime(): Double = (cpuTotal + (System.nanoTime() - cpuStart)) * 10e-10

    // ----------------------------------------------------------------------- //

    override fun getDeviceInfo(): Map<String, String>? = (host as? DeviceInfo)?.deviceInfo

    // ----------------------------------------------------------------------- //

    override fun canInteract(player: String): Boolean = !Settings.get.canComputersBeOwned ||
        synchronized(_users) { _users.isEmpty() || _users.contains(player) } ||
        FMLCommonHandler.instance().minecraftServerInstance.isSinglePlayer || run {
        val config = FMLCommonHandler.instance().minecraftServerInstance.playerList
        val entity = config.getPlayerByUsername(player)
        entity != null && config.canSendCommands(entity.gameProfile)
    }

    override fun isRunning(): Boolean = synchronized(state) { state.peek() != State.Stopped && state.peek() != State.Stopping }

    override fun isPaused(): Boolean = synchronized(state) { state.peek() == State.Paused && remainingPause > 0 }

    override fun start(): Boolean = synchronized(state) {
        when (state.peek()) {
            State.Stopped -> {
                if (node!!.network() != null) {
                    onHostChanged()
                    processAddedComponents()
                    verifyComponents()
                    if (!Settings.get.ignorePower && node.globalBuffer() < cost) {
                        // No beep! We have no energy after all :P
                        crash("gui.Error.NoEnergy")
                        false
                    } else if (architecture == null || maxComponents == 0) {
                        beep("-")
                        crash("gui.Error.NoCPU")
                        false
                    } else if (componentCount() > maxComponents) {
                        beep("-..")
                        crash("gui.Error.ComponentOverflow")
                        false
                    } else if (!hasMemory) {
                        beep("-.")
                        crash("gui.Error.NoRAM")
                        false
                    } else if (!init()) {
                        beep("--")
                        false
                    } else {
                        switchTo(State.Starting)
                        uptime = 0
                        node.sendToReachable("computer.started")
                        true
                    }
                } else false
            }
            State.Paused -> {
                if (remainingPause > 0) {
                    remainingPause = 0
                    host.markChanged()
                    true
                } else false
            }
            State.Stopping -> {
                switchTo(State.Restarting)
                EventHandler.unscheduleClose(this)
                true
            }
            else -> false
        }
    }

    override fun pause(seconds: Double): Boolean {
        val ticksToPause = max((seconds * 20).toInt(), 0)
        fun shouldPause(st: State) = when (st) {
            State.Stopping, State.Stopped -> false
            State.Paused -> ticksToPause > remainingPause
            else -> true
        }
        if (shouldPause(synchronized(state) { state.peek() })) {
            // Check again when we get the lock, might have changed since.
            synchronized(this) {
                synchronized(state) {
                    if (shouldPause(state.peek())) {
                        if (state.peek() != State.Paused) {
                            assert(!state.contains(State.Paused))
                            state.push(State.Paused)
                        }
                        remainingPause = ticksToPause
                        host.markChanged()
                        return true
                    }
                }
            }
        }
        return false
    }

    override fun stop(): Boolean = synchronized(state) {
        when (state.peek()) {
            State.Stopped, State.Stopping -> false
            else -> {
                state.push(State.Stopping)
                EventHandler.scheduleClose(this)
                true
            }
        }
    }

    override fun consumeCallBudget(callCost: Double) {
        if (architecture != null && architecture!!.isInitialized && !inSynchronizedCall) {
            val clampedCost = max(0.0, callCost)
            if (clampedCost > callBudget) {
                throw LimitReachedException()
            }
            callBudget -= clampedCost
        }
    }

    override fun beep(frequency: Short, duration: Short) {
        PacketSender.sendSound(host.world(), host.xPosition(), host.yPosition(), host.zPosition(), frequency.toInt(), duration.toInt())
    }

    override fun beep(pattern: String) {
        PacketSender.sendSound(host.world(), host.xPosition(), host.yPosition(), host.zPosition(), pattern)
    }

    override fun crash(message: String): Boolean {
        this.message = message
        synchronized(state) {
            val result = stop()
            if (state.peek() == State.Stopping) {
                // When crashing, make sure there's no "Running" left in the stack.
                state.clear()
                state.push(State.Stopping)
            }
            return result
        }
    }

    fun convertArg(param: Any?): Any? = when (param) {
        is Boolean -> param
        is Char -> param.code
        is Byte -> param
        is Short -> param
        is Int -> param
        is Long -> param
        is Number -> param.toDouble()
        is String -> param
        is ByteArray -> param
        is NBTTagCompound -> param
        else -> {
            OpenComputers.log.warn("Trying to push signal with an unsupported argument of type ${param?.javaClass?.name}")
            null
        }
    }

    override fun signal(name: String, vararg args: Any?): Boolean {
        synchronized(state) {
            when (state.peek()) {
                State.Stopped, State.Stopping -> return false
                else -> {
                    synchronized(signals) {
                        if (signals.size >= maxSignalQueueSize) return false
                        else if (args.isEmpty()) {
                            signals.add(Signal(name, emptyArray()))
                        } else {
                            signals.add(Signal(name, args.map { arg ->
                                when (arg) {
                                    null, Unit -> null
                                    is Map<*, *> -> if (arg.isEmpty() || (arg.keys.first() is String && arg.values.first() is String)) arg else null
                                    /*is MutableMap<*, *> -> if (arg.isEmpty() || (arg.keys.first() is String && arg.values.first() is String)) arg.toMap() else null
                                    is java.util.Map<*, *> -> {
                                        val convertedMap = mutableMapOf<Any?, Any?>()
                                        for ((key, value) in arg) {
                                            val convertedKey = convertArg(key)
                                            if (convertedKey != null) {
                                                val convertedValue = convertArg(value)
                                                if (convertedValue != null) {
                                                    convertedMap[convertedKey] = convertedValue
                                                }
                                            }
                                        }
                                        convertedMap
                                    }*/
                                    else -> convertArg(arg)
                                }
                            }.toTypedArray()))
                        }
                    }
                }
            }
        }

        architecture?.onSignal()
        return true
    }

    override fun popSignal(): li.cil.oc.api.machine.Signal? = synchronized(signals) {
        if (signals.isEmpty()) null else signals.poll().convert()
    }

    override fun methods(value: Any): MutableMap<String, Callback> {
        return Callbacks(value).mapValues { it.value.annotation }.toMutableMap()
    }

    override fun invoke(address: String, method: String, args: Array<Any?>): Array<out Any?> {
        if (node != null && node.network() != null) {
            val component = node.network().node(address)
            if (component is li.cil.oc.server.network.Component && (component.canBeSeenFrom(node) || component == node)) {
                val annotation = component.annotation(method)
                if (annotation.direct) {
                    consumeCallBudget(1.0 / annotation.limit)
                }
                return component.invoke(method, this, *args)
            } else {
                throw IllegalArgumentException("no such component")
            }
        } else {
            // Not really, but makes the VM stop, which is what we want in this case,
            // because it means we've been disconnected / disposed already.
            throw LimitReachedException()
        }
    }

    override fun invoke(value: Value, method: String, args: Array<Any?>): Array<out Any?> {
        val callback = Callbacks(value)[method] ?: throw NoSuchMethodException()
        val annotation = callback.annotation
        if (annotation.direct) {
            consumeCallBudget(1.0 / annotation.limit)
        }
        val arguments = ArgumentsImpl(args.toList())
        return Registry.run { callback(value, this@Machine, arguments).convert() }
    }

    override fun addUser(name: String) {
        if (_users.size >= Settings.get.maxUsers)
            throw Exception("too many users")

        if (_users.contains(name))
            throw Exception("user exists")
        if (name.length > Settings.get.maxUsernameLength)
            throw Exception("username too long")
        if (!FMLCommonHandler.instance().minecraftServerInstance.onlinePlayerNames.contains(name))
            throw Exception("player must be online")

        synchronized(_users) {
            _users.add(name)
            usersChanged = true
        }
    }

    override fun removeUser(name: String): Boolean = synchronized(_users) {
        val success = _users.remove(name)
        if (success) {
            usersChanged = true
        }
        success
    }

    // ----------------------------------------------------------------------- //

    @Callback(doc = """function():boolean -- Starts the computer. Returns true if the state changed.""")
    fun start(context: Context, args: Arguments): Array<Any?> =
        result(!isPaused && start())

    @Callback(doc = """function():boolean -- Stops the computer. Returns true if the state changed.""")
    fun stop(context: Context, args: Arguments): Array<Any?> =
        result(stop())

    @Callback(direct = true, doc = """function():boolean -- Returns whether the computer is running.""")
    fun isRunning(context: Context, args: Arguments): Array<Any?> =
        result(isRunning)

    @Callback(doc = """function([frequency:string or number[, duration:number]]) -- Plays a tone, useful to alert users via audible feedback.""")
    fun beep(context: Context, args: Arguments): Array<Any?>? {
        if (args.count() == 1 && args.isString(0)) {
            beep(args.checkString(0))
        } else {
            val frequency = args.optInteger(0, 440)
            if (frequency < 20 || frequency > 2000) {
                throw IllegalArgumentException("invalid frequency, must be in [20, 2000]")
            }
            val duration = args.optDouble(1, 0.1)
            val durationInMilliseconds = max(50, min(5000, (duration * 1000).toInt()))
            context.pause(durationInMilliseconds / 1000.0)
            beep(frequency.toShort(), durationInMilliseconds.toShort())
        }
        return null
    }

    @Callback(doc = """function():table -- Collect information on all connected devices.""")
    fun getDeviceInfo(context: Context, args: Arguments): Array<Any?> {
        context.pause(1.0) // Iterating all nodes is potentially expensive, and I see no practical reason for having to call this frequently.
        return arrayOf(node.network().nodes().mapNotNull { n ->
            val deviceHost = n.host() as? DeviceInfo ?: return@mapNotNull null
            val valid = when (n) {
                is Component -> n.canBeSeenFrom(node) || n == node
                else -> n.canBeReachedFrom(node)
            }
            if (!valid)
                return@mapNotNull null
            n.address() to deviceHost.deviceInfo
        }.toMap())
    }

    @Callback(doc = """function():table -- Returns a map of program name to disk label for known programs.""")
    fun getProgramLocations(context: Context, args: Arguments): Array<Any?> =
        result(ProgramLocations.getMappings(MachineCompanion.getArchitectureName(architecture!!.javaClass)))

    // ----------------------------------------------------------------------- //

    fun isExecuting(): Boolean = synchronized(state) { state.contains(State.Running) }

    override fun canUpdate(): Boolean = true

    override fun update() {
        if (synchronized(state) { state.peek() != State.Stopped }) {
            // Add components that were added since the last update to the actual list
            // of components if we can see them. We use this delayed approach to avoid
            // issues with components that have a visibility lower than their
            // reachability, because in that case if they get connected in the wrong
            // order we wouldn't add them (since they'd be invisible in their connect
            // message, and only become visible with a later node-to-node connection,
            // but that wouldn't trigger a connect message anymore due to the higher
            // reachability).
            processAddedComponents()

            // Component overflow check, crash if too many components are connected, to
            // avoid confusion on the user's side due to components not showing up.
            if (componentCount() > maxComponents) {
                beep("-..")
                crash("gui.Error.ComponentOverflow")
            }

            // Update world time for time() and uptime().
            worldTime = host.world().worldTime
            uptime += 1

            if (remainIdle > 0) {
                remainIdle -= 1
            }

            // Reset direct call budget.
            callBudget = maxCallBudget

            // Make sure we have enough power.
            if (Settings.get.isTickMultiple(host.world)) {
                synchronized(state) {
                    when (state.peek()) {
                        State.Paused, State.Restarting, State.Stopping, State.Stopped -> { } // No power consumption.
                        State.Sleeping -> {
                            if (remainIdle > 0 && signals.isEmpty()) {
                                if (!node.tryChangeBuffer(-cost * Settings.get.sleepCostFactor)) {
                                    crash("gui.Error.NoEnergy")
                                }
                            }
                        }
                        else -> {
                            if (!node.tryChangeBuffer(-cost)) {
                                crash("gui.Error.NoEnergy")
                            }
                        }
                    }
                }
            }

            // Avoid spamming user list across the network.
            if (host.world().totalWorldTime % 20 == 0L && usersChanged) {
                val list = synchronized(_users) {
                    usersChanged = false
                    users()
                }
                if (host is Computer) {
                    PacketSender.sendComputerUserList(host, list)
                }
            }

            // Check if we should switch states. These are all the states in which we're
            // guaranteed that the executor thread isn't running anymore.
            when (synchronized(state) { state.peek() }) {
                // Booting up.
                State.Starting -> {
                    verifyComponents()
                    switchTo(State.Yielded)
                }
                // Computer is rebooting.
                State.Restarting -> {
                    close()
                    if (Settings.get.eraseTmpOnReboot) {
                        tmp?.node()?.remove() // To force deleting contents.
                        tmp?.let { node.connect(it.node()) }
                    }
                    node.sendToReachable("computer.stopped")
                    start()
                }
                // Resume from pauses based on sleep or signal underflow.
                State.Sleeping -> {
                    if (remainIdle <= 0 || signals.isNotEmpty()) {
                        switchTo(State.Yielded)
                    }
                }
                // Resume in case we paused because the game was paused.
                State.Paused -> {
                    if (remainingPause > 0) {
                        remainingPause -= 1
                    } else {
                        verifyComponents() // In case we're resuming after loading.
                        state.pop()
                        switchTo(state.top) // Trigger execution if necessary.
                    }
                }
                // Perform a synchronized call (message sending).
                State.SynchronizedCall -> {
                    // We switch into running state, since we'll behave as though the call
                    // were performed from our executor thread.
                    switchTo(State.Running)
                    try {
                        inSynchronizedCall = true
                        architecture!!.runSynchronized()
                        inSynchronizedCall = false
                        // Check if the callback called pause() or stop().
                        when (state.peek()) {
                            State.Running -> switchTo(State.SynchronizedReturn)
                            State.Paused -> {
                                state.pop() // Paused
                                state.pop() // Running, no switchTo to avoid new future.
                                state.push(State.SynchronizedReturn)
                                state.push(State.Paused)
                            }
                            State.Stopping -> {
                                state.clear()
                                state.push(State.Stopping)
                            }
                            else -> throw AssertionError()
                        }
                    } catch (e: Error) {
                        if (e.message == "not enough memory") {
                            crash("gui.Error.OutOfMemory")
                        } else {
                            throw e
                        }
                    } catch (e: Throwable) {
                        OpenComputers.log.warn("Faulty architecture implementation for synchronized calls.", e)
                        crash("gui.Error.InternalError")
                    } finally {
                        inSynchronizedCall = false
                    }
                }
                else -> { } // Nothing special to do, just avoid match errors.
            }

            // Finally check if we should stop the computer. We cannot lock the state
            // because we may have to wait for the executor thread to finish, which
            // might turn into a deadlock depending on where it currently is.
            when (synchronized(state) { state.peek() }) {
                // Computer is shutting down.
                State.Stopping -> synchronized(this) { synchronized(state) { tryClose() } }
                else -> { }
            }
        }
    }

    // ----------------------------------------------------------------------- //

    override fun onMessage(message: Message) {
        val data = message.data()
        if (data != null && data.isNotEmpty()) {
            if (message.name() == "computer.signal" && data[0] is String) {
                val name = data[0] as String
                val args = data.drop(1).toTypedArray()
                signal(name, message.source().address(), *args)
            } else if (message.name() == "computer.checked_signal" && data.size >= 2 && data[0] is EntityPlayer && data[1] is String) {
                val player = data[0] as EntityPlayer
                val name = data[1] as String
                val args = data.drop(2).toTypedArray()
                if (canInteract(player.name)) {
                    signal(name, message.source().address(), *args)
                }
            } else if (message.name() == "computer.start" && !isPaused) {
                start()
            } else if (message.name() == "computer.stop") {
                stop()
            }
        } else {
            if (message.name() == "computer.start" && !isPaused) start()
            else if (message.name() == "computer.stop") stop()
        }
    }

    override fun onConnect(node: Node) {
        if (node == this.node) {
            _components[this.node.address()!!] = this.node.name()
            tmp?.let { this.node.connect(it.node()) }
            architecture?.onConnect()
        } else {
            if (node is Component) {
                addComponent(node)
            }
        }
        // For computers, to generate the components in their inventory.
        host.onMachineConnect(node)
    }

    override fun onDisconnect(node: Node) {
        if (node == this.node) {
            close()
            tmp?.node()?.remove()
        } else {
            if (node is Component) {
                removeComponent(node)
            }
        }
        // For computers, to save the components in their inventory.
        host.onMachineDisconnect(node)
    }

    // ----------------------------------------------------------------------- //

    fun addComponent(component: Component) {
        if (!_components.containsKey(component.address())) {
            addedComponents.add(component)
        }
    }

    fun removeComponent(component: Component) {
        if (_components.containsKey(component.address())) {
            synchronized(_components) { _components.remove(component.address()) }
            signal("component_removed", component.address(), component.name())
        }
        addedComponents.remove(component)
    }

    private fun processAddedComponents() {
        if (addedComponents.isNotEmpty()) {
            for (component in addedComponents) {
                if (component.canBeSeenFrom(node)) {
                    synchronized(_components) { _components[component.address()] = component.name() }
                    // Skip the signal if we're not initialized yet, since we'd generate a
                    // duplicate in the startup script otherwise.
                    if (architecture != null && architecture!!.isInitialized) {
                        signal("component_added", component.address(), component.name())
                    }
                }
            }
            addedComponents.clear()
        }
    }

    private fun verifyComponents() {
        val invalid = mutableSetOf<String>()
        for ((address, name) in _components) {
            val component = node!!.network().node(address)
            if (component is Component && component.name() == name) {
                // All is well.
            } else {
                if (name == "filesystem") {
                    OpenComputers.log.trace("A component of type '$name' disappeared ($address)! This usually means that it didn't save its node.")
                    OpenComputers.log.trace("If this was a file system provided by a ComputerCraft peripheral, this is normal.")
                } else {
                    OpenComputers.log.warn("A component of type '$name' disappeared ($address)! This usually means that it didn't save its node.")
                }
                signal("component_removed", address, name)
                invalid.add(address)
            }
        }
        for (address in invalid) {
            _components.remove(address)
        }
    }

    // ----------------------------------------------------------------------- //

    private val tmpPath get() = node!!.address() + "_tmp"
    private val StateTag = "state"
    private val UsersTag = "users"
    private val MessageTag = "message"
    private val ComponentsTag = "components"
    private val AddressTag = "address"
    private val NameTag = "name"
    private val TmpTag = "tmp"
    private val SignalsTag = "signals"
    private val ArgsTag = "args"
    private val LengthTag = "length"
    private val ArgPrefixTag = "arg"
    private val UptimeTag = "uptime"
    private val CPUTimeTag = "cpuTime"
    private val RemainingPauseTag = "remainingPause"

    override fun load(nbt: NBTTagCompound) = synchronized(this) { synchronized(state) {
        assert(state.peek() == State.Stopped || state.peek() == State.Paused)
        close()
        state.clear()

        super.load(nbt)

        nbt.getIntArray(StateTag).reversed().forEach { state.push(State.values()[it]) }
        val usersTagList = nbt.getTagList(UsersTag, NBT.TAG_STRING)
        for (i in 0 until usersTagList.tagCount()) {
            _users.add(usersTagList.getStringTagAt(i))
        }
        if (nbt.hasKey(MessageTag)) {
            message = nbt.getString(MessageTag)
        }

        val componentsTagList = nbt.getTagList(ComponentsTag, NBT.TAG_COMPOUND)
        for (i in 0 until componentsTagList.tagCount()) {
            val tag = componentsTagList.getCompoundTagAt(i)
            _components[tag.getString(AddressTag)] = tag.getString(NameTag)
        }

        tmp?.let { fs ->
            if (nbt.hasKey(TmpTag)) fs.load(nbt.getCompoundTag(TmpTag))
            else fs.load(SaveHandler.loadNBT(nbt, tmpPath))
        }

        if (state.isNotEmpty() && isRunning && init()) {
            try {
                architecture!!.load(nbt)

                val signalsTagList = nbt.getTagList(SignalsTag, NBT.TAG_COMPOUND)
                for (i in 0 until signalsTagList.tagCount()) {
                    val signalNbt = signalsTagList.getCompoundTagAt(i)
                    val argsNbt = signalNbt.getCompoundTag(ArgsTag)
                    val argsLength = argsNbt.getInteger(LengthTag)
                    signals.add(Signal(
                        signalNbt.getString(NameTag),
                        (0 until argsLength).map { idx ->
                            when (val tag = argsNbt.getTag(ArgPrefixTag + idx)) {
                                is NBTTagByte -> when (tag.byte.toInt()) {
                                    -1 -> null
                                    1 -> true
                                    else -> false
                                }
                                is NBTTagLong -> tag.long
                                is NBTTagDouble -> tag.double
                                is NBTTagString -> tag.string
                                is NBTTagByteArray -> tag.byteArray
                                is NBTTagList -> {
                                    val data = mutableMapOf<String, String>()
                                    for (j in 0 until tag.tagCount() step 2) {
                                        data[tag.getStringTagAt(j)] = tag.getStringTagAt(j + 1)
                                    }
                                    data
                                }
                                is NBTTagCompound -> tag
                                else -> null
                            }
                        }.toTypedArray()
                    ))
                }

                uptime = nbt.getLong(UptimeTag)
                cpuTotal = nbt.getLong(CPUTimeTag)
                remainingPause = nbt.getInteger(RemainingPauseTag)

                // Delay execution for a second to allow the world around us to settle.
                if (state.peek() != State.Restarting) {
                    pause(Settings.get.startupDelay)
                }
            } catch (t: Throwable) {
                OpenComputers.log.error(
                    "Unexpected error loading a state of computer at ${host.machinePosition()}. " +
                        "State: ${state.peek() ?: "no state"}. Unless you're upgrading/downgrading across a major version, please report this! Thank you.", t)
                close()
            }
        } else {
            // Clean up in case we got a weird state stack.
            onHostChanged()
            close()
        }
    }}

    override fun save(nbt: NBTTagCompound) = synchronized(this) { synchronized(state) {
        // The lock on 'this' should guarantee that this never happens regularly.
        // If something other than regular saving tries to save while we are executing code,
        // e.g. SpongeForge saving during robot.move due to block changes being captured,
        // just don't save this at all. What could possibly go wrong?
        if (isExecuting()) return

        if (SaveHandler.savingForClients) {
            return
        }

        // Make sure we don't continue running until everything has saved.
        pause(0.05)

        super.save(nbt)

        // Make sure the component list is up-to-date.
        processAddedComponents()

        nbt.setIntArray(StateTag, state.map { it.ordinal }.toIntArray())
        nbt.setNewStringList(UsersTag, _users)
        message?.let { nbt.setString(MessageTag, it) }

        val componentsNbt = NBTTagList()
        for ((address, name) in _components) {
            val componentNbt = NBTTagCompound()
            componentNbt.setString(AddressTag, address)
            componentNbt.setString(NameTag, name)
            componentsNbt.appendTag(componentNbt)
        }
        nbt.setTag(ComponentsTag, componentsNbt)

        tmp?.let { fs -> SaveHandler.scheduleSave(host, nbt, tmpPath) { fs.save(it) } }

        if (state.peek() != State.Stopped) {
            try {
                architecture!!.save(nbt)

                val signalsNbt = NBTTagList()
                for (s in signals) {
                    val signalNbt = NBTTagCompound()
                    signalNbt.setString(NameTag, s.name)
                    signalNbt.setNewCompoundTag(ArgsTag) { args ->
                        args.setInteger(LengthTag, s.args.size)
                        s.args.forEachIndexed { i, arg ->
                            when (arg) {
                                null -> args.setByte(ArgPrefixTag + i, -1)
                                is Boolean -> args.setByte(ArgPrefixTag + i, if (arg) 1 else 0)
                                is Long -> args.setLong(ArgPrefixTag + i, arg)
                                is Double -> args.setDouble(ArgPrefixTag + i, arg)
                                is String -> args.setString(ArgPrefixTag + i, arg)
                                is ByteArray -> args.setByteArray(ArgPrefixTag + i, arg)
                                is Map<*, *> -> {
                                    val list = NBTTagList()
                                    for ((key, value) in arg) {
                                        list.appendTag(NBTTagString(key.toString()))
                                        list.appendTag(NBTTagString(value.toString()))
                                    }
                                    args.setTag(ArgPrefixTag + i, list)
                                }
                                is NBTTagCompound -> args.setTag(ArgPrefixTag + i, arg)
                                else -> args.setByte(ArgPrefixTag + i, -1)
                            }
                        }
                    }
                    signalsNbt.appendTag(signalNbt)
                }
                nbt.setTag(SignalsTag, signalsNbt)

                nbt.setLong(UptimeTag, uptime)
                nbt.setLong(CPUTimeTag, cpuTotal)
                nbt.setInteger(RemainingPauseTag, remainingPause)
            } catch (t: Throwable) {
                OpenComputers.log.error(
                    "Unexpected error saving a state of computer at ${host.machinePosition()}. " +
                        "State: ${state.peek() ?: "no state"}. Unless you're upgrading/downgrading across a major version, please report this! Thank you.", t)
            }
        }
    }}

    // ----------------------------------------------------------------------- //

    private fun init(): Boolean {
        onHostChanged()
        if (architecture == null) return false

        // Reset error state.
        message = null

        // Clear any left-over signals from a previous run.
        signals.clear()

        // Connect the `/tmp` node to our owner. We're not in a network in
        // case we're loading, which is why we have to check it here.
        val node = node!!
        if (node.network() != null) {
            tmp?.let { node.connect(it.node()) }
        }

        return try {
            architecture!!.initialize()
        } catch (ex: Throwable) {
            OpenComputers.log.warn("Failed initializing computer.", ex)
            close()
            false
        }
    }

    fun tryClose(): Boolean = if (isExecuting()) false else {
        close()
        tmp?.node()?.remove() // To force deleting contents.
        val node = node!!
        if (node.network() != null) {
            tmp?.let { node.connect(it.node()) }
        }
        node.sendToReachable("computer.stopped")
        true
    }

    private fun close() {
        if (synchronized(state) { state.isEmpty() || state.peek() != State.Stopped }) {
            // Give up the state lock, then get the more generic lock on this instance first
            // before locking on state again. Always must be in that order to avoid deadlocks.
            synchronized(this) {
                synchronized(state) {
                    state.clear()
                    state.push(State.Stopped)
                    architecture?.close()
                    signals.clear()
                    uptime = 0
                    cpuTotal = 0
                    cpuStart = 0
                    remainIdle = 0
                }
            }

            // Mark state change in owner, to send it to clients.
            host.markChanged()
        }
    }

    // ----------------------------------------------------------------------- //

    private fun switchTo(value: State): State = synchronized(state) {
        val result = state.pop()
        if (value == State.Stopping || value == State.Restarting) {
            state.clear()
        }
        state.push(value)
        if (value == State.Yielded || value == State.SynchronizedReturn) {
            remainIdle = 0
            threadPool.schedule(this, Settings.get.executionDelay.toLong(), TimeUnit.MILLISECONDS)
        }

        // Mark state change in owner, to send it to clients.
        host.markChanged()

        result
    }

    private val isGamePaused: Boolean get() {
        val server = FMLCommonHandler.instance().minecraftServerInstance
        return server != null && !server.isDedicatedServer && (server as? IntegratedServer)?.let { Minecraft.getMinecraft().isGamePaused } ?: false
    }

    // This is a really high level lock that we only use for saving and loading.
    override fun run() = synchronized(this) {
        val isSynchronizedReturn = synchronized(state) {
            if (state.peek() != State.Yielded && state.peek() != State.SynchronizedReturn) {
                return
            }
            // See if the game appears to be paused, in which case we also pause.
            if (isGamePaused) {
                state.push(State.Paused)
                return
            }
            switchTo(State.Running) == State.SynchronizedReturn
        }

        cpuStart = System.nanoTime()

        try {
            val result = architecture!!.runThreaded(isSynchronizedReturn)

            // Check if someone called pause() or stop() in the meantime.
            synchronized(state) {
                when (state.peek()) {
                    State.Running -> when (result) {
                        is ExecutionResult.Sleep -> synchronized(signals) {
                            // Immediately check for signals to allow processing more than one
                            // signal per game tick.
                            if (signals.isEmpty() && result.ticks > 0) {
                                switchTo(State.Sleeping)
                                remainIdle = result.ticks
                            } else {
                                switchTo(State.Yielded)
                            }
                        }
                        is ExecutionResult.SynchronizedCall -> switchTo(State.SynchronizedCall)
                        is ExecutionResult.Shutdown -> if (result.reboot) {
                            switchTo(State.Restarting)
                        } else {
                            switchTo(State.Stopping)
                        }
                        is ExecutionResult.Error -> {
                            beep("--")
                            crash(result.message ?: "unknown error")
                        }
                    }
                    State.Paused -> {
                        state.pop() // Paused
                        state.pop() // Running, no switchTo to avoid new future.
                        when (result) {
                            is ExecutionResult.Sleep -> {
                                remainIdle = result.ticks
                                state.push(State.Sleeping)
                            }
                            is ExecutionResult.SynchronizedCall -> state.push(State.SynchronizedCall)
                            is ExecutionResult.Shutdown -> if (result.reboot) {
                                state.push(State.Restarting)
                            } else {
                                state.push(State.Stopping)
                            }
                            is ExecutionResult.Error -> crash(result.message ?: "unknown error")
                        }
                        state.push(State.Paused)
                    }
                    State.Stopping -> {
                        state.clear()
                        state.push(State.Stopping)
                    }
                    State.Restarting -> { } // Nothing to do!
                    else -> throw AssertionError("Invalid state in executor post-processing.")
                }
                assert(!isExecuting())
            }
        } catch (e: Throwable) {
            OpenComputers.log.warn("Architecture's runThreaded threw an error. This should never happen!", e)
            crash("gui.Error.InternalError")
        }

        // Keep track of time spent executing the computer.
        cpuTotal += System.nanoTime() - cpuStart
    }

    /** Possible states of the computer, and in particular its executor. */
    enum class State {
        /** The computer is not running right now and there is no Lua state. */
        Stopped,

        /** Booting up, doing the first run to initialize the kernel and libs. */
        Starting,

        /** Computer is currently rebooting. */
        Restarting,

        /** The computer is currently shutting down. */
        Stopping,

        /** The computer is paused and waiting for the game to resume. */
        Paused,

        /** The computer executor is waiting for a synchronized call to be made. */
        SynchronizedCall,

        /** The computer should resume with the result of a synchronized call. */
        SynchronizedReturn,

        /** The computer will resume as soon as possible. */
        Yielded,

        /** The computer is yielding for a longer amount of time. */
        Sleeping,

        /** The computer is up and running, executing Lua code. */
        Running
    }

    /** Signals are messages sent to the Lua state from Java asynchronously. */
    class Signal(val name: String, val args: Array<out Any?>) : li.cil.oc.api.machine.Signal {
        override fun name() = name
        override fun args() = args
        fun convert() = Signal(name, Registry.run { args.convert() })
    }

    companion object {
        private val threadPool = ThreadPoolFactory.create("Computer", Settings.get.threads)
    }
}

object MachineCompanion : MachineAPI {
    // Keep registration order, to allow deterministic iteration of the architectures.
    val checked: LinkedHashSet<Class<out Architecture>> = LinkedHashSet()

    override fun add(architecture: Class<out Architecture>) {
        if (!checked.contains(architecture)) {
            try {
                architecture.getConstructor(APIMachine::class.java)
            } catch (t: Throwable) {
                throw IllegalArgumentException("Architecture does not have required constructor.", t)
            }
            checked.add(architecture)
        }
    }

    override fun architectures(): MutableList<Class<out Architecture>> = checked.toMutableList()

    override fun getArchitectureName(architecture: Class<out Architecture>): String {
        val annotation = architecture.getAnnotation(Architecture.Name::class.java)
        return annotation?.value ?: architecture.simpleName
    }

    override fun create(host: MachineHost): APIMachine = Machine(host)
}
