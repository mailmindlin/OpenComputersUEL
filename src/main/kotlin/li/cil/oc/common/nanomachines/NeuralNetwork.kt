package li.cil.oc.common.nanomachines

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.*
import li.cil.oc.api.Persistable
import li.cil.oc.api.nanomachines.Behavior
import li.cil.oc.api.nanomachines.BehaviorProvider
import li.cil.oc.server.PacketSender
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraft.util.text.TextComponentString
import net.minecraft.util.text.TextFormatting
import net.minecraftforge.common.util.Constants.NBT
import kotlin.random.Random

class NeuralNetwork(private val controller: ControllerImpl) : Persistable {
    val triggers: MutableList<TriggerNeuron> = mutableListOf()
    val connectors: MutableList<ConnectorNeuron> = mutableListOf()
    val behaviors: MutableList<BehaviorNeuron> = mutableListOf()

    val behaviorMap: MutableMap<Behavior, BehaviorNeuron> = mutableMapOf()

    fun inputs(behavior: Behavior): Int {
        return behaviorMap[behavior]?.let { node ->
            node.inputs.count { it.isActive }
        } ?: 0
    }

    fun reconfigure() {
        // Rebuild list of valid behaviors.
        behaviors.clear()
        for (provider in Nanomachines.getProviders()) {
            val createdBehaviors = provider.createBehaviors(controller.player)?.filterNotNull() ?: continue
            for (b in createdBehaviors) {
                behaviors.add(BehaviorNeuron(provider, b))
            }
        }

        // Adjust length of trigger list and reset.
        while (triggers.size > behaviors.size * Settings.get.nanomachineTriggerQuota) {
            triggers.removeAt(triggers.size - 1)
        }
        triggers.forEach { it.isActive = false }
        while (triggers.size < behaviors.size * Settings.get.nanomachineTriggerQuota) {
            triggers.add(TriggerNeuron())
        }

        // Adjust length of connector list and reset.
        while (connectors.size > behaviors.size * Settings.get.nanomachineConnectorQuota) {
            connectors.removeAt(connectors.size - 1)
        }
        connectors.forEach { it.inputs.clear() }
        while (connectors.size < behaviors.size * Settings.get.nanomachineConnectorQuota) {
            connectors.add(ConnectorNeuron())
        }

        // Build connections.
        val rng = Random(controller.player.entityWorld.rand.nextInt())

        fun <Sink : ConnectorNeuron, Source : Neuron> connect(sinks: Iterable<Sink>, sources: MutableList<Source>) {
            // Shuffle sink list to give each entry the same chance.
            val sinkPool = sinks.shuffled(rng).toMutableList()
            for (sink in sinkPool) {
                if (sources.isEmpty()) break
                // Avoid connecting one sink to the same source twice.
                val blacklist = mutableSetOf<Source>()
                for (n in 0..rng.nextInt(Settings.get.nanomachineMaxInputs)) {
                    if (sources.isEmpty()) break
                    val baseIndex = rng.nextInt(sources.size)
                    val rotatedSources = sources.drop(baseIndex) + sources.take(baseIndex)
                    val sourceIndex = rotatedSources.indexOfFirst { s -> !blacklist.contains(s) }
                    if (sourceIndex >= 0) {
                        val source = sources.removeAt((sourceIndex + baseIndex) % sources.size)
                        blacklist.add(source)
                        sink.inputs.add(source)
                    }
                }
            }
        }

        // Connect connectors to triggers, then behaviors to connectors and/or remaining triggers.
        val sourcePool: MutableList<Neuron> = mutableListOf()
        repeat(Settings.get.nanomachineMaxOutputs) {
            sourcePool.addAll(triggers)
        }
        connect(connectors, sourcePool)
        repeat(Settings.get.nanomachineMaxOutputs) {
            sourcePool.addAll(connectors)
        }
        connect(behaviors, sourcePool)

        // Clean up dead nodes.
        val deadConnectors = connectors.filter { it.inputs.isEmpty() }
        connectors.removeAll(deadConnectors)
        behaviors.forEach { it.inputs.removeAll(deadConnectors) }

        val deadBehaviors = behaviors.filter { it.inputs.isEmpty() }
        behaviors.removeAll(deadBehaviors)

        behaviorMap.clear()
        for (n in behaviors) {
            behaviorMap[n.behavior] = n
        }
    }

    // Enter debug configuration, one input -> one behavior, and list mapping in console.
    fun debug() {
        val log: (String) -> Unit = when (controller.player) {
            is EntityPlayerMP -> { s -> PacketSender.sendClientLog(s, controller.player) }
            else -> { s -> OpenComputers.log.info(s) }
        }
        log("Creating debug configuration for nanomachines in player ${controller.player.displayName}.")

        behaviors.clear()
        for (provider in Nanomachines.getProviders()) {
            val createdBehaviors = provider.createBehaviors(controller.player)?.filterNotNull() ?: continue
            for (b in createdBehaviors) {
                behaviors.add(BehaviorNeuron(provider, b))
            }
        }

        connectors.clear()

        triggers.clear()
        for (i in behaviors.indices) {
            val behavior = behaviors[i]
            val trigger = TriggerNeuron()
            triggers.add(trigger)
            behavior.inputs.add(trigger)

            log("$i -> ${behavior.behavior.nameHint} (${behavior.behavior.javaClass})")
        }
    }

