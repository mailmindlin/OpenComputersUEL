package li.cil.oc.server.component

import com.google.common.base.Strings
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ComponentConnector
import li.cil.oc.api.network.Environment
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Packet
import li.cil.oc.api.network.SidedEnvironment
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.api.prefab.AbstractValue
import li.cil.oc.server.PacketSender
import li.cil.oc.server.network.DebugNetwork
import li.cil.oc.server.network.DebugNode
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.checkSideAny
import li.cil.oc.util.ExtendedBlock.extendedBlock
import li.cil.oc.util.ExtendedNBT
import li.cil.oc.util.ExtendedWorld.extendedWorld
import li.cil.oc.util.extendedNBT
import li.cil.oc.util.toTypedMap
import li.cil.oc.util.InventoryUtils
import net.minecraft.block.Block
import net.minecraft.command.CommandResultStats
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityMinecart
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.JsonToNBT
import net.minecraft.nbt.NBTBase
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.scoreboard.IScoreCriteria
import net.minecraft.scoreboard.Scoreboard
import net.minecraft.server.management.UserListOpsEntry
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraft.util.SoundCategory
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.text.ITextComponent
import net.minecraft.world.GameType
import net.minecraft.world.World
import net.minecraft.world.WorldServer
import net.minecraftforge.common.DimensionManager
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.event.world.BlockEvent
import net.minecraftforge.fluids.FluidRegistry
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.ModAPIManager

class DebugCard(val host: EnvironmentHost) : AbstractManagedEnvironment(), DebugNode {
    override val node: ComponentConnector = Network.newNode(this, Visibility.Neighbors)
        .withComponent("debug")
        .withConnector()
        .create()

    // Used to detect disconnects.
    private var remoteNode: Node? = null

    // Used for delayed connecting to remote node again after loading.
    private var remoteNodePosition: Triple<Int, Int, Int>? = null

    // Player this card is bound to (if any) to use for permissions.
    var access: AccessContext? = null

    val player: String? get() = access?.player

    private val commandSender: CommandSender by lazy {
        val defaultFakePlayer = FakePlayerFactory.get(
            host.world() as WorldServer,
            Settings.get.fakePlayerProfile
        )
        val actualPlayer = player?.let { name ->
            FMLCommonHandler.instance().minecraftServerInstance.playerList.getPlayerByUsername(name)
        } ?: defaultFakePlayer
        CommandSender(host, actualPlayer)
    }

    // ----------------------------------------------------------------------- //

    private fun checkAccess() {
        val msg = Settings.get.debugCardAccess.checkAccess(access)
        if (msg != null) {
            throw Exception(msg)
        }
    }

    @Callback(doc = """function(value:number):number -- Changes the component network's energy buffer by the specified delta.""")
    fun changeBuffer(context: Context, args: Arguments): Array<Any?> {
        checkAccess()
        return result(node.changeBuffer(args.checkDouble(0)))
    }

    @Callback(doc = """function():number -- Get the container's X position in the world.""")
    fun getX(context: Context, args: Arguments): Array<Any?> {
        checkAccess()
        return result(host.xPosition())
    }

    @Callback(doc = """function():number -- Get the container's Y position in the world.""")
    fun getY(context: Context, args: Arguments): Array<Any?> {
        checkAccess()
        return result(host.yPosition())
    }

    @Callback(doc = """function():number -- Get the container's Z position in the world.""")
    fun getZ(context: Context, args: Arguments): Array<Any?> {
        checkAccess()
        return result(host.zPosition())
    }

    @Callback(doc = """function([id:number]):userdata -- Get the world object for the specified dimension ID, or the container's.""")
    fun getWorld(context: Context, args: Arguments): Array<Any?> {
        checkAccess()
        return if (args.count() > 0) {
            result(WorldValue(DimensionManager.getWorld(args.checkInteger(0)), access))
        } else {
            result(WorldValue(host.world(), access))
        }
    }

    @Callback(doc = """function():table -- Get a list of all world IDs, loaded and unloaded.""")
    fun getWorlds(context: Context, args: Arguments): Array<Any?> {
        checkAccess()
        return result(DimensionManager.getStaticDimensionIDs())
    }

    @Callback(doc = """function(name:string):userdata -- Get the entity of a player.""")
    fun getPlayer(context: Context, args: Arguments): Array<Any?> {
        checkAccess()
        return result(PlayerValue(args.checkString(0), access))
    }

    @Callback(doc = """function():table -- Get a list of currently logged-in players.""")
    fun getPlayers(context: Context, args: Arguments): Array<Any?> {
        checkAccess()
        return result(FMLCommonHandler.instance().minecraftServerInstance.onlinePlayerNames)
    }

