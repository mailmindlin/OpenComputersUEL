package li.cil.oc.common.component

import java.util.UUID
import java.util.EnumSet

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.component.RackBusConnectable
import li.cil.oc.api.component.RackMountable
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.internal.Keyboard as InternalKeyboard
import li.cil.oc.api.internal.Rack as InternalRack
import li.cil.oc.api.internal.TextBuffer as InternalTextBuffer
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network.Environment
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.util.Lifecycle
import li.cil.oc.api.util.StateAware
import li.cil.oc.api.util.StateAware.State
import li.cil.oc.common.Tier
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.item.Terminal
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagString
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraftforge.common.util.Constants.NBT

class TerminalServer(val rack: InternalRack, val slot: Int) : Environment, EnvironmentHost, Analyzable, RackMountable, Lifecycle, DeviceInfo {

    override val node: Node = ApiNetwork.newNode(this, Visibility.None).create()

    val buffer: InternalTextBuffer by lazy {
        val screenItem = ApiItems.get(Constants.BlockName.ScreenTier1).createItemStack(1)
        val buf = Driver.driverFor(screenItem, javaClass).createEnvironment(screenItem, this) as InternalTextBuffer
        val (maxWidth, maxHeight) = Settings.screenResolutionsByTier(Tier.Three)
        buf.setMaximumResolution(maxWidth, maxHeight)
        buf.setMaximumColorDepth(Settings.screenDepthsByTier(Tier.Three))
        buf
    }

    val keyboard: InternalKeyboard by lazy {
        val keyboardItem = ApiItems.get(Constants.BlockName.Keyboard).createItemStack(1)
        val kbd = Driver.driverFor(keyboardItem, javaClass).createEnvironment(keyboardItem, this) as InternalKeyboard
        kbd.setUsableOverride(object : InternalKeyboard.UsabilityChecker {
            override fun isUsableByPlayer(keyboard: InternalKeyboard, player: EntityPlayer): Boolean {
                val stack = player.heldItemMainhand
                val subItem = Delegator.subItem(stack)
                return if (subItem is Terminal && stack.hasTagCompound()) {
                    sidedKeys.contains(stack.tagCompound!!.getString(Settings.namespace + "key"))
                } else {
                    false
                }
            }
        })
        kbd
    }

    var range: Double = Settings.get.maxWirelessRange(Tier.Two)
    val keys: MutableList<String> = mutableListOf()

    fun hasAddress(): Boolean {
        if (rack != null) {
            val data = rack.getMountableData(slot)
            if (data != null) {
                return data.hasKey("terminalAddress")
            }
        }
        return false
    }

    val address: String get() = rack.getMountableData(slot).getString("terminalAddress")

    val sidedKeys: List<String> get() {
        return if (!rack.world().isRemote) {
            keys
        } else {
            val tagList = rack.getMountableData(slot).getTagList("keys", NBT.TAG_STRING)
            (0 until tagList.tagCount()).map { i -> (tagList[i] as NBTTagString).string }
        }
    }

    // ----------------------------------------------------------------------- //
    // DeviceInfo

    private val deviceInfo: Map<String, String> by lazy {
        mapOf(
            DeviceAttribute.Class.toString() to DeviceClass.Generic.toString(),
            DeviceAttribute.Description.toString() to "Terminal server",
            DeviceAttribute.Vendor.toString() to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product.toString() to "RemoteViewing EX"
        )
    }

    override fun getDeviceInfo(): java.util.Map<String, String> = deviceInfo as java.util.Map<String, String>

    // ----------------------------------------------------------------------- //
    // Environment

    override fun onConnect(node: Node) {
        if (node == this.node) {
            node.connect(buffer.node())
            node.connect(keyboard.node())
            buffer.node().connect(keyboard.node())
        }
    }

    override fun onDisconnect(node: Node) {
        if (node == this.node) {
            buffer.node().remove()
            keyboard.node().remove()
        }
    }

    override fun onMessage(message: Message) {
    }

    // ----------------------------------------------------------------------- //
    // EnvironmentHost

    override fun world() = rack.world()

    override fun xPosition() = rack.xPosition()

    override fun yPosition() = rack.yPosition()

    override fun zPosition() = rack.zPosition()

    override fun markChanged() = rack.markChanged()

    // ----------------------------------------------------------------------- //
    // RackMountable

    override fun getData(): NBTTagCompound {
        if (node.address() == null) ApiNetwork.joinNewNetwork(node)

        val nbt = NBTTagCompound()
        nbt.extendedNBT().setNewTagList("keys", keys)
        nbt.setString("terminalAddress", node.address())
        return nbt
    }

    override fun getConnectableCount(): Int = 0

    override fun getConnectableAt(index: Int): RackBusConnectable? = null

