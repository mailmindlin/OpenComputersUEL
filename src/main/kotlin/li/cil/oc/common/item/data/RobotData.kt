package li.cil.oc.common.item.data

import com.google.common.base.Charsets
import com.google.common.base.Strings
import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.integration.opencomputers.DriverScreen
import li.cil.oc.util.ItemUtils
import li.cil.oc.util.setNewTagList
import li.cil.oc.util.toArray
import li.cil.oc.util.toNbt
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.util.Constants.NBT
import java.io.BufferedReader
import java.io.InputStreamReader

class RobotData : ItemData {
    constructor() : super(Constants.BlockName.Robot)

    constructor(stack: ItemStack) : this() {
        load(stack)
    }

    var name = ""

    // Overall energy including components.
    var totalEnergy = 0

    // Energy purely stored in robot component - this is what we have to restore manually.
    var robotEnergy = 0

    var tier = 0

    var components = emptyArray<ItemStack>()

    var containers = emptyArray<ItemStack>()

    var lightColor = 0xF23030

    private val StoredEnergyTag = Settings.namespace + "storedEnergy"
    private val RobotEnergyTag = Settings.namespace + "robotEnergy"
    private val TierTag = Settings.namespace + "tier"
    private val ComponentsTag = Settings.namespace + "components"
    private val ContainersTag = Settings.namespace + "containers"
    private val LightColorTag = Settings.namespace + "lightColor"

    override fun load(nbt: NBTTagCompound) {
        name = ItemUtils.getDisplayName(nbt) ?: ""
        if (Strings.isNullOrEmpty(name)) {
            name = RobotData.randomName
        }
        totalEnergy = nbt.getInteger(StoredEnergyTag)
        robotEnergy = nbt.getInteger(RobotEnergyTag)
        tier = nbt.getInteger(TierTag)
        components = nbt.getTagList(ComponentsTag, NBT.TAG_COMPOUND)
            .toArray<NBTTagCompound>()
            .map { ItemStack(it) }
            .toTypedArray()
        containers = nbt.getTagList(ContainersTag, NBT.TAG_COMPOUND)
            .toArray<NBTTagCompound>()
            .map { ItemStack(it) }
            .toTypedArray()
        if (nbt.hasKey(LightColorTag)) {
            lightColor = nbt.getInteger(LightColorTag)
        }
    }

    override fun save(nbt: NBTTagCompound) {
        if (!Strings.isNullOrEmpty(name)) {
            ItemUtils.setDisplayName(nbt, name)
        }
        nbt.setInteger(StoredEnergyTag, totalEnergy)
        nbt.setInteger(RobotEnergyTag, robotEnergy)
        nbt.setInteger(TierTag, tier)
        nbt.setNewTagList(ComponentsTag, components.asIterable().map { it.toNbt() })
        nbt.setNewTagList(ContainersTag, containers.asIterable().map { it.toNbt() })
        nbt.setInteger(LightColorTag, lightColor)
    }

    fun copyItemStack(): ItemStack {
        val stack = createItemStack()
        // Forget all node addresses and so on. This is used when 'picking' a
        // robot in creative mode.
        val newInfo = RobotData(stack)
        newInfo.components.forEach { cs ->
            val driver = Driver.driverFor(cs)
            if (driver != null && driver == DriverScreen) {
                val nbt = driver.dataTag(cs)
                for (tagName in nbt.keySet.toTypedArray()) {
                    nbt.removeTag(tagName)
                }
            }
        }
        // Don't show energy info (because it's unreliable) but fill up the
        // internal buffer. This is for creative use only, anyway.
        newInfo.totalEnergy = 0
        newInfo.robotEnergy = 50000
        newInfo.save(stack)
        return stack
    }

    companion object {
        @JvmField
        val names: Array<String> = try {
            val inputStream = RobotData::class.java.getResourceAsStream(
                "/assets/${Settings.resourceDomain}/robot.names"
            )
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).useLines { lines ->
                lines
                    .map { it.takeWhile { c -> c != '#' }.trim() }
                    .filter { it.isNotEmpty() }
                    .toList()
                    .toTypedArray()
            }
        } catch (t: Throwable) {
            OpenComputers.log.warn("Failed loading robot name list.", t)
            emptyArray()
        }

        @JvmStatic
        val randomName: String
            get() = if (names.isNotEmpty()) names[(Math.random() * names.size).toInt()] else "Robot"
    }
}
