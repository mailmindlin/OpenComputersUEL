package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.common.event.ChunkloaderUpgradeHandler
import net.minecraft.entity.Entity
import net.minecraftforge.common.ForgeChunkManager
import net.minecraftforge.common.ForgeChunkManager.Ticket

sealed class UpgradeChunkloader(val host: EnvironmentHost) : ManagedEnvironmentKt(), DeviceInfoKt {
    override val node = Network.newNode(this, Visibility.Network)
        .withComponent("chunkloader")
        .withConnector()
        .create()

    override val deviceInfo = mapOf(
        DeviceAttribute.Class to DeviceClass.Generic,
        DeviceAttribute.Description to "World stabilizer",
        DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
        DeviceAttribute.Product to "Realizer9001-CL"
    )

    var ticket: Ticket? = null

    override fun canUpdate(): Boolean = true

    override fun update() {
        super.update()
        if (host.world.totalWorldTime % Settings.get.tickFrequency.toInt() == 0L && ticket != null) {
            if (!node.tryChangeBuffer(-Settings.get.chunkloaderCost * Settings.get.tickFrequency)) {
                ticket?.let { t ->
                    try {
                        ForgeChunkManager.releaseTicket(t)
                    } catch (e: Throwable) {
                        // Ignored.
                    }
                }
                ticket = null
            } else if (host is Entity) { // Robot move events are not fired for entities (drones)
                ChunkloaderUpgradeHandler.updateLoadedChunk(this)
            }
        }
    }

    @Callback(doc = "function():boolean -- Gets whether the chunkloader is currently active.")
    fun isActive(context: Context, args: Arguments): Array<Any?> = result(ticket != null)

    @Callback(doc = "function(enabled:boolean):boolean -- Enables or disables the chunkloader, returns true if active changed")
    fun setActive(context: Context, args: Arguments): Array<Any?> =
        result(setActive(args.checkBoolean(0), throwIfBlocked = true))

    override fun onConnect(node: Node) {
        super.onConnect(node)
        if (node == this.node) {
            val restoredTicket = ChunkloaderUpgradeHandler.restoredTickets.remove(node.address())
            if (restoredTicket != null) {
                if (!isDimensionAllowed) {
                    try {
                        ForgeChunkManager.releaseTicket(restoredTicket)
                    } catch (e: Throwable) {
                        // Ignored.
                    }
                    OpenComputers.log.info("Releasing chunk loader ticket at (${host.xPosition()}, ${host.yPosition()}, ${host.zPosition()}) in blacklisted dimension ${host.world().provider.dimension}.")
                } else {
                    OpenComputers.log.info("Reclaiming chunk loader ticket at (${host.xPosition()}, ${host.yPosition()}, ${host.zPosition()}) in dimension ${host.world().provider.dimension}.")
                    ticket = restoredTicket
                    ChunkloaderUpgradeHandler.updateLoadedChunk(this)
                }
            } else if (host is Context) {
                val ctx = host as Context
                if (ctx.isRunning) {
                    requestTicket()
                }
            }
        }
    }

    override fun onDisconnect(node: Node) {
        super.onDisconnect(node)
        if (node == this.node) {
            ticket?.let { t ->
                try {
                    ForgeChunkManager.releaseTicket(t)
                } catch (e: Throwable) {
                    // Ignored.
                }
            }
            ticket = null
        }
    }

    override fun onMessage(message: Message) {
        super.onMessage(message)
        when (message.name()) {
            "computer.stopped" -> setActive(enabled = false)
            "computer.started" -> setActive(enabled = true)
        }
    }

    private fun setActive(enabled: Boolean, throwIfBlocked: Boolean = false): Boolean {
        return if (enabled && ticket == null) {
            requestTicket(throwIfBlocked)
            ticket != null
        } else if (!enabled && ticket != null) {
            ticket?.let { t ->
                try {
                    ForgeChunkManager.releaseTicket(t)
                } catch (e: Throwable) {
                    // Ignored.
                }
            }
            ticket = null
            true
        } else {
            false
        }
    }

    private val isDimensionAllowed: Boolean
        get() {
            val id: Int = host.world().provider.dimension
            val whitelist = Settings.get.chunkloadDimensionWhitelist
            val blacklist = Settings.get.chunkloadDimensionBlacklist
            if (whitelist.isNotEmpty()) {
                if (!whitelist.contains(id))
                    return false
            }
            if (blacklist.isNotEmpty()) {
                if (blacklist.contains(id)) {
                    return false
                }
            }
            return true
        }

    private fun requestTicket(throwIfBlocked: Boolean = false) {
        if (!isDimensionAllowed) {
            if (throwIfBlocked) {
                throw Exception("this dimension is blacklisted")
            }
        } else {
            ticket = ForgeChunkManager.requestTicket(OpenComputers, host.world, ForgeChunkManager.Type.NORMAL)
            ChunkloaderUpgradeHandler.updateLoadedChunk(this)
        }
    }
}
