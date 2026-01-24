package li.cil.oc.common.recipe

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.detail.ItemInfo
import li.cil.oc.common.item.data.DroneData
import li.cil.oc.common.item.data.MicrocontrollerData
import li.cil.oc.common.item.data.PrintData
import li.cil.oc.common.item.data.RobotData
import li.cil.oc.common.item.data.TabletData
import li.cil.oc.server.machine.luac.LuaStateFactory
import li.cil.oc.util.Color
import li.cil.oc.util.SideTracker
import net.minecraft.init.Blocks
import net.minecraft.inventory.InventoryCrafting
import net.minecraft.item.ItemStack
import net.minecraft.item.crafting.IRecipe
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraft.nbt.NBTTagString
import java.util.UUID

object ExtendedRecipe {
    private val drone by lazy { ApiItems.get(Constants.ItemName.Drone) }
    private val eeprom by lazy { ApiItems.get(Constants.ItemName.EEPROM) }
    private val luaBios by lazy { ApiItems.get(Constants.ItemName.LuaBios) }
    private val mcu by lazy { ApiItems.get(Constants.BlockName.Microcontroller) }
    private val navigationUpgrade by lazy { ApiItems.get(Constants.ItemName.NavigationUpgrade) }
    private val linkedCard by lazy { ApiItems.get(Constants.ItemName.LinkedCard) }
    private val floppy by lazy { ApiItems.get(Constants.ItemName.Floppy) }
    private val hdds by lazy {
        arrayOf(
            ApiItems.get(Constants.ItemName.HDDTier1),
            ApiItems.get(Constants.ItemName.HDDTier2),
            ApiItems.get(Constants.ItemName.HDDTier3)
        )
    }
    private val cpus by lazy {
        arrayOf(
            ApiItems.get(Constants.ItemName.CPUTier1),
            ApiItems.get(Constants.ItemName.CPUTier2),
            ApiItems.get(Constants.ItemName.CPUTier3),
            ApiItems.get(Constants.ItemName.APUTier1),
            ApiItems.get(Constants.ItemName.APUTier2)
        )
    }
    private val robot by lazy { ApiItems.get(Constants.BlockName.Robot) }
    private val tablet by lazy { ApiItems.get(Constants.ItemName.Tablet) }
    private val print by lazy { ApiItems.get(Constants.BlockName.Print) }
    private val disabled by lazy {
        val stack = ItemStack(Blocks.DIRT)
        val tag = NBTTagCompound()
        val displayTag = NBTTagCompound()
        val loreList = NBTTagList()
        loreList.appendTag(NBTTagString("Autocrafting of this item is disabled to avoid exploits."))
        displayTag.setTag("Lore", loreList)
        tag.setTag("display", displayTag)
        stack.tagCompound = tag
        stack
    }