    @Callback(doc = """function():userdata -- Get the scoreboard object for the world""")
    fun getScoreboard(context: Context, args: Arguments): Array<Any?> {
        checkAccess()
        return result(ScoreboardValue(host.world(), access))
    }

    @Callback(doc = "function(x: number, y: number, z: number[, worldId: number]):boolean, string, table -- returns contents at the location in world by id (default host world)")
    fun scanContentsAt(context: Context, args: Arguments): Array<Any?> {
        checkAccess()
        val x = args.checkInteger(0)
        val y = args.checkInteger(1)
        val z = args.checkInteger(2)
        val worldServer = if (args.count() > 3) DimensionManager.getWorld(args.checkInteger(3)) else host.world()
        val world = worldServer

        val position = BlockPosition(x, y, z, world)
        val fakePlayer = FakePlayerFactory.get(world as WorldServer, Settings.get.fakePlayerProfile)
        fakePlayer.posX = position.x + 0.5
        fakePlayer.posY = position.y + 0.5
        fakePlayer.posZ = position.z + 0.5

        val entity = world.findNearestEntityWithinAABB(Entity::class.java, position.bounds, fakePlayer)
        return when (entity) {
            is EntityLivingBase -> result(true, "EntityLivingBase", entity)
            is EntityMinecart -> result(true, "EntityMinecart", entity)
            else -> {
                val block = world.extendedWorld().getBlock(position)
                val metadata = world.extendedWorld().getBlockMetadata(position)
                when {
                    block == null || block.extendedBlock().isAir(position) -> result(false, "air", block)
                    FluidRegistry.lookupFluidForBlock(block) != null -> {
                        val event = BlockEvent.BreakEvent(world, position.toBlockPos(), metadata, fakePlayer)
                        MinecraftForge.EVENT_BUS.post(event)
                        result(event.isCanceled, "liquid", block)
                    }
                    block.extendedBlock().isReplaceable(position) -> {
                        val event = BlockEvent.BreakEvent(world, position.toBlockPos(), metadata, fakePlayer)
                        MinecraftForge.EVENT_BUS.post(event)
                        result(event.isCanceled, "replaceable", block)
                    }
                    block.extendedBlock().getCollisionBoundingBoxFromPool(position) == null -> {
                        result(true, "passable", block)
                    }
                    else -> result(true, "solid", block)
                }
            }
        }
    }

    @Callback(doc = """function(name:string):boolean -- Get whether a mod or API is loaded.""")
    fun isModLoaded(context: Context, args: Arguments): Array<Any?> {
        checkAccess()
        val name = args.checkString(0)
        return result(Loader.isModLoaded(name) || ModAPIManager.INSTANCE.hasAPI(name))
    }

    @Callback(doc = """function(command:string):number -- Runs an arbitrary command using a fake player.""")
    fun runCommand(context: Context, args: Arguments): Array<Any?> {
        checkAccess()
        val commands: Iterable<Any?> = if (args.isTable(0)) {
            args.checkTable(0).values
        } else {
            listOf(args.checkString(0))
        }

        synchronized(commandSender) {
            commandSender.prepare()
            var value = 0
            for (command in commands) {
                value = FMLCommonHandler.instance().minecraftServerInstance.commandManager.executeCommand(
                    commandSender,
                    command.toString()
                )
            }
            return result(value, commandSender.messages)
        }
    }

    @Callback(doc = """function(x:number, y:number, z:number):boolean -- Add a component block at the specified coordinates to the computer network.""")
    fun connectToBlock(context: Context, args: Arguments): Array<Any?> {
        checkAccess()
        val x = args.checkInteger(0)
        val y = args.checkInteger(1)
        val z = args.checkInteger(2)
        val other = findNode(BlockPosition(x, y, z))
        return if (other != null) {
            remoteNode?.let { node.disconnect(it) }
            remoteNode = other
            remoteNodePosition = Triple(x, y, z)
            node.connect(other)
            result(true)
        } else {
            result(Unit, "no node found at this position")
        }
    }

    private fun findNode(position: BlockPosition): Node? {
        if (!host.world().extendedWorld().blockExists(position)) return null
        return when (val te = host.world().getTileEntity(position.toBlockPos())) {
            is SidedEnvironment -> EnumFacing.values().mapNotNull { te.sidedNode(it) }.firstOrNull()
            is Environment -> te.node()
            else -> null
        }
    }

    @Callback(doc = """function():userdata -- Test method for user-data and general value conversion.""")
    fun test(context: Context, args: Arguments): Array<Any?> {
        checkAccess()

        val v1 = mutableMapOf<Any, Any>("a" to true, "b" to "test")
        val v2 = mapOf<Any, Any>(10 to "zxc", false to v1)
        v1["c"] = v2

        return result(v2, TestValue(), host.world())
    }

