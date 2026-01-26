package li.cil.oc.common.inventory

import li.cil.oc.OpenComputers
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.Driver
import li.cil.oc.api.driver.DriverItem
import li.cil.oc.api.network.Environment
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.network.Node
import li.cil.oc.api.util.Lifecycle
import li.cil.oc.integration.opencomputers.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

interface ComponentInventory : Inventory, Environment {
    open class State internal constructor() {
        internal var components: Array<ManagedEnvironment?>? = null
        internal var isSizeInventoryReady: Boolean = true
        internal val updatingComponents: MutableList<ManagedEnvironment> = mutableListOf()

        fun components(sizeInventory: Int): Array<ManagedEnvironment?> {
            if (components == null && isSizeInventoryReady) {
                components = arrayOfNulls(sizeInventory)
            }
            return components ?: emptyArray()
        }
        internal fun addUpdating(component: ManagedEnvironment) {
            assert(!updatingComponents.contains(component))
            updatingComponents.add(component)
        }
    }

    val componentInventoryDelegate: State

    val components: Array<ManagedEnvironment?>
        get() = this.componentInventoryDelegate.components(sizeInventory)

    // ----------------------------------------------------------------------- //

    abstract val host: EnvironmentHost

    // ----------------------------------------------------------------------- //

    fun updateComponents() {
        val updatingComponents = this.componentInventoryDelegate.updatingComponents
        if (updatingComponents.isNotEmpty()) {
            var i = 0
            // ArrayBuffer.foreach caches the size for performance reasons, but that
            // will cause issues if the list changed during iteration (e.g. because
            // a component removed itself / another component, such as the self-
            // destruct card from Computronics). Also, this list will generally be
            // quite short, so it won't have any noticeable impact, anyway.
            while (i < updatingComponents.size) {
                updatingComponents[i].update()
                i++
            }
        }
    }

    // ----------------------------------------------------------------------- //

    fun connectComponents() {
        val state = this.componentInventoryDelegate
        for (slot in 0 until sizeInventory) {
            if (slot >= 0 && slot < components.size) {
                val stack = getStackInSlot(slot)
                if (!stack.isEmpty && components[slot] == null && isComponentSlot(slot, stack)) {
                    val driver = Driver.driverFor(stack)
                    if (driver != null) {
                        val component = driver.createEnvironment(stack, host)
                        if (component != null) {
                            applyLifecycleState(component, Lifecycle.LifecycleState.Constructing)
                            try {
                                component.load(dataTag(driver, stack))
                            } catch (e: Throwable) {
                                OpenComputers.log.warn("An item component of type '${component.javaClass.name}' (provided by driver '${driver.javaClass.name}') threw an error while loading.", e)
                            }
                            if (component.canUpdate()) {
                                state.addUpdating(component)
                            }
                            components[slot] = component
                        }
                    }
                }
            }
        }
        // Make sure our node is connected.
        ApiNetwork.joinNewNetwork(node())
        for (component in components) {
            if (component != null) {
                applyLifecycleState(component, Lifecycle.LifecycleState.Initializing)
                connectItemNode(component.node())
                applyLifecycleState(component, Lifecycle.LifecycleState.Initialized)
            }
        }
    }

    fun disconnectComponents() {
        for (component in components) {
            if (component != null) {
                applyLifecycleState(component, Lifecycle.LifecycleState.Disposing)
                component.node()?.remove()
                applyLifecycleState(component, Lifecycle.LifecycleState.Disposed)
            }
        }
    }

    // ----------------------------------------------------------------------- //

    override fun save(nbt: NBTTagCompound) {
        saveComponents()
        super<Inventory>.save(nbt) // Save items after updating their tags.
    }

