package li.cil.oc.common.nanomachines

import com.google.common.base.Charsets
import com.google.common.base.Strings
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.*
import li.cil.oc.api.nanomachines.Behavior
import li.cil.oc.api.nanomachines.Controller
import li.cil.oc.api.nanomachines.DisableReason
import li.cil.oc.api.network.Packet
import li.cil.oc.api.network.WirelessEndpoint
import li.cil.oc.common.item.data.NanomachineData
import li.cil.oc.common.Tier
import li.cil.oc.integration.util.DamageSourceWithRandomCause
import li.cil.oc.server.PacketSender
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.PlayerUtils
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.potion.Potion
import net.minecraft.potion.PotionEffect
import net.minecraft.util.EnumParticleTypes
import net.minecraft.world.World
import java.util.UUID

class ControllerImpl(val player: EntityPlayer) : Controller, WirelessEndpoint {
    init {
        if (isServer) Network.joinWirelessNetwork(this)
    }

    var previousDimension: Int = player.world.provider.dimension

    val CommandRange: Double by lazy { Settings.get.nanomachinesCommandRange * Settings.get.nanomachinesCommandRange }

    companion object {
        const val FullSyncInterval = 20 * 60
    }

    val OverloadDamage = DamageSourceWithRandomCause("oc.nanomachinesOverload", 3)
        .setDamageBypassesArmor()
        .setDamageIsAbsolute()

    var uuid: String = UUID.randomUUID().toString()
    var responsePort: Int = 0
    var commandDelay: Int = 0
    var queuedCommand: (() -> Unit)? = null
    var storedEnergy: Double = Settings.get.bufferNanomachines * 0.25
    var hadPower: Boolean = true
    val configuration = NeuralNetwork(this)
    val activeBehaviors: MutableSet<Behavior> = mutableSetOf()
    var activeBehaviorsDirty: Boolean = true
    var hasSentConfiguration: Boolean = false

    override fun world(): World = player.entityWorld

    override fun x(): Int = BlockPosition(player).x

    override fun y(): Int = BlockPosition(player).y

    override fun z(): Int = BlockPosition(player).z

    override fun receivePacket(packet: Packet, sender: WirelessEndpoint) {
        if (localBuffer > 0 && commandDelay < 1 && !player.isDead) {
            val dx = (sender.x() + 0.5) - player.posX
            val dy = (sender.y() + 0.5) - player.posY
            val dz = (sender.z() + 0.5) - player.posZ
            val dSquared = Math.sqrt(dx * dx + dy * dy + dz * dz)
            if (dSquared <= CommandRange) {
                val header = packet.data().firstOrNull()
                if (header is ByteArray && String(header, Charsets.UTF_8) == "nanomachines") {
                    val command = packet.data().drop(1).map { value ->
                        when (value) {
                            is ByteArray -> String(value, Charsets.UTF_8)
                            else -> value
                        }
                    }.toTypedArray()

                    when {
                        command.size == 2 && command[0] == "setResponsePort" && command[1] is Number -> {
                            responsePort = (command[1] as Number).toInt().coerceIn(0, 0xFFFF)
                            respond(sender, "port", responsePort)
                        }
                        command.size == 1 && command[0] == "getPowerState" -> {
                            respond(sender, "power", localBuffer, localBufferSize)
                        }
                        command.size == 1 && command[0] == "saveConfiguration" -> {
                            val nanomachines = Items.get(Constants.ItemName.Nanomachines)
                            try {
                                val index = player.inventory.mainInventory.indexOfFirst { stack ->
                                    Items.get(stack) == nanomachines && NanomachineData(stack).configuration.isEmpty()
                                }
                                if (index >= 0) {
                                    val stack = player.inventory.decrStackSize(index, 1)
                                    NanomachineData(this).save(stack)
                                    player.inventory.addItemStackToInventory(stack)
                                    InventoryUtils.spawnStackInWorld(BlockPosition(player), stack)
                                    respond(sender, "saved", true)
                                } else {
                                    respond(sender, "saved", false, "no nanomachines")
                                }
                            } catch (e: Throwable) {
                                respond(sender, "saved", false, "error")
                            }
                        }
                        command.size == 1 && command[0] == "getHealth" -> {
                            respond(sender, "health", player.health, player.maxHealth)
                        }
                        command.size == 1 && command[0] == "getHunger" -> {
                            respond(sender, "hunger", player.foodStats.foodLevel, player.foodStats.saturationLevel)
                        }
                        command.size == 1 && command[0] == "getAge" -> {
                            respond(sender, "age", (player.idleTime / 20f).toInt())
                        }
                        command.size == 1 && command[0] == "getName" -> {
                            respond(sender, "name", player.displayName.unformattedComponentText)
                        }
                        command.size == 1 && command[0] == "getExperience" -> {
                            respond(sender, "experience", player.experienceLevel)
                        }
                        command.size == 1 && command[0] == "getTotalInputCount" -> {
                            respond(sender, "totalInputCount", totalInputCount)
                        }
                        command.size == 1 && command[0] == "getSafeActiveInputs" -> {
                            respond(sender, "safeActiveInputs", safeActiveInputs)
                        }
                        command.size == 1 && command[0] == "getMaxActiveInputs" -> {
                            respond(sender, "maxActiveInputs", maxActiveInputs)
                        }
                        command.size == 2 && command[0] == "getInput" && command[1] is Number -> {
                            try {
                                val trigger = getInput((command[1] as Number).toInt() - 1)
                                respond(sender, "input", (command[1] as Number).toInt(), trigger)
                            } catch (e: Throwable) {
                                respond(sender, "input", "error")
                            }
                        }
                        command.size == 3 && command[0] == "setInput" && command[1] is Number && command[2] is Boolean -> {
                            try {
                                val index = (command[1] as Number).toInt()
                                if (setInput(index - 1, command[2] as Boolean)) {
                                    respond(sender, "input", index, getInput(index - 1))
                                } else {
                                    respond(sender, "input", "too many active inputs")
                                }
                            } catch (e: Throwable) {
                                respond(sender, "input", "error")
                            }
                        }
                        command.size == 1 && command[0] == "getActiveEffects" -> {
                            synchronized(configuration) {
                                val names = activeBehaviors.mapNotNull { it.nameHint }.filter { !Strings.isNullOrEmpty(it) }
                                val joined = "{" + names.joinToString(",") { it.replace(',', '_').replace('"', '_') } + "}"
                                respond(sender, "effects", joined)
                            }
                        }
                        // else: Ignore
                    }
                }
            }
        }
    }

