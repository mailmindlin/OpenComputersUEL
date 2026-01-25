package li.cil.oc.common.item.data

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.util.setNewCompoundTag
import net.minecraft.item.ItemMap
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.world.World

class NavigationUpgradeData : ItemData {
    constructor() : super(Constants.ItemName.NavigationUpgrade)

    constructor(stack: ItemStack) : this() {
        load(stack)
    }

    var map = ItemStack(net.minecraft.init.Items.FILLED_MAP)

    fun mapData(world: World) = try {
        (map.item as ItemMap).getMapData(map, world)
    } catch (e: Throwable) {
        throw Exception("invalid map")
    }

    fun getSize(world: World): Int {
        val info = mapData(world)
        return 128 * (1 shl info.scale.toInt())
    }

    private val DataTag = Settings.namespace + "data"
    private val MapTag = Settings.namespace + "map"

    override fun load(stack: ItemStack) {
        if (stack.hasTagCompound()) {
            load(stack.tagCompound!!.getCompoundTag(DataTag))
        }
    }

    override fun save(stack: ItemStack) {
        if (!stack.hasTagCompound()) {
            stack.tagCompound = NBTTagCompound()
        }
        save(stack.getCompoundTag(DataTag))
    }

    override fun load(nbt: NBTTagCompound) {
        if (nbt.hasKey(MapTag)) {
            map = ItemStack(nbt.getCompoundTag(MapTag))
        }
    }

    override fun save(nbt: NBTTagCompound) {
        if (map != null) {
            nbt.setNewCompoundTag(MapTag, map::writeToNBT)
        }
    }
}