    fun saveComponents() {
        for (slot in 0 until sizeInventory) {
            val stack = getStackInSlot(slot)
            if (!stack.isEmpty) {
                if (slot >= components.size) {
                    // isSizeInventoryReady was added to resolve issues where an inventory was used before its
                    // nbt data had been parsed. See https://github.com/MightyPirates/OpenComputers/issues/2522
                    // If this error is hit again, perhaps another subtype needs to handle nbt loading like Case does
                    OpenComputers.log.error("ComponentInventory components length ${components.size} does not accommodate inventory size $sizeInventory")
                    return
                } else {
                    val component = components[slot]
                    if (component != null) {
                        // We're guaranteed to have a driver for entries.
                        val driver = Driver.driverFor(stack)
                        if (driver != null) {
                            save(component, driver, stack)
                        }
                    }
                }
            }
        }
    }

    // ----------------------------------------------------------------------- //

    override fun getInventoryStackLimit(): Int = 1

    override fun onItemAdded(slot: Int, stack: ItemStack) {
        val state = this.componentInventoryDelegate
        if (slot >= 0 && slot < components.size && isComponentSlot(slot, stack)) {
            val driver = Driver.driverFor(stack)
            if (driver != null) {
                val component = driver.createEnvironment(stack, host)
                if (component != null) {
                    synchronized(this) {
                        components[slot] = component
                        applyLifecycleState(component, Lifecycle.LifecycleState.Constructing)
                        try {
                            component.load(dataTag(driver, stack))
                        } catch (e: Throwable) {
                            OpenComputers.log.warn("An item component of type '${component.javaClass.name}' (provided by driver '${driver.javaClass.name}') threw an error while loading.", e)
                        }
                        if (component.canUpdate()) {
                            state.addUpdating(component)
                        }
                        applyLifecycleState(component, Lifecycle.LifecycleState.Initializing)
                        connectItemNode(component.node())
                        applyLifecycleState(component, Lifecycle.LifecycleState.Initialized)
                        save(component, driver, stack)
                    }
                }
            }
        }
    }

    override fun onItemRemoved(slot: Int, stack: ItemStack) {
        if (slot >= 0 && slot < components.size) {
            // Uninstall component previously in that slot.
            val component = components[slot]
            if (component != null) {
                synchronized(this) {
                    // Note to self: we have to remove the node from the network *before*
                    // saving, to allow file systems to close their handles before they
                    // are saved (otherwise hard drives would restore all handles after
                    // being installed into a different computer, even!)
                    components[slot] = null
                    this.componentInventoryDelegate.updatingComponents.remove(component)
                    applyLifecycleState(component, Lifecycle.LifecycleState.Disposing)
                    component.node()?.remove()
                    val driver = Driver.driverFor(stack)
                    if (driver != null) {
                        save(component, driver, stack)
                    }
                    // However, nodes then may add themselves to a network again, to
                    // ensure they have an address that gets sent to the client, used
                    // for associating some components with each other. So we do it again.
                    // TODO Should be possible to avoid this with lifecycle state now.
                    component.node()?.remove()
                    applyLifecycleState(component, Lifecycle.LifecycleState.Disposed)
                }
            }
        }
    }

    open fun isComponentSlot(slot: Int, stack: ItemStack): Boolean = true

    fun connectItemNode(node: Node?) {
        val myNode = node()
        if (myNode != null && node != null) {
            myNode.connect(node)
        }
    }

    companion object {
        @JvmStatic
        fun dataTag(driver: DriverItem, stack: ItemStack): NBTTagCompound =
            driver.dataTag(stack) ?: Item.dataTag(stack)

        @JvmStatic
        fun applyLifecycleState(component: Any, state: Lifecycle.LifecycleState) {
            if (component is Lifecycle) {
                component.onLifecycleStateChange(state)
            }
        }
    }

    fun save(component: ManagedEnvironment, driver: DriverItem, stack: ItemStack) {
        try {
            val tag = dataTag(driver, stack)
            // Clear the tag compound before saving to get the same behavior as
            // in tile entities (otherwise entries have to be cleared manually).
            for (key in tag.keySet.map { it as String }) {
                tag.removeTag(key)
            }
            component.save(tag)
        } catch (e: Throwable) {
            OpenComputers.log.warn("An item component of type '${component.javaClass.name}' (provided by driver '${driver.javaClass.name}') threw an error while saving.", e)
        }
    }
}
