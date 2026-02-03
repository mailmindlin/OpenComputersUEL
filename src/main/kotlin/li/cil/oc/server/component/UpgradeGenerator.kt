package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.internal.Agent
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.util.Result
import li.cil.oc.util.StackOption
import li.cil.oc.util.result
import li.cil.oc.util.setNewCompoundTag
import net.minecraft.entity.item.EntityItem
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntityFurnace

open class UpgradeGenerator(val host: EnvironmentHost) : ManagedEnvironmentKt(), DeviceInfo {
    private val agent: Agent
        get() = host as Agent

    override val node = nodeFactory(Visibility.Network)
        .withComponent("generator", Visibility.Neighbors)
        .withConnector()
        .create()

    var inventory: ItemStack? = null

    var remainingTicks = 0

    private val deviceInfo = mapOf(
        DeviceAttribute.Class to DeviceClass.Power,
        DeviceAttribute.Description to "Generator",
        DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
        DeviceAttribute.Product to "Portagen 2.0 (Rev. 3)",
        DeviceAttribute.Capacity to "1"
    )

    override fun getDeviceInfo() = deviceInfo

    // ----------------------------------------------------------------------- //

    @Callback(doc = "function([count:number]):boolean -- Tries to insert fuel from the selected slot into the generator's queue.")
    fun insert(context: Context, args: Arguments): Result {
        val count = args.optInteger(0, 64)
        val stack = agent.mainInventory().getStackInSlot(agent.selectedSlot())
        if (stack.isEmpty) return result(Unit, "selected slot is empty")
        if (!TileEntityFurnace.isItemFuel(stack)) {
            return result(Unit, "selected slot does not contain fuel")
        }
        val container: ItemStack = stack.item.getContainerItem(stack)
        val inQueue: ItemStack = inventory ?: ItemStack.EMPTY
        if (!inQueue.isEmpty && inQueue.count > 0) {
            if (!inQueue.isItemEqual(stack) || !ItemStack.areItemStackTagsEqual(inQueue, stack)) {
                return result(Unit, "different fuel type already queued")
            }
        }
        val space = if (inQueue.isEmpty) stack.maxStackSize else inQueue.maxStackSize - inQueue.count
        if (space == 0) {
            return result(Unit, "queue is full")
        }
        val previousSelectedFuel: ItemStack = stack.copy()
        val insertLimit: Int = minOf(stack.count, minOf(space, count))
        val fuelToInsert: ItemStack = stack.splitStack(insertLimit)

        // remove the fuel from the inventory
        if (stack.count == 0) {
            agent.mainInventory().setInventorySlotContents(agent.selectedSlot(), ItemStack.EMPTY)
        } else {
            agent.mainInventory().setInventorySlotContents(agent.selectedSlot(), stack)
        }

        // add empty containers to inventory
        if (!container.isEmpty) {
            container.grow(fuelToInsert.count - 1)
            if (!agent.player().inventory.addItemStackToInventory(container)) {
                // no containers could be placed in inventory, give back the fuel
                agent.mainInventory().setInventorySlotContents(agent.selectedSlot(), previousSelectedFuel)
                return result(false, "no space in inventory for fuel containers")
            } else if (container.count > 0) {
                // not all the containers could be inserted in the inventory
                agent.player().entityDropItem(container.copy(), -0.25f)
            }
        }

        // could be zero
        fuelToInsert.grow(inQueue.count)
        inventory = fuelToInsert

        return result(true, insertLimit)
    }

    @Callback(doc = "function():number -- Get the size of the item stack in the generator's queue.")
    fun count(context: Context, args: Arguments): Result {
        return inventory?.let { stack ->
            result(stack.count, stack.item.getItemStackDisplayName(stack))
        } ?: result(0)
    }