    fun respond(endpoint: WirelessEndpoint, vararg data: Any) {
        queuedCommand = {
            if (responsePort > 0) {
                val cost = Settings.get.wirelessCostPerRange[Tier.Two] * CommandRange
                val epsilon = 0.1
                if (changeBuffer(-cost) > -epsilon) {
                    val packetData = (listOf("nanomachines") + data.toList()).toTypedArray()
                    val packet = api.Network.newPacket(uuid, null, responsePort, packetData)
                    api.Network.sendWirelessPacket(this, CommandRange, packet)
                }
            }
        }
        commandDelay = (Settings.get.nanomachinesCommandDelay * 20).toInt()
    }

    // ----------------------------------------------------------------------- //

    override fun reconfigure(): Controller {
        if (isServer) synchronized(configuration) {
            configuration.reconfigure()
            activeBehaviorsDirty = true

            if (player is EntityPlayerMP && player.connection != null) {
                player.addPotionEffect(PotionEffect(Potion.getPotionFromResourceLocation("blindness"), 100))
                player.addPotionEffect(PotionEffect(Potion.getPotionFromResourceLocation("poison"), 150))
                player.addPotionEffect(PotionEffect(Potion.getPotionFromResourceLocation("slowness"), 200))
                changeBuffer(-Settings.get.nanomachineReconfigureCost)

                hasSentConfiguration = false
            }
        }
        return this
    }

    override fun getTotalInputCount(): Int = synchronized(configuration) { configuration.triggers.size }

    override fun getSafeActiveInputs(): Int = Settings.get.nanomachinesSafeInputsActive

    override fun getMaxActiveInputs(): Int = Settings.get.nanomachinesMaxInputsActive

    override fun getInput(index: Int): Boolean = synchronized(configuration) { configuration.triggers[index].isActive }

    override fun setInput(index: Int, value: Boolean): Boolean {
        return isServer && synchronized(configuration) {
            (!value || configuration.triggers.count { it.isActive } < Settings.get.nanomachinesMaxInputsActive) && run {
                configuration.triggers[index].isActive = value
                activeBehaviorsDirty = true
                true
            }
        }
    }

    override fun getActiveBehaviors(): Iterable<Behavior> = synchronized(configuration) {
        cleanActiveBehaviors(DisableReason.InputChanged)
        activeBehaviors
    }

    override fun getInputCount(behavior: Behavior): Int = synchronized(configuration) { configuration.inputs(behavior) }

    // ----------------------------------------------------------------------- //

    override fun getLocalBuffer(): Double = storedEnergy

    override fun getLocalBufferSize(): Double = Settings.get.bufferNanomachines

    override fun changeBuffer(delta: Double): Double {
        return if (isClient) delta
        else if (delta < 0 && (Settings.get.ignorePower || player.capabilities.isCreativeMode)) 0.0
        else {
            val newValue = storedEnergy + delta
            storedEnergy = newValue.coerceIn(0.0, localBufferSize)
            newValue - storedEnergy
        }
    }

    // ----------------------------------------------------------------------- //

