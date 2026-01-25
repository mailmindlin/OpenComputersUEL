package li.cil.oc.common.tileentity.traits

import li.cil.oc.api.driver.DriverItem
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.network.Node
import li.cil.oc.common.EventHandler
import li.cil.oc.common.tileentity.behaviors.Behavior
import li.cil.oc.common.tileentity.behaviors.BehaviorLifecycle
import li.cil.oc.common.tileentity.behaviors.NbtSeriailzable
import li.cil.oc.common.inventory.ComponentInventory as InventoryComponentInventory
import li.cil.oc.util.StackOption
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ICapabilityProvider
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

interface ComponentInventory : Environment, Inventory, InventoryComponentInventory {
    // ----------------------------------------------------------------------- //
    override val componentInventoryDelegate: Delegate

    class Delegate(val tile: ComponentInventory): InventoryComponentInventory.State(), Behavior, BehaviorLifecycle, NbtSeriailzable {
        // Cache changes to inventory slots on the client side to avoid recreating
        // components when we don't have to and the slots are just cleared by MC
        // temporarily.
        private val pendingRemovalsActual: MutableList<ItemStack?> by lazy {
            MutableList(tile.sizeInventory) { null }
        }
        private val pendingAddsActual: MutableList<ItemStack?> by lazy {
            MutableList(tile.sizeInventory) { null }
        }
        private var updateScheduled = false

        val pendingRemovals: MutableList<ItemStack?>
            get() {
                adjustSize(pendingRemovalsActual)
                return pendingRemovalsActual
            }

        val pendingAdds: MutableList<ItemStack?>
            get() {
                adjustSize(pendingAddsActual)
                return pendingAddsActual
            }

        private fun adjustSize(buffer: MutableList<ItemStack?>) {
            val delta = buffer.size - tile.sizeInventory
            if (delta > 0) {
                for (i in 0 until delta) {
                    buffer.removeAt(buffer.size - 1)
                }
            } else if (delta < 0) {
                for (i in 0 until -delta) {
                    buffer.add(null)
                }
            }
        }

        fun getSizeInventory() = tile.sizeInventory

        private fun applyInventoryChanges() {
            updateScheduled = false
            for (slot in 0 until getSizeInventory()) {
                val removed = pendingRemovals[slot]
                val added = pendingAdds[slot]
                when {
                    removed != null && added != null -> {
                        if (!removed.isItemEqual(added) || !ItemStack.areItemStackTagsEqual(removed, added)) {
                            super.onItemRemoved(slot, removed)
                            super.onItemAdded(slot, added)
                            tile.markDirty()
                        }
                        // else: No change, ignore.
                    }
                    removed != null && added == null -> {
                        super.onItemRemoved(slot, removed)
                        tile.markDirty()
                    }
                    removed == null && added != null -> {
                        super.onItemAdded(slot, added)
                        tile.markDirty()
                    }
                    // No change.
                }

                pendingRemovals[slot] = null
                pendingAdds[slot] = null
            }
        }

        private fun scheduleInventoryChange() {
            if (!updateScheduled) {
                updateScheduled = true
                EventHandler.scheduleClient { applyInventoryChanges() }
            }
        }

        override fun writeToNBTForClient(nbt: NBTTagCompound) {
            tile.connectComponents()
            super.writeToNBTForClient(nbt)
            tile.save(nbt)
        }

        @SideOnly(Side.CLIENT)
        override fun readFromNBTForClient(nbt: NBTTagCompound) {
            super.readFromNBTForClient(nbt)
            tile.load(nbt)
            tile.connectComponents()
        }

        override fun initialize() {
            super.initialize()
            if (tile.isClient) {
                tile.connectComponents()
            }
        }

        override fun dispose() {
            super.dispose()
            if (tile.isClient) {
                tile.disconnectComponents()
            }
        }

        fun hasCapability(capability: Capability<*>, facing: EnumFacing?): Boolean {
            val localFacing = when {
                facing == null -> null
                this is Rotatable -> (this as Rotatable).toLocal(facing)
                else -> facing
            }
            return super.hasCapability(capability, facing) || components().any { component ->
                component != null && component is ICapabilityProvider && component.hasCapability(capability, localFacing)
            }
        }

        fun <T> getCapability(capability: Capability<T>, facing: EnumFacing?): T? {
            val localFacing = when {
                facing == null -> null
                tile is Rotatable -> (tile as Rotatable).toLocal(facing)
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

    override val host: EnvironmentHost
        get() = this

    // ----------------------------------------------------------------------- //

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
}