    fun print(player: EntityPlayer) {
        val sb = StringBuilder()
        fun colored(value: Any, enabled: Boolean) {
            if (enabled) sb.append(TextFormatting.GREEN)
            else sb.append(TextFormatting.RED)
            sb.append(value)
            sb.append(TextFormatting.RESET)
        }
        for (behavior in behaviors) {
            val name = behavior.behavior.nameHint ?: behavior.behavior.javaClass.simpleName
            colored(name, behavior.isActive)
            sb.append(" <- (")
            var first = true
            for (input in behavior.inputs) {
                if (first) first = false else sb.append(", ")
                when (input) {
                    is TriggerNeuron -> {
                        colored(triggers.indexOf(input) + 1, input.isActive)
                    }
                    is ConnectorNeuron -> {
                        sb.append("(")
                        var innerFirst = true
                        for (trigger in input.inputs) {
                            if (innerFirst) innerFirst = false else sb.append(", ")
                            colored(triggers.indexOf(trigger) + 1, trigger.isActive)
                        }
                        first = false
                        sb.append(")")
                    }
                }
            }
            sb.append(")")
            player.sendMessage(TextComponentString(sb.toString()))
            sb.clear()
        }
    }

    override fun save(nbt: NBTTagCompound) {
        save(nbt, forItem = false)
    }

    companion object {
        private const val TriggersTag = "triggers"
        private const val IsActiveTag = "isActive"
        private const val ConnectorsTag = "connectors"
        private const val BehaviorsTag = "behaviors"
        private const val BehaviorTag = "behavior"
        private const val TriggerInputsTag = "triggerInputs"
        private const val ConnectorInputsTag = "connectorInputs"
    }

    fun save(nbt: NBTTagCompound, forItem: Boolean) {
        val triggersNbt = NBTTagList()
        for (t in triggers) {
            val tagNbt = NBTTagCompound()
            tagNbt.setBoolean(IsActiveTag, t.isActive && !forItem)
            triggersNbt.appendTag(tagNbt)
        }
        nbt.setTag(TriggersTag, triggersNbt)

        val connectorsNbt = NBTTagList()
        for (c in connectors) {
            val tagNbt = NBTTagCompound()
            tagNbt.setIntArray(TriggerInputsTag, c.inputs.mapNotNull { triggers.indexOf(it).takeIf { idx -> idx >= 0 } }.toIntArray())
            connectorsNbt.appendTag(tagNbt)
        }
        nbt.setTag(ConnectorsTag, connectorsNbt)

        val behaviorsNbt = NBTTagList()
        for (b in behaviors) {
            val tagNbt = NBTTagCompound()
            tagNbt.setIntArray(TriggerInputsTag, b.inputs.mapNotNull { triggers.indexOf(it).takeIf { idx -> idx >= 0 } }.toIntArray())
            tagNbt.setIntArray(ConnectorInputsTag, b.inputs.mapNotNull { connectors.indexOf(it).takeIf { idx -> idx >= 0 } }.toIntArray())
            tagNbt.setTag(BehaviorTag, b.provider.writeToNBT(b.behavior))
            behaviorsNbt.appendTag(tagNbt)
        }
        nbt.setTag(BehaviorsTag, behaviorsNbt)
    }

    override fun load(nbt: NBTTagCompound) {
        triggers.clear()
        val triggersNbt = nbt.getTagList(TriggersTag, NBT.TAG_COMPOUND)
        for (i in 0 until triggersNbt.tagCount()) {
            val t = triggersNbt.getCompoundTagAt(i)
            val neuron = TriggerNeuron()
            neuron.isActive = t.getBoolean(IsActiveTag)
            triggers.add(neuron)
        }

        connectors.clear()
        val connectorsNbt = nbt.getTagList(ConnectorsTag, NBT.TAG_COMPOUND)
        for (i in 0 until connectorsNbt.tagCount()) {
            val t = connectorsNbt.getCompoundTagAt(i)
            val neuron = ConnectorNeuron()
            neuron.inputs.addAll(t.getIntArray(TriggerInputsTag).map { triggers[it] })
            connectors.add(neuron)
        }

        behaviors.clear()
        val behaviorsNbt = nbt.getTagList(BehaviorsTag, NBT.TAG_COMPOUND)
        for (i in 0 until behaviorsNbt.tagCount()) {
            val t = behaviorsNbt.getCompoundTagAt(i)
            for (p in Nanomachines.getProviders()) {
                val b = p.readFromNBT(controller.player, t.getCompoundTag(BehaviorTag))
                if (b is Behavior) {
                    val neuron = BehaviorNeuron(p, b)
                    neuron.inputs.addAll(t.getIntArray(TriggerInputsTag).map { triggers[it] })
                    neuron.inputs.addAll(t.getIntArray(ConnectorInputsTag).map { connectors[it] })
                    behaviors.add(neuron)
                    break // Done.
                }
                // Keep looking.
            }
        }

        behaviorMap.clear()
        for (n in behaviors) {
            behaviorMap[n.behavior] = n
        }
    }

    interface Neuron {
        val isActive: Boolean
    }

    class TriggerNeuron : Neuron {
        override var isActive: Boolean = false
    }

    open class ConnectorNeuron : Neuron {
        val inputs: MutableList<Neuron> = mutableListOf()

        override val isActive: Boolean get() = inputs.all { it.isActive }
    }

    class BehaviorNeuron(val provider: BehaviorProvider, val behavior: Behavior) : ConnectorNeuron()
}
