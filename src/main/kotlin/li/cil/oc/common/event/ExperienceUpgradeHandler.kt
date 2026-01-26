package li.cil.oc.common.event

import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.api.event.RobotAnalyzeEvent
import li.cil.oc.api.event.RobotAttackEntityEvent
import li.cil.oc.api.event.RobotBreakBlockEvent
import li.cil.oc.api.event.RobotExhaustionEvent
import li.cil.oc.api.event.RobotMoveEvent
import li.cil.oc.api.event.RobotPlaceBlockEvent
import li.cil.oc.api.event.RobotRenderEvent
import li.cil.oc.api.event.RobotUsedToolEvent
import li.cil.oc.api.internal.Agent
import li.cil.oc.api.internal.Robot
import li.cil.oc.api.network.Node
import li.cil.oc.server.component.UpgradeExperience
import net.minecraft.client.renderer.GlStateManager
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object ExperienceUpgradeHandler {
    @JvmStatic
    @SubscribeEvent
    fun onRobotAnalyze(e: RobotAnalyzeEvent) {
        val (level, experience) = getLevelAndExperience(e.agent)
        // This is basically a 'does it have an experience upgrade' check.
        if (experience != 0.0) {
            e.player.sendMessage(Localization.Analyzer.RobotXp(experience, level))
        }
    }

    @JvmStatic
    @SubscribeEvent
    fun onRobotComputeDamageRate(e: RobotUsedToolEvent.ComputeDamageRate) {
        e.setDamageRate(e.damageRate * maxOf(0.0, 1 - getLevel(e.agent) * Settings.get.toolEfficiencyPerLevel))
    }

    @JvmStatic
    @SubscribeEvent
    fun onRobotBreakBlockPre(e: RobotBreakBlockEvent.Pre) {
        val boost = maxOf(0.0, 1 - getLevel(e.agent) * Settings.get.harvestSpeedBoostPerLevel)
        e.setBreakTime(e.breakTime * boost)
    }

    @JvmStatic
    @SubscribeEvent
    fun onRobotAttackEntityPost(e: RobotAttackEntityEvent.Post) {
        val agent = e.agent
        if (agent is Robot) {
            if (!agent.equipmentInventory().getStackInSlot(0).isEmpty && e.target.isDead) {
                addExperience(agent, Settings.get.robotActionXp)
            }
        }
    }

    @JvmStatic
    @SubscribeEvent
    fun onRobotBreakBlockPost(e: RobotBreakBlockEvent.Post) {
        addExperience(e.agent, e.experience * Settings.get.robotOreXpRate + Settings.get.robotActionXp)
    }

    @JvmStatic
    @SubscribeEvent
    fun onRobotPlaceBlockPost(e: RobotPlaceBlockEvent.Post) {
        addExperience(e.agent, Settings.get.robotActionXp)
    }

    @JvmStatic
    @SubscribeEvent
    fun onRobotMovePost(e: RobotMoveEvent.Post) {
        addExperience(e.agent, Settings.get.robotExhaustionXpRate * 0.01)
    }

    @JvmStatic
    @SubscribeEvent
    fun onRobotExhaustion(e: RobotExhaustionEvent) {
        addExperience(e.agent, Settings.get.robotExhaustionXpRate * e.exhaustion)
    }

    @JvmStatic
    @SubscribeEvent
    fun onRobotRender(e: RobotRenderEvent) {
        val level = when (val agent = e.agent) {
            is Robot -> {
                var acc = 0
                for (index in 0 until agent.sizeInventory) {
                    val component = agent.getComponentInSlot(index)
                    if (component is UpgradeExperience) {
                        acc += component.level
                    }
                }
                acc
            }
            else -> 0
        }
        when {
            level > 19 -> GlStateManager.color(0.4f, 1f, 1f)
            level > 9 -> GlStateManager.color(1f, 1f, 0.4f)
            else -> GlStateManager.color(0.5f, 0.5f, 0.5f)
        }
    }

    private fun getLevel(agent: Agent): Int {
        var level = 0
        forEachUpgrade(agent.machine().node()!!) { upgrade -> level += upgrade.level }
        return level
    }

    private fun getLevelAndExperience(agent: Agent): Pair<Int, Double> {
        var level = 0
        var experience = 0.0
        forEachUpgrade(agent.machine().node()!!) { upgrade ->
            level += upgrade.level
            experience += upgrade.experience
        }
        return Pair(level, experience)
    }

    private fun addExperience(agent: Agent, amount: Double) {
        forEachUpgrade(agent.machine().node()!!) { upgrade -> upgrade.addExperience(amount) }
    }

    private inline fun forEachUpgrade(node: Node, f: (UpgradeExperience) -> Unit) {
        node.reachableNodes().forEach { n ->
            val host = n.host()
            if (host is UpgradeExperience) {
                f(host)
            }
        }
    }
}