    // ----------------------------------------------------------------------- //

    @Callback(doc = """function(player:string, text:string) -- Sends text to the specified player's clipboard if possible.""")
    fun sendToClipboard(context: Context, args: Arguments): Array<Any?> {
        checkAccess()
        val player = FMLCommonHandler.instance().minecraftServerInstance.playerList.getPlayerByUsername(args.checkString(0))
        return if (player != null) {
            PacketSender.sendClipboard(player, args.checkString(1))
            result(true)
        } else {
            result(false, "no such player")
        }
    }

    @Callback(doc = """function(address:string, data...) -- Sends data to the debug card with the specified address.""")
    fun sendToDebugCard(context: Context, args: Arguments): Array<Any?> {
        checkAccess()
        val destination = args.checkString(0)
        DebugNetwork.getEndpoint(destination)
            ?.takeIf { it != this@DebugCard }
            ?.let { endpoint ->
                val packet = Network.newPacket(node.address(), destination, 0, args.drop(1).toTypedArray())
                endpoint.receivePacket(packet)
            }
        return result()
    }

    override fun receivePacket(packet: Packet) {
        val distance = 0
        node.sendToReachable(
            "computer.signal",
            "debug_message",
            packet.source(),
            packet.port(),
            distance.toDouble(),
            *packet.data()
        )
    }

    override fun address(): String = node?.address() ?: "debug"

    // ----------------------------------------------------------------------- //

    override fun onConnect(node: Node) {
        super.onConnect(node)
        if (node == this.node) {
            DebugNetwork.add(this)
            remoteNodePosition?.let { (x, y, z) ->
                remoteNode = findNode(BlockPosition(x, y, z))
                if (remoteNode != null) {
                    this.node.connect(remoteNode)
                } else {
                    remoteNodePosition = null
                }
            }
        }
    }

    override fun onDisconnect(node: Node) {
        super.onDisconnect(node)
        if (node == this.node) {
            DebugNetwork.remove(this)
            remoteNode?.disconnect(node)
        } else if (remoteNode == node) {
            remoteNode = null
            remoteNodePosition = null
        }
    }

    // ----------------------------------------------------------------------- //

    override fun load(nbt: NBTTagCompound) {
        super.load(nbt)
        access = AccessContext.load(nbt)
        if (nbt.hasKey(Settings.namespace + "remoteX")) {
            val x = nbt.getInteger(Settings.namespace + "remoteX")
            val y = nbt.getInteger(Settings.namespace + "remoteY")
            val z = nbt.getInteger(Settings.namespace + "remoteZ")
            remoteNodePosition = Triple(x, y, z)
        }
    }

    override fun save(nbt: NBTTagCompound) {
        super.save(nbt)
        access?.save(nbt)
        remoteNodePosition?.let { (x, y, z) ->
            nbt.setInteger(Settings.namespace + "remoteX", x)
            nbt.setInteger(Settings.namespace + "remoteY", y)
            nbt.setInteger(Settings.namespace + "remoteZ", z)
        }
    }

    // ----------------------------------------------------------------------- //
    // Nested classes
    // ----------------------------------------------------------------------- //

    data class AccessContext(val player: String, val nonce: String) {
        fun save(nbt: NBTTagCompound) {
            nbt.setString(Settings.namespace + "player", player)
            nbt.setString(Settings.namespace + "accessNonce", nonce)
        }

        companion object {
            fun remove(nbt: NBTTagCompound) {
                nbt.removeTag(Settings.namespace + "player")
                nbt.removeTag(Settings.namespace + "accessNonce")
            }

            fun load(nbt: NBTTagCompound): AccessContext? {
                return if (nbt.hasKey(Settings.namespace + "player")) {
                    AccessContext(
                        nbt.getString(Settings.namespace + "player"),
                        nbt.getString(Settings.namespace + "accessNonce")
                    )
                } else {
                    null
                }
            }
        }
    }

    class PlayerValue : AbstractValue {
        private var name: String = ""
        private var ctx: AccessContext? = null

        constructor() // For loading

        constructor(name: String, ctx: AccessContext?) {
            this.name = name
            this.ctx = ctx
        }

        private fun checkAccess() {
            val msg = Settings.get.debugCardAccess.checkAccess(ctx)
            if (msg != null) {
                throw Exception(msg)
            }
        }

        private fun <T> withPlayer(f: (EntityPlayerMP) -> T): T {
            checkAccess()
            val player = FMLCommonHandler.instance().minecraftServerInstance.playerList.getPlayerByUsername(name)
            return if (player is EntityPlayerMP) {
                f(player)
            } else {
                throw Exception("player is offline")
            }
        }