    fun update() {
        if (player.isDead) {
            return
        }

        if (isServer) {
            if (commandDelay > 0) {
                commandDelay -= 1
                if (commandDelay == 0) {
                    queuedCommand?.invoke()
                    queuedCommand = null
                }
            }

            // Handle dimension changes, the robust way (because when logging in,
            // load is called while the world is still set to the overworld, but
            // no dimension change event is fired if the player actually logged
            // out in another dimension... yay)
            if (player.world.provider.dimension != previousDimension) {
                api.Network.leaveWirelessNetwork(this, previousDimension)
                api.Network.joinWirelessNetwork(this)
                previousDimension = player.world.provider.dimension
            } else {
                api.Network.updateWirelessNetwork(this)
            }
        }

        var hasPower = localBuffer > 0 || Settings.get.ignorePower
        val active by lazy { activeBehaviors.toList() } // Wrap once.
        val activeInputs by lazy { configuration.triggers.count { it.isActive } }

        if (hasPower != hadPower) {
            if (!hasPower) {
                active.forEach { it.onDisable(DisableReason.OutOfEnergy) } // This may change our energy buffer.
                hasPower = localBuffer > 0 || Settings.get.ignorePower
            } else {
                active.forEach { it.onEnable() }
            }
        }

        if (hasPower) {
            active.forEach { it.update() }

            if (isServer) {
                if (Settings.get.isTickMultiple(player.entityWorld)) {
                    changeBuffer(-Settings.get.nanomachineCost * Settings.get.tickFrequency * (activeInputs + 0.5))
                    PacketSender.sendNanomachinePower(player)
                }

                val overload = activeInputs - safeActiveInputs
                if (!player.capabilities.isCreativeMode && overload > 0 && player.entityWorld.totalWorldTime % 20 == 0L) {
                    player.attackEntityFrom(OverloadDamage, overload.toFloat())
                }
            }

            if (isClient && Settings.get.enableNanomachinePfx) {
                val energyRatio = localBuffer / (localBufferSize + 1)
                val triggerRatio = activeInputs.toDouble() / (configuration.triggers.size + 1)
                val intensity = (energyRatio + triggerRatio) * 0.25
                PlayerUtils.spawnParticleAround(player, EnumParticleTypes.PORTAL, intensity)
            }
        }

        if (isServer) {
            // Send new power state, if it changed.
            if (hadPower != hasPower) {
                PacketSender.sendNanomachinePower(player)
            }

            // Send a full sync every now and then, e.g. for other players coming
            // closer that weren't there to get the initial info for an enabled
            // input.
            if (!hasSentConfiguration || player.entityWorld.totalWorldTime % FullSyncInterval == 0L) {
                hasSentConfiguration = true
                PacketSender.sendNanomachineConfiguration(player)
            }
        }

        hadPower = hasPower
    }

    fun reset() {
        synchronized(configuration) {
            for (index in 0 until totalInputCount) {
                configuration.triggers[index].isActive = false
                activeBehaviorsDirty = true
            }
            cleanActiveBehaviors(DisableReason.Default)
        }
    }

    fun dispose() {
        reset()
        if (isServer) {
            api.Network.leaveWirelessNetwork(this)
        }
    }

    fun debug() {
        if (isServer) {
            configuration.debug()
            activeBehaviorsDirty = true
        }
    }

    fun print() {
        if (isServer) {
            configuration.print(player)
        }
    }

    // ----------------------------------------------------------------------- //

    fun save(nbt: NBTTagCompound) {
        synchronized(configuration) {
            nbt.setString("uuid", uuid)
            nbt.setInteger("port", responsePort)
            nbt.setDouble("energy", storedEnergy)
            val configNbt = NBTTagCompound()
            configuration.save(configNbt)
            nbt.setTag("configuration", configNbt)
        }
    }

    fun load(nbt: NBTTagCompound) {
        synchronized(configuration) {
            uuid = nbt.getString("uuid")
            responsePort = nbt.getInteger("port")
            storedEnergy = nbt.getDouble("energy")
            configuration.load(nbt.getCompoundTag("configuration"))
            activeBehaviorsDirty = true
        }
    }

    // ----------------------------------------------------------------------- //

    private val isClient: Boolean get() = world().isRemote

    private val isServer: Boolean get() = !isClient

    private fun cleanActiveBehaviors(reason: DisableReason) {
        if (activeBehaviorsDirty) {
            synchronized(configuration) {
                if (activeBehaviorsDirty) {
                    val newBehaviors = configuration.behaviors.filter { it.isActive }.map { it.behavior }.toSet()
                    val addedBehaviors = newBehaviors - activeBehaviors
                    val removedBehaviors = activeBehaviors - newBehaviors
                    activeBehaviors.clear()
                    activeBehaviors.addAll(newBehaviors)
                    activeBehaviorsDirty = false
                    addedBehaviors.forEach { it.onEnable() }
                    removedBehaviors.forEach { it.onDisable(reason) }

                    if (isServer) {
                        PacketSender.sendNanomachineInputs(player)
                    }
                }
            }
        }
    }
}