    @Callback(doc = "function([count:number]):boolean -- Tries to remove items from the generator's queue.")
    fun remove(context: Context, args: Arguments): Result {
        val count = args.optInteger(0, Int.MAX_VALUE)
        if (count <= 0) {
            return result(true) // it is allowed to remove zero
        }
        val inQueue: ItemStack = inventory?.takeIf { !it.isEmpty && it.count > 0 } ?: ItemStack.EMPTY
        if (inQueue.isEmpty) {
            return result(false, "queue is empty")
        }
        val previousSelectedItem: ItemStack = agent.mainInventory().getStackInSlot(agent.selectedSlot()).copy()
        val containerItem = inQueue.item.getContainerItem(inQueue)
        val emptyContainer: ItemStack = if (!containerItem.isEmpty && containerItem.count > 0) {
            if (!previousSelectedItem.isEmpty &&
                previousSelectedItem.item == containerItem.item &&
                ItemStack.areItemStackTagsEqual(previousSelectedItem, containerItem)
            ) {
                previousSelectedItem.copy()
            } else {
                return result(false, "removing this fuel requires the appropriate container in the selected slot")
            }
        } else {
            ItemStack.EMPTY // nothing to do, nothing required
        }

        val removeLimit: Int = minOf(inQueue.count, if (emptyContainer.isEmpty) count else emptyContainer.count)

        // backup in case of failure
        val previousQueue = inQueue.copy()
        val forUser = inQueue.splitStack(removeLimit)
        if (!emptyContainer.isEmpty) {
            emptyContainer.splitStack(removeLimit)
            if (emptyContainer.isEmpty) {
                agent.mainInventory().setInventorySlotContents(agent.selectedSlot(), ItemStack.EMPTY)
            } else {
                agent.mainInventory().decrStackSize(agent.selectedSlot(), removeLimit)
            }
        }
        // addItemStackToInventory splits the input stack by reference
        return if (!agent.player().inventory.addItemStackToInventory(forUser)) {
            // returns false if NO items were inserted
            agent.mainInventory().setInventorySlotContents(agent.selectedSlot(), previousSelectedItem)
            inventory = previousQueue
            result(false, "no inventory space available for fuel")
        } else {
            val actualRemoval: Int = removeLimit - forUser.count
            previousQueue.shrink(actualRemoval) // reduce it by how much was given to the user
            inventory = previousQueue
            result(true, actualRemoval)
        }
    }

    // ----------------------------------------------------------------------- //

    override fun canUpdate(): Boolean = true

    override fun update() {
        super.update()
        if (remainingTicks <= 0 && inventory != null) {
            val stack = inventory!!
            remainingTicks = TileEntityFurnace.getItemBurnTime(stack)
            if (remainingTicks > 0) {
                updateClient()
                stack.shrink(1)
                if (stack.count <= 0) {
                    // do not put container in inventory (we left the container when fuel was inserted)
                    inventory = null
                }
            }
        }
        if (remainingTicks > 0) {
            remainingTicks -= 1
            if (remainingTicks == 0 && inventory == null) {
                updateClient()
            }
            node!!.changeBuffer(Settings.get.generatorEfficiency)
        }
    }

    private fun updateClient() {
        if (host is li.cil.oc.api.internal.Robot) {
            val robot = host as li.cil.oc.api.internal.Robot
            robot.synchronizeSlot(robot.componentSlot(node!!.address()))
        }
    }

    // ----------------------------------------------------------------------- //

    override fun onDisconnect(node: Node) {
        super.onDisconnect(node)
        if (node == this.node) {
            inventory?.let { stack ->
                val world = host.world()
                val entity = EntityItem(world, host.xPosition(), host.yPosition(), host.zPosition(), stack.copy())
                entity.motionY = 0.04
                entity.setPickupDelay(5)
                world.spawnEntity(entity)
                inventory = null
            }
            remainingTicks = 0
        }
    }

    private val InventoryTag = "inventory"
    private val RemainingTicksTag = "remainingTicks"

    override fun load(nbt: NBTTagCompound) {
        super.load(nbt)
        if (nbt.hasKey(InventoryTag)) {
            inventory = ItemStack(nbt.getCompoundTag(InventoryTag))
        }
        remainingTicks = nbt.getInteger(RemainingTicksTag)
    }

    override fun save(nbt: NBTTagCompound) {
        super.save(nbt)
        inventory?.let { stack ->
            nbt.setNewCompoundTag(InventoryTag) { stack.writeToNBT(it) }
        }
        if (remainingTicks > 0) {
            nbt.setInteger(RemainingTicksTag, remainingTicks)
        }
    }
}