        @Callback(doc = """function():userdata -- Get the player's world object.""")
        fun getWorld(context: Context, args: Arguments): Array<Any?> =
            withPlayer { player -> result(WorldValue(player.entityWorld, ctx)) }

        @Callback(doc = """function():string -- Get the player's game type.""")
        fun getGameType(context: Context, args: Arguments): Array<Any?> =
            withPlayer { player -> result(player.interactionManager.gameType.name) }

        @Callback(doc = """function(gametype:string) -- Set the player's game type (survival, creative, adventure).""")
        fun setGameType(context: Context, args: Arguments): Array<Any?> =
            withPlayer { player ->
                val gametype = args.checkString(0)
                player.setGameType(GameType.values().find { it.name == gametype } ?: GameType.SURVIVAL)
                null
            }

        @Callback(doc = """function():number, number, number -- Get the player's position.""")
        fun getPosition(context: Context, args: Arguments): Array<Any?> =
            withPlayer { player -> result(player.posX, player.posY, player.posZ) }

        @Callback(doc = """function(x:number, y:number, z:number) -- Set the player's position.""")
        fun setPosition(context: Context, args: Arguments): Array<Any?> =
            withPlayer { player ->
                player.setPositionAndUpdate(args.checkDouble(0), args.checkDouble(1), args.checkDouble(2))
                null
            }

        @Callback(doc = """function():number -- Get the player's health.""")
        fun getHealth(context: Context, args: Arguments): Array<Any?> =
            withPlayer { player -> result(player.health) }

        @Callback(doc = """function():number -- Get the player's max health.""")
        fun getMaxHealth(context: Context, args: Arguments): Array<Any?> =
            withPlayer { player -> result(player.maxHealth) }

        @Callback(doc = """function(health:number) -- Set the player's health.""")
        fun setHealth(context: Context, args: Arguments): Array<Any?> =
            withPlayer { player ->
                player.health = args.checkDouble(0).toFloat()
                null
            }

        @Callback(doc = """function():number -- Get the player's level""")
        fun getLevel(context: Context, args: Arguments): Array<Any?> =
            withPlayer { player -> result(player.experienceLevel) }

        @Callback(doc = """function():number -- Get the player's total experience""")
        fun getExperienceTotal(context: Context, args: Arguments): Array<Any?> =
            withPlayer { player -> result(player.experienceTotal) }

        @Callback(doc = """function(level:number) -- Add a level to the player's experience level""")
        fun addExperienceLevel(context: Context, args: Arguments): Array<Any?> =
            withPlayer { player ->
                player.addExperienceLevel(args.checkInteger(0))
                null
            }

        @Callback(doc = """function(level:number) -- Remove a level from the player's experience level""")
        fun removeExperienceLevel(context: Context, args: Arguments): Array<Any?> =
            withPlayer { player ->
                player.addExperienceLevel(-args.checkInteger(0))
                null
            }

        @Callback(doc = """function() -- Clear the players inventory""")
        fun clearInventory(context: Context, args: Arguments): Array<Any?> =
            withPlayer { player ->
                player.inventory.clear()
                null
            }

        @Callback(doc = """function(id:string, amount:number, meta:number[, nbt:string]):number -- Adds the item stack to the players inventory""")
        fun insertItem(context: Context, args: Arguments): Array<Any?> =
            withPlayer { player ->
                val item = Item.REGISTRY.getObject(ResourceLocation(args.checkString(0)))
                    ?: throw IllegalArgumentException("invalid item id")
                val amount = args.checkInteger(1)
                val meta = args.checkInteger(2)
                val tagJson = args.checkString(3)
                val tag = if (Strings.isNullOrEmpty(tagJson)) null else JsonToNBT.getTagFromJson(tagJson)
                val stack = ItemStack(item, amount, meta)
                stack.tagCompound = tag
                result(InventoryUtils.addToPlayerInventory(stack, player))
            }

        // ----------------------------------------------------------------------- //

        private companion object {
            const val NameTag = "name"
        }

        override fun load(nbt: NBTTagCompound) {
            super.load(nbt)
            ctx = AccessContext.load(nbt)
            name = nbt.getString(NameTag)
        }

        override fun save(nbt: NBTTagCompound) {
            super.save(nbt)
            ctx?.save(nbt)
            nbt.setString(NameTag, name)
        }
    }

    class ScoreboardValue : AbstractValue {
        private var scoreboard: Scoreboard? = null
        private var dimension: Int = 0
        private var ctx: AccessContext? = null

        constructor() // For loading

        constructor(world: World?, ctx: AccessContext?) {
            this.scoreboard = world?.scoreboard
            this.dimension = world?.provider?.dimension ?: 0
            this.ctx = ctx
        }

