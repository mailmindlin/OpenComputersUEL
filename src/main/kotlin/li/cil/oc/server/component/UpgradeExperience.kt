package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.internal.Agent
import li.cil.oc.api.internal.Robot
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.util.UpgradeExperience
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.item.EntityXPOrb
import net.minecraft.init.Items
import net.minecraft.nbt.NBTTagCompound

class UpgradeExperience(val host: EnvironmentHost) : ManagedEnvironmentKt(), DeviceInfoKt {
    companion object {
        const val MaxLevel = 30
    }

    var experience = 0.0
    var level = 0

    private val agent: Agent
        get() = host as Agent

    override val node = nodeFactory(Visibility.Network)
        .withComponent("experience")
        .withConnector((30 * Settings.get.bufferPerLevel).toDouble())
        .create()

    override val deviceInfo = mapOf(
        DeviceAttribute.Class to DeviceClass.Generic,
        DeviceAttribute.Description to "Knowledge database",
        DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
        DeviceAttribute.Product to "ERSO (Event Recorder and Self-Optimizer)",
        DeviceAttribute.Capacity to "30"
    )

    private val xpForNextLevel: Double
        get() = UpgradeExperience.xpForLevel(level + 1)

    internal fun addExperience(value: Double) {
        if (level < MaxLevel) {
            experience += value
            if (experience >= xpForNextLevel) {
                updateXpInfo()
            }
            val world = agent.world()
            val pos = agent.player().position
            val orb = EntityXPOrb(world, pos.x.toDouble() + 0.5, pos.y.toDouble() + 0.5, pos.z.toDouble() + 0.5, value.toInt())
            agent.player().xpCooldown = 0
            orb.onCollideWithPlayer(agent.player())
        }
    }

    fun updateXpInfo() {
        // xp(level) = base + (level * const) ^ exp
        // pow(xp(level) - base, 1/exp) / const = level
        val oldLevel = level
        level = UpgradeExperience.calculateLevelFromExperience(experience)
        if (node != null) {
            if (level != oldLevel) {
                updateClient()
            }
            node.setLocalBufferSize((Settings.get.bufferPerLevel * level).toDouble())
        }
    }

    @Callback(direct = true, doc = "function():number -- The current level of experience stored in this experience upgrade.")
    fun level(context: Context, args: Arguments): Result =
        result(UpgradeExperience.calculateExperienceLevel(level, experience))

    @Callback(doc = "function():boolean -- Tries to consume an enchanted item to add experience to the upgrade.")
    fun consume(context: Context, args: Arguments): Result {
        if (level >= MaxLevel) {
            return result(Unit, "max level")
        }
        val stack = agent.mainInventory().getStackInSlot(agent.selectedSlot())
        if (stack.isEmpty) {
            return result(Unit, "no item")
        }
        var xp = 0
        if (stack.item == Items.EXPERIENCE_BOTTLE) {
            xp += 3 + agent.world().rand.nextInt(5) + agent.world().rand.nextInt(5)
        } else {
            val enchantments = EnchantmentHelper.getEnchantments(stack)
            for ((enchantment, level) in enchantments) {
                if (enchantment != null) {
                    xp += enchantment.getMinEnchantability(level)
                }
            }
            if (xp <= 0) {
                return result(Unit, "could not extract experience from item")
            }
        }
        val consumed = agent.mainInventory().decrStackSize(agent.selectedSlot(), 1)
        if (consumed.isEmpty) {
            return result(Unit, "could not consume item")
        }
        addExperience(xp * Settings.get.constantXpGrowth)
        return result(true)
    }

    private fun updateClient() {
        if (host is Robot) {
            val robot = host as Robot
            robot.synchronizeSlot(robot.componentSlot(node.address()))
        }
    }

    override fun save(nbt: NBTTagCompound) {
        super.save(nbt)
        UpgradeExperience.setExperience(nbt, experience)
    }

    override fun load(nbt: NBTTagCompound) {
        super.load(nbt)
        experience = UpgradeExperience.getExperience(nbt)
        updateXpInfo()
    }
}
