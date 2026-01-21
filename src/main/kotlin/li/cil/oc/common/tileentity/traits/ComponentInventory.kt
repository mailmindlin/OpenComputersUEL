package li.cil.oc.common.tileentity.traits

import li.cil.oc.api.driver.DriverItem
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.network.Node
import li.cil.oc.common.EventHandler
import li.cil.oc.common.inventory.Inventory
import li.cil.oc.common.inventory.ComponentInventory as InventoryComponentInventory
import li.cil.oc.util.StackOption
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ICapabilityProvider
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

abstract class ComponentInventory : Environment, Inventory, InventoryComponentInventory {
    override fun host() = this

    // ----------------------------------------------------------------------- //

    // Cache changes to inventory slots on the client side to avoid recreating
    // components when we don't have to and the slots are just cleared by MC
    // temporarily.
    private val pendingRemovalsActual: MutableList<StackOption> by lazy {
        MutableList(getSizeInventory()) { StackOption.Empty }
    }
    private val pendingAddsActual: MutableList<StackOption> by lazy {
        MutableList(getSizeInventory()) { StackOption.Empty }
    }
    private var updateScheduled = false

    val pendingRemovals: MutableList<StackOption>
        get() {
            adjustSize(pendingRemovalsActual)
            return pendingRemovalsActual
        }

    val pendingAdds: MutableList<StackOption>
        get() {
            adjustSize(pendingAddsActual)
            return pendingAddsActual
        }

    private fun adjustSize(buffer: MutableList<StackOption>) {
        val delta = buffer.size - getSizeInventory()
        if (delta > 0) {
            for (i in 0 until delta) {
                buffer.removeAt(buffer.size - 1)
            }
        } else if (delta < 0) {
            for (i in 0 until -delta) {
                buffer.add(StackOption.Empty)
            }
        }
    }

    private fun applyInventoryChanges() {
        updateScheduled = false
        for (slot in 0 until getSizeInventory()) {
            val removed = pendingRemovals[slot]
            val added = pendingAdds[slot]
            when {
                removed is StackOption.SomeStack && added is StackOption.SomeStack -> {
                    if (!removed.stack.isItemEqual(added.stack) || !ItemStack.areItemStackTagsEqual(removed.stack, added.stack)) {
                        super.onItemRemoved(slot, removed.stack)
                        super.onItemAdded(slot, added.stack)
                        markDirty()
                    }
                    // else: No change, ignore.
                }
                removed is StackOption.SomeStack && added is StackOption.Empty -> {
                    super.onItemRemoved(slot, removed.stack)
                    markDirty()
                }
                removed is StackOption.Empty && added is StackOption.SomeStack -> {
                    super.onItemAdded(slot, added.stack)
                    markDirty()
                }
                // No change.
            }

            pendingRemovals[slot] = StackOption.Empty
            pendingAdds[slot] = StackOption.Empty
        }
    }

    private fun scheduleInventoryChange() {
        if (!updateScheduled) {
            updateScheduled = true
            EventHandler.scheduleClient { applyInventoryChanges() }
        }
    }

    override fun onItemAdded(slot: Int, stack: ItemStack) {
        if (isServer) {
            super.onItemAdded(slot, stack)
        } else {
            val removed = pendingRemovals[slot]
            if (removed is StackOption.SomeStack && removed.stack.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(removed.stack, stack)) {
                // Reverted to original state.
                pendingAdds[slot] = StackOption.Empty
                pendingRemovals[slot] = StackOption.Empty
            } else {
                // Got a removal and an add of *something else* in the same tick.
                pendingAdds[slot] = StackOption.SomeStack(stack)
                scheduleInventoryChange()
            }
        }
    }

    override fun onItemRemoved(slot: Int, stack: ItemStack) {
        if (isServer) {
            super.onItemRemoved(slot, stack)
        } else {
            val added = pendingAdds[slot]
            if (added is StackOption.SomeStack) {
                // If we have a pending add and get a remove on a slot it is
                // now either empty, or the previous remove is valid again.
                pendingAdds[slot] = StackOption.Empty
            } else {
                // If we have no pending add, only the first removal can be
                // relevant (further ones should in fact be impossible).
                if (pendingRemovals[slot] is StackOption.Empty) {
                    pendingRemovals[slot] = StackOption.SomeStack(stack)
                    scheduleInventoryChange()
                }
            }
        }
    }

    override fun save(component: ManagedEnvironment, driver: DriverItem, stack: ItemStack) {
        if (isServer) {
            super.save(component, driver, stack)
        }
    }

    // ----------------------------------------------------------------------- //

    override fun initialize() {
        super.initialize()
        if (isClient) {
            connectComponents()
        }
    }

    override fun dispose() {
        super.dispose()
        if (isClient) {
            disconnectComponents()
        }
    }

    override fun onConnect(node: Node) {
        super.onConnect(node)
        if (node == this.node()) {
            connectComponents()
        }
    }

    override fun onDisconnect(node: Node) {
        super.onDisconnect(node)
        if (node == this.node()) {
            disconnectComponents()
        }
    }

    override fun hasCapability(capability: Capability<*>, facing: EnumFacing?): Boolean {
        val localFacing = when {
            facing == null -> null
            this is Rotatable -> (this as Rotatable).toLocal(facing)
            else -> facing
        }
        return super.hasCapability(capability, facing) || components().any { component ->
            component != null && component is ICapabilityProvider && component.hasCapability(capability, localFacing)
        }
    }

    override fun <T> getCapability(capability: Capability<T>, facing: EnumFacing?): T? {
        val localFacing = when {
            facing == null -> null
            this is Rotatable -> (this as Rotatable).toLocal(facing)
            else -> facing
        }
        val superResult = if (super.hasCapability(capability, facing)) super.getCapability(capability, facing) else null
        if (superResult != null) return superResult

        for (component in components()) {
            if (component != null && component is ICapabilityProvider && component.hasCapability(capability, localFacing)) {
                return component.getCapability(capability, localFacing)
            }
        }
        return null
    }

    override fun writeToNBTForClient(nbt: NBTTagCompound) {
        connectComponents()
        super.writeToNBTForClient(nbt)
        save(nbt)
    }

    @SideOnly(Side.CLIENT)
    override fun readFromNBTForClient(nbt: NBTTagCompound) {
        super.readFromNBTForClient(nbt)
        load(nbt)
        connectComponents()
    }
}