        private fun checkAccess() {
            val msg = Settings.get.debugCardAccess.checkAccess(ctx)
            if (msg != null) {
                throw Exception(msg)
            }
        }

        @Callback(doc = """function(team:string) - Add a team to the scoreboard""")
        fun addTeam(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val team = args.checkString(0)
            scoreboard?.createTeam(team)
            return result()
        }

        @Callback(doc = """function(teamName: string) - Remove a team from the scoreboard""")
        fun removeTeam(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val teamName = args.checkString(0)
            val team = scoreboard?.getTeam(teamName)
            if (team != null) scoreboard?.removeTeam(team)
            return result()
        }

        @Callback(doc = """function(player:string, team:string):boolean - Add a player to a team""")
        fun addPlayerToTeam(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val player = args.checkString(0)
            val team = args.checkString(1)
            return result(scoreboard?.addPlayerToTeam(player, team) ?: false)
        }

        @Callback(doc = """function(player:string):boolean - Remove a player from their team""")
        fun removePlayerFromTeams(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val player = args.checkString(0)
            return result(scoreboard?.removePlayerFromTeams(player) ?: false)
        }

        @Callback(doc = """function(player:string, team:string):boolean - Remove a player from a specific team""")
        fun removePlayerFromTeam(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val player = args.checkString(0)
            val teamName = args.checkString(1)
            val team = scoreboard?.getTeam(teamName)
            if (team != null) scoreboard?.removePlayerFromTeam(player, team)
            return result()
        }

        @Callback(doc = """function(objectiveName:string, objectiveCriteria:string) - Create a new objective for the scoreboard""")
        fun addObjective(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val objName = args.checkString(0)
            val objType = args.checkString(1)
            val criteria = IScoreCriteria.INSTANCES[objType]
            scoreboard?.addScoreObjective(objName, criteria)
            return result()
        }

        @Callback(doc = """function(objectiveName:string) - Remove an objective from the scoreboard""")
        fun removeObjective(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val objName = args.checkString(0)
            val objective = scoreboard?.getObjective(objName)
            if (objective != null) scoreboard?.removeObjective(objective)
            return result()
        }

        @Callback(doc = """function(playerName:string, objectiveName:string, score:int) - Sets the score of a player for a certain objective""")
        fun setPlayerScore(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val name = args.checkString(0)
            val objective = scoreboard?.getObjective(args.checkString(1))
            val scoreVal = args.checkInteger(2)
            if (objective != null) {
                val score = scoreboard?.getOrCreateScore(name, objective)
                score?.scorePoints = scoreVal
            }
            return result()
        }

        @Callback(doc = """function(playerName:string, objectiveName:string):int - Gets the score of a player for a certain objective""")
        fun getPlayerScore(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val name = args.checkString(0)
            val objective = scoreboard?.getObjective(args.checkString(1))
            return if (objective != null) {
                val score = scoreboard?.getOrCreateScore(name, objective)
                result(score?.scorePoints ?: 0)
            } else {
                result(0)
            }
        }

        @Callback(doc = """function(playerName:string, objectiveName:string, score:int) - Increases the score of a player for a certain objective""")
        fun increasePlayerScore(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val name = args.checkString(0)
            val objective = scoreboard?.getObjective(args.checkString(1))
            val scoreVal = args.checkInteger(2)
            if (objective != null) {
                val score = scoreboard?.getOrCreateScore(name, objective)
                score?.increaseScore(scoreVal)
            }
            return result()
        }

        @Callback(doc = """function(playerName:string, objectiveName:string, score:int) - Decrease the score of a player for a certain objective""")
        fun decreasePlayerScore(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val name = args.checkString(0)
            val objective = scoreboard?.getObjective(args.checkString(1))
            val scoreVal = args.checkInteger(2)
            if (objective != null) {
                val score = scoreboard?.getOrCreateScore(name, objective)
                score?.decreaseScore(scoreVal)
            }
            return result()
        }

        // ----------------------------------------------------------------------- //

        private companion object {
            const val DimensionTag = "dimension"
        }

        override fun load(nbt: NBTTagCompound) {
            super.load(nbt)
            ctx = AccessContext.load(nbt)
            dimension = nbt.getInteger(DimensionTag)
            scoreboard = DimensionManager.getWorld(dimension)?.scoreboard
        }

        override fun save(nbt: NBTTagCompound) {
            super.save(nbt)
            ctx?.save(nbt)
            nbt.setInteger(DimensionTag, dimension)
        }
    }

    class WorldValue : AbstractValue {
        private var world: World? = null
        private var ctx: AccessContext? = null

        constructor() // For loading

        constructor(world: World?, ctx: AccessContext?) {
            this.world = world
            this.ctx = ctx
        }

        private fun checkAccess() {
            val msg = Settings.get.debugCardAccess.checkAccess(ctx)
            if (msg != null) {
                throw Exception(msg)
            }
        }