    @JvmStatic
    fun addNBTToResult(recipe: IRecipe, craftedStack: ItemStack, inventory: InventoryCrafting): ItemStack {
        val craftedItemName = ApiItems.get(craftedStack)

        if (craftedItemName == navigationUpgrade) {
            Driver.driverFor(craftedStack)?.let { driver ->
                for (stack in getItems(inventory)) {
                    if (stack.item == net.minecraft.init.Items.FILLED_MAP) {
                        // Store information of the map used for crafting in the result.
                        val nbt = driver.dataTag(craftedStack)
                        val mapTag = NBTTagCompound()
                        stack.writeToNBT(mapTag)
                        nbt.setTag(Settings.namespace + "map", mapTag)
                    }
                }
            }
        }

        if (craftedItemName == linkedCard) {
            if (SideTracker.isServer()) {
                Driver.driverFor(craftedStack)?.let { driver ->
                    val nbt = driver.dataTag(craftedStack)
                    nbt.setString(Settings.namespace + "tunnel", UUID.randomUUID().toString())
                }
            }
        }

        if (cpus.contains(craftedItemName)) {
            LuaStateFactory.setDefaultArch(craftedStack)
        }

        if (craftedItemName == floppy || hdds.contains(craftedItemName)) {
            if (!craftedStack.hasTagCompound()) {
                craftedStack.tagCompound = NBTTagCompound()
            }
            val nbt = craftedStack.tagCompound!!
            if (recipe.canFit(1, 1)) {
                // Formatting / loot to normal disk conversion, only keep coloring.
                val colorKey = Settings.namespace + "color"
                for (stack in getItems(inventory)) {
                    val stackInfo = ApiItems.get(stack)
                    if (stackInfo != null && (stackInfo == floppy || stackInfo.name() == "lootDisk") && stack.hasTagCompound()) {
                        val oldData = stack.tagCompound!!
                        if (oldData.hasKey(colorKey) && oldData.getInteger(colorKey) != Color.dyes.indexOf("lightGray")) {
                            nbt.setTag(colorKey, oldData.getTag(colorKey).copy())
                        }
                    }
                }
                if (nbt.isEmpty) {
                    craftedStack.tagCompound = null
                }
            } else if (getItems(inventory).all { ApiItems.get(it) == floppy }) {
                // Copy operation.
                for (stack in getItems(inventory)) {
                    if (ApiItems.get(stack) == floppy && stack.hasTagCompound()) {
                        val oldData = stack.tagCompound!!
                        for (oldTagName in oldData.keySet) {
                            if (!nbt.hasKey(oldTagName)) {
                                nbt.setTag(oldTagName, oldData.getTag(oldTagName).copy())
                            }
                        }
                    }
                }
            }
        }

        if (craftedItemName == print &&
            recipe is ExtendedShapelessOreRecipe &&
            recipe.ingredients.size == 2
        ) {
            // First, copy old data.
            val data = PrintData(craftedStack)
            val inputs = getItems(inventory)
            for (stack in inputs) {
                if (ApiItems.get(stack) == print) {
                    data.load(stack)
                }
            }

            // Then apply new data.
            val beaconBlocks = arrayOf(
                ItemStack(net.minecraft.init.Blocks.IRON_BLOCK),
                ItemStack(net.minecraft.init.Blocks.GOLD_BLOCK),
                ItemStack(net.minecraft.init.Blocks.EMERALD_BLOCK),
                ItemStack(net.minecraft.init.Blocks.DIAMOND_BLOCK)
            )

            val glowstoneDust = ItemStack(net.minecraft.init.Items.GLOWSTONE_DUST)
            val glowstone = ItemStack(net.minecraft.init.Blocks.GLOWSTONE)
            for (stack in inputs) {
                if (beaconBlocks.any { it.isItemEqual(stack) }) {
                    if (data.isBeaconBase) {
                        // Crafting wouldn't change anything, prevent accidental resource loss.
                        return ItemStack.EMPTY
                    }
                    data.isBeaconBase = true
                }
                if (glowstoneDust.isItemEqual(stack)) {
                    if (data.lightLevel == 15) {
                        // Crafting wouldn't change anything, prevent accidental resource loss.
                        return ItemStack.EMPTY
                    }
                    data.lightLevel = minOf(15, data.lightLevel + 1)
                }
                if (glowstone.isItemEqual(stack)) {
                    if (data.lightLevel == 15) {
                        // Crafting wouldn't change anything, prevent accidental resource loss.
                        return ItemStack.EMPTY
                    }
                    data.lightLevel = minOf(15, data.lightLevel + 4)
                }
            }

            // Finally apply modified data.
            data.save(craftedStack)
        }

        // EEPROM copying.
        if (craftedItemName == eeprom &&
            craftedStack.count == 2 &&
            recipe is ExtendedShapelessOreRecipe &&
            recipe.ingredients.size == 2
        ) {
            for (stack in getItems(inventory)) {
                if (ApiItems.get(stack) == eeprom && stack.hasTagCompound()) {
                    val copy = stack.tagCompound!!.copy() as NBTTagCompound
                    // Erase node address, just in case.
                    copy.getCompoundTag(Settings.namespace + "data").getCompoundTag("node").removeTag("address")
                    craftedStack.tagCompound = copy
                    break
                }
            }
        }

        // Swapping EEPROM in devices.
        recraft(craftedStack, inventory, mcu) { stack -> MCUDataWrapper(stack) }
        recraft(craftedStack, inventory, drone) { stack -> DroneDataWrapper(stack) }
        recraft(craftedStack, inventory, robot) { stack -> RobotDataWrapper(stack) }
        recraft(craftedStack, inventory, tablet) { stack -> TabletDataWrapper(stack) }

        return craftedStack
    }

    private fun getItems(inventory: InventoryCrafting): List<ItemStack> =
        (0 until inventory.sizeInventory).map { inventory.getStackInSlot(it) }.filter { !it.isEmpty }

    private fun recraft(craftedStack: ItemStack, inventory: InventoryCrafting, descriptor: ItemInfo, dataFactory: (ItemStack) -> ItemDataWrapper) {
        if (ApiItems.get(craftedStack) == descriptor) {
            // Find old Microcontroller.
            val oldMcu = getItems(inventory).find { ApiItems.get(it) == descriptor }
            if (oldMcu != null) {
                val data = dataFactory(oldMcu)

                // Remove old EEPROM.
                val oldRom = data.components.filter { ApiItems.get(it) == eeprom }
                data.components = data.components.toMutableList().apply { removeAll(oldRom) }.toTypedArray()

                // Insert new EEPROM.
                for (stack in getItems(inventory)) {
                    if (ApiItems.get(stack) == eeprom) {
                        data.components = data.components + stack.copy().apply { count = 1 }
                    }
                }

                data.save(craftedStack)
            }
        }
    }

    private interface ItemDataWrapper {
        var components: Array<ItemStack>
        fun save(stack: ItemStack)
    }

    private class MCUDataWrapper(val stack: ItemStack) : ItemDataWrapper {
        val data = MicrocontrollerData(stack)

        override var components: Array<ItemStack>
            get() = data.components
            set(value) { data.components = value }

        override fun save(stack: ItemStack) = data.save(stack)
    }

    private class DroneDataWrapper(val stack: ItemStack) : ItemDataWrapper {
        val data = DroneData(stack)

        override var components: Array<ItemStack>
            get() = data.components
            set(value) { data.components = value }

        override fun save(stack: ItemStack) = data.save(stack)
    }

    private class RobotDataWrapper(val stack: ItemStack) : ItemDataWrapper {
        val data = RobotData(stack)

        override var components: Array<ItemStack>
            get() = data.components
            set(value) { data.components = value }

        override fun save(stack: ItemStack) = data.save(stack)
    }

    private class TabletDataWrapper(val stack: ItemStack) : ItemDataWrapper {
        val data = TabletData(stack)

        override var components: Array<ItemStack> = data.items.filter { !it.isEmpty }.toTypedArray()

        override fun save(stack: ItemStack) {
            data.items = components.clone()
            data.save(stack)
        }
    }
}