    override fun onActivate(player: EntityPlayer, hand: EnumHand, heldItem: ItemStack, hitX: Float, hitY: Float): Boolean {
        if (ApiItems.get(heldItem) == ApiItems.get(Constants.ItemName.Terminal)) {
            if (!world().isRemote) {
                val key = UUID.randomUUID().toString()
                if (!heldItem.hasTagCompound()) {
                    heldItem.tagCompound = NBTTagCompound()
                } else {
                    keys.remove(heldItem.tagCompound!!.getString(Settings.namespace + "key"))
                }
                val maxSize = Settings.get.terminalsPerServer
                while (keys.size >= maxSize) {
                    keys.removeAt(0)
                }
                keys.add(key)
                heldItem.tagCompound!!.setString(Settings.namespace + "key", key)
                heldItem.tagCompound!!.setString(Settings.namespace + "server", node.address())
                rack.markChanged(slot)
                player.inventory.markDirty()
            }
            return true
        }
        return false
    }

    // ----------------------------------------------------------------------- //
    // Persistable

    private val BufferTag = Settings.namespace + "buffer"
    private val KeyboardTag = Settings.namespace + "keyboard"
    private val KeysTag = Settings.namespace + "keys"

    override fun load(nbt: NBTTagCompound) {
        if (!rack.world().isRemote) {
            node.load(nbt)
        }
        buffer.load(nbt.getCompoundTag(BufferTag))
        keyboard.load(nbt.getCompoundTag(KeyboardTag))
        keys.clear()
        val tagList = nbt.getTagList(KeysTag, NBT.TAG_STRING)
        for (i in 0 until tagList.tagCount()) {
            keys.add((tagList[i] as NBTTagString).string)
        }
    }

    override fun save(nbt: NBTTagCompound) {
        node.save(nbt)
        nbt.extendedNBT().setNewCompoundTag(BufferTag) { buffer.save(it) }
        nbt.extendedNBT().setNewCompoundTag(KeyboardTag) { keyboard.save(it) }
        nbt.extendedNBT().setNewTagList(KeysTag, keys)
    }

    // ----------------------------------------------------------------------- //
    // ManagedEnvironment

    override fun canUpdate(): Boolean = true

    override fun update() {
        if (world().isRemote || (node.address() != null && node.network() != null)) {
            buffer.update()
        }
    }

    // ----------------------------------------------------------------------- //
    // StateAware

    override fun getCurrentState(): EnumSet<State> {
        return EnumSet.noneOf(StateAware.State::class.java)
    }

    // ----------------------------------------------------------------------- //
    // Analyzable

    override fun onAnalyze(player: EntityPlayer, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Array<Node> = arrayOf(buffer.node(), keyboard.node())

    // ----------------------------------------------------------------------- //
    // LifeCycle

    override fun onLifecycleStateChange(state: Lifecycle.LifecycleState) {
        if (rack.world().isRemote) {
            when (state) {
                Lifecycle.LifecycleState.Initialized -> Companion.loaded.add(this)
                Lifecycle.LifecycleState.Disposed -> Companion.loaded.remove(this)
                else -> { /* Ignore */ }
            }
        }
    }

    companion object {
        @JvmField
        val loaded = TerminalServerCache()
    }
}

// we need a smart cache because nodes are loaded in before they have addresses
// and we need a unique set of terminal servers based on address
// This cache acts as a Map[address: String, term: TerminalServer]
// But it can store terminals before they have an address
// Null-address terminals are not available for binding
// As an address loads, repeated addresses are dropped from the list
class TerminalServerCache {

    private val ready: MutableMap<String, TerminalServer> = mutableMapOf()
    private val pending: MutableList<TerminalServer> = mutableListOf()

    private fun completePending() {
        val promoted: MutableList<TerminalServer> = mutableListOf()
        for (term in pending) {
            if (term.hasAddress()) {
                promoted.add(term)
            }
        }
        for (term in promoted) {
            pending.remove(term)
            val address = term.address
            if (!ready.containsKey(address)) {
                ready[address] = term
            }
        }
    }

    fun add(terminal: TerminalServer): Boolean {
        completePending()
        return if (terminal.hasAddress()) {
            val newAddress: String = terminal.address
            if (ready.containsKey(newAddress)) {
                false
            } else {
                ready[newAddress] = terminal
                true
            }
        } else {
            pending.add(terminal)
            true
        }
    }

    fun remove(terminal: TerminalServer): Boolean {
        completePending()
        return if (terminal.hasAddress()) {
            ready.remove(terminal.address) != null
        } else {
            val before = pending.size
            pending.remove(terminal)
            pending.size > before
        }
    }

    fun clear() {
        ready.clear()
        pending.clear()
    }

    fun find(address: String): TerminalServer? {
        completePending()
        return ready[address]
    }
}