        // ----------------------------------------------------------------------- //

        @Callback(doc = """function():number -- Gets the numeric id of the current dimension.""")
        fun getDimensionId(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            return result(world?.provider?.dimension ?: 0)
        }

        @Callback(doc = """function():string -- Gets the name of the current dimension.""")
        fun getDimensionName(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            return result(world?.provider?.dimensionType?.name ?: "")
        }

        @Callback(doc = """function():number -- Gets the seed of the world.""")
        fun getSeed(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            return result(world?.seed ?: 0L)
        }

        @Callback(doc = """function():boolean -- Returns whether it is currently raining.""")
        fun isRaining(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            return result(world?.isRaining ?: false)
        }

        @Callback(doc = """function(value:boolean) -- Sets whether it is currently raining.""")
        fun setRaining(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            world?.worldInfo?.isRaining = args.checkBoolean(0)
            return result()
        }

        @Callback(doc = """function():boolean -- Returns whether it is currently thundering.""")
        fun isThundering(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            return result(world?.isThundering ?: false)
        }

        @Callback(doc = """function(value:boolean) -- Sets whether it is currently thundering.""")
        fun setThundering(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            world?.worldInfo?.isThundering = args.checkBoolean(0)
            return result()
        }

        @Callback(doc = """function():number -- Get the current world time.""")
        fun getTime(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            return result(world?.worldTime ?: 0L)
        }

        @Callback(doc = """function(value:number) -- Set the current world time.""")
        fun setTime(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            world?.worldTime = args.checkDouble(0).toLong()
            return result()
        }

        @Callback(doc = """function():number, number, number -- Get the current spawn point coordinates.""")
        fun getSpawnPoint(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val info = world?.worldInfo
            return result(info?.spawnX ?: 0, info?.spawnY ?: 0, info?.spawnZ ?: 0)
        }

        @Callback(doc = """function(x:number, y:number, z:number) -- Set the spawn point coordinates.""")
        fun setSpawnPoint(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            world?.worldInfo?.setSpawn(BlockPos(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2)))
            return result()
        }

        @Callback(doc = """function(x:number, y:number, z:number, sound:string, range:number) -- Play a sound at the specified coordinates.""")
        fun playSoundAt(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val (x, y, z) = Triple(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))
            val sound = args.checkString(3)
            val range = args.checkInteger(4)
            world?.let { PacketSender.sendSound(it, x, y, z, ResourceLocation(sound), SoundCategory.MASTER, range) }
            return result()
        }

        // ----------------------------------------------------------------------- //

        @Callback(doc = """function(x:number, y:number, z:number):number -- Get the ID of the block at the specified coordinates.""")
        fun getBlockId(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val pos = BlockPos(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))
            return result(Block.getIdFromBlock(world?.getBlockState(pos)?.block))
        }

        @Callback(doc = """function(x:number, y:number, z:number):number -- Get the metadata of the block at the specified coordinates.""")
        fun getMetadata(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val pos = BlockPos(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))
            val state = world?.getBlockState(pos)
            return result(state?.block?.getMetaFromState(state) ?: 0)
        }

        @Callback(doc = """function(x:number, y:number, z:number[, actualState:boolean=false]) - gets the block state for the block at the specified position, optionally getting additional display related data""")
        fun getBlockState(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val pos = BlockPos(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))
            var state = world?.getBlockState(pos)
            if (args.optBoolean(3, false) && state != null && world != null) {
                state = state.getActualState(world, pos)
            }
            return result(state)
        }

        @Callback(doc = """function(x:number, y:number, z:number):number -- Check whether the block at the specified coordinates is loaded.""")
        fun isLoaded(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            return result(world?.isBlockLoaded(BlockPos(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))) ?: false)
        }

        @Callback(doc = """function(x:number, y:number, z:number):number -- Check whether the block at the specified coordinates has a tile entity.""")
        fun hasTileEntity(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val blockPos = BlockPos(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))
            val state = world?.getBlockState(blockPos)
            return result(state?.block?.hasTileEntity(state) ?: false)
        }

        @Callback(doc = """function(x:number, y:number, z:number):table -- Get the NBT of the block at the specified coordinates.""")
        fun getTileNBT(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val blockPos = BlockPos(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))
            val tileEntity = world?.getTileEntity(blockPos)
            return if (tileEntity != null) {
                val nbt = NBTTagCompound()
                tileEntity.writeToNBT(nbt)
                result(nbt.extendedNBT().toTypedMap())
            } else {
                result()
            }
        }

        @Callback(doc = """function(x:number, y:number, z:number, nbt:table):boolean -- Set the NBT of the block at the specified coordinates.""")
        fun setTileNBT(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val blockPos = BlockPos(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))
            val tileEntity = world?.getTileEntity(blockPos)
            return if (tileEntity != null) {
                val tableData = args.checkTable(3)
                val nbt = li.cil.oc.util.ExtendedNBT.typedMapToNbt(tableData.toMap())
                if (nbt is NBTTagCompound) {
                    tileEntity.readFromNBT(nbt)
                    tileEntity.markDirty()
                    world?.extendedWorld()?.notifyBlockUpdate(blockPos)
                    result(true)
                } else {
                    result(Unit, "nbt tag compound expected, got '${NBTBase.NBT_TYPES[nbt.id.toInt()]}'")
                }
            } else {
                result(Unit, "no tile entity")
            }
        }

        @Callback(doc = """function(x:number, y:number, z:number):number -- Get the light opacity of the block at the specified coordinates.""")
        fun getLightOpacity(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            return result(world?.getBlockLightOpacity(BlockPos(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))) ?: 0)
        }

        @Callback(doc = """function(x:number, y:number, z:number):number -- Get the light value (emission) of the block at the specified coordinates.""")
        fun getLightValue(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            return result(world?.getLight(BlockPos(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2)), false) ?: 0)
        }

        @Callback(doc = """function(x:number, y:number, z:number):number -- Get whether the block at the specified coordinates is directly under the sky.""")
        fun canSeeSky(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            return result(world?.canBlockSeeSky(BlockPos(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))) ?: false)
        }

        @Callback(doc = """function(x:number, y:number, z:number, id:number or string, meta:number):number -- Set the block at the specified coordinates.""")
        fun setBlock(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val block = if (args.isInteger(3)) Block.getBlockById(args.checkInteger(3)) else Block.getBlockFromName(args.checkString(3))
            val metadata = args.checkInteger(4)
            val pos = BlockPos(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))
            return result(world?.setBlockState(pos, block.getStateFromMeta(metadata)) ?: false)
        }

        @Callback(doc = """function(x1:number, y1:number, z1:number, x2:number, y2:number, z2:number, id:number or string, meta:number):number -- Set all blocks in the area defined by the two corner points (x1, y1, z1) and (x2, y2, z2).""")
        fun setBlocks(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val (xMin, yMin, zMin) = Triple(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2))
            val (xMax, yMax, zMax) = Triple(args.checkInteger(3), args.checkInteger(4), args.checkInteger(5))
            val block = if (args.isInteger(6)) Block.getBlockById(args.checkInteger(6)) else Block.getBlockFromName(args.checkString(6))
            val metadata = args.checkInteger(7)
            for (x in minOf(xMin, xMax)..maxOf(xMin, xMax)) {
                for (y in minOf(yMin, yMax)..maxOf(yMin, yMax)) {
                    for (z in minOf(zMin, zMax)..maxOf(zMin, zMax)) {
                        world?.setBlockState(BlockPos(x, y, z), block.getStateFromMeta(metadata))
                    }
                }
            }
            return result()
        }

        // ----------------------------------------------------------------------- //

        @Callback(doc = """function(id:string, count:number, damage:number, nbt:string, x:number, y:number, z:number, side:number):boolean - Insert an item stack into the inventory at the specified location. NBT tag is expected in JSON format.""")
        fun insertItem(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val item = Item.REGISTRY.getObject(ResourceLocation(args.checkString(0)))
                ?: throw IllegalArgumentException("invalid item id")
            val count = args.checkInteger(1)
            val damage = args.checkInteger(2)
            val tagJson = args.optString(3, "")
            val tag = if (Strings.isNullOrEmpty(tagJson)) null else JsonToNBT.getTagFromJson(tagJson)
            val position = BlockPosition(args.checkDouble(4), args.checkDouble(5), args.checkDouble(6), world)
            val side = args.checkSideAny(7)
            val inventory = InventoryUtils.inventoryAt(position, side)
            return if (inventory != null) {
                val stack = ItemStack(item, count, damage)
                stack.tagCompound = tag
                result(InventoryUtils.insertIntoInventory(stack, inventory))
            } else {
                result(Unit, "no inventory")
            }
        }

        @Callback(doc = """function(x:number, y:number, z:number, slot:number[, count:number]):number - Reduce the size of an item stack in the inventory at the specified location.""")
        fun removeItem(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val position = BlockPosition(args.checkDouble(0), args.checkDouble(1), args.checkDouble(2), world)
            val inventory = InventoryUtils.anyInventoryAt(position)
            return if (inventory != null) {
                val slot = args.checkInteger(3) - 1
                if (slot < 0 || slot >= inventory.slots) {
                    throw IllegalArgumentException("invalid slot")
                }
                val count = args.optInteger(4, 64)
                val removed = inventory.extractItem(slot, count, false)
                if (removed.isEmpty) result(0) else result(removed.count)
            } else {
                result(Unit, "no inventory")
            }
        }

        @Callback(doc = """function(id:string, amount:number, x:number, y:number, z:number, side:number):boolean - Insert some fluid into the tank at the specified location.""")
        fun insertFluid(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val fluid = FluidRegistry.getFluid(args.checkString(0))
                ?: throw IllegalArgumentException("invalid fluid id")
            val amount = args.checkInteger(1)
            val position = BlockPosition(args.checkDouble(2), args.checkDouble(3), args.checkDouble(4), world)
            val handler = world?.getTileEntity(position.toBlockPos())
            return if (handler is IFluidHandler) {
                result(handler.fill(FluidStack(fluid, amount), true))
            } else {
                result(Unit, "no tank")
            }
        }

        @Callback(doc = """function(amount:number, x:number, y:number, z:number, side:number):boolean - Remove some fluid from a tank at the specified location.""")
        fun removeFluid(context: Context, args: Arguments): Array<Any?> {
            checkAccess()
            val amount = args.checkInteger(0)
            val position = BlockPosition(args.checkDouble(1), args.checkDouble(2), args.checkDouble(3), world)
            val handler = world?.getTileEntity(position.toBlockPos())
            return if (handler is IFluidHandler) {
                result(handler.drain(amount, true))
            } else {
                result(Unit, "no tank")
            }
        }

        // ----------------------------------------------------------------------- //

        private companion object {
            const val DimensionTag = "dimension"
        }

        override fun load(nbt: NBTTagCompound) {
            super.load(nbt)
            ctx = AccessContext.load(nbt)
            world = DimensionManager.getWorld(nbt.getInteger(DimensionTag))
        }

        override fun save(nbt: NBTTagCompound) {
            super.save(nbt)
            ctx?.save(nbt)
            nbt.setInteger(DimensionTag, world?.provider?.dimension ?: 0)
        }
    }

    class CommandSender(
        private val host: EnvironmentHost,
        private val underlying: EntityPlayerMP
    ) : FakePlayer(underlying.entityWorld as WorldServer, underlying.gameProfile) {

        var messages: String? = null

        fun prepare() {
            val blockPos = BlockPosition(host)
            posX = blockPos.x.toDouble()
            posY = blockPos.y.toDouble()
            posZ = blockPos.z.toDouble()
            messages = null
        }

        override fun getName(): String = underlying.name

        override fun getEntityWorld(): World = host.world()

        override fun sendMessage(message: ITextComponent) {
            messages = (messages?.let { "$it\n" } ?: "") + message.unformattedText
        }

        override fun getDisplayName(): ITextComponent = underlying.displayName

        override fun setCommandStat(type: CommandResultStats.Type, amount: Int) =
            underlying.setCommandStat(type, amount)

        override fun getPosition(): BlockPos = underlying.position

        override fun canUseCommand(level: Int, commandName: String): Boolean {
            val profile = underlying.gameProfile
            val server = underlying.server
            val config = server.playerList
            return server.isSinglePlayer || (config.canSendCommands(profile) && run {
                val entry = config.oppedPlayers.getEntry(profile)
                if (entry is UserListOpsEntry) {
                    entry.permissionLevel >= level
                } else {
                    server.opPermissionLevel >= level
                }
            })
        }

        override fun getCommandSenderEntity(): EntityPlayerMP = underlying

        override fun getPositionVector(): Vec3d = underlying.positionVector

        override fun sendCommandFeedback(): Boolean = underlying.sendCommandFeedback()
    }

    class TestValue : AbstractValue() {
        private var value: String = "hello"

        override fun apply(context: Context, arguments: Arguments): Any {
            OpenComputers.log.info("TestValue.apply(${arguments.toArray().joinToString(", ")})")
            return value
        }

        override fun unapply(context: Context, arguments: Arguments) {
            OpenComputers.log.info("TestValue.unapply(${arguments.toArray().joinToString(", ")})")
            value = arguments.checkString(1)
        }

        override fun call(context: Context, arguments: Arguments): Array<Any?> {
            OpenComputers.log.info("TestValue.call(${arguments.toArray().joinToString(", ")})")
            return result(*arguments.toArray())
        }

        override fun dispose(context: Context) {
            super.dispose(context)
            OpenComputers.log.info("TestValue.dispose()")
        }

        private companion object {
            const val ValueTag = "value"
        }

        override fun load(nbt: NBTTagCompound) {
            super.load(nbt)
            value = nbt.getString(ValueTag)
        }

        override fun save(nbt: NBTTagCompound) {
            super.save(nbt)
            nbt.setString(ValueTag, value)
        }
    }
}
