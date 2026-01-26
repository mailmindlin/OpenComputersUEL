package li.cil.oc.common.event

import li.cil.oc.OpenComputers
import li.cil.oc.api.event.RobotMoveEvent
import li.cil.oc.server.component.UpgradeChunkloader
import li.cil.oc.util.BlockPosition
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.World
import net.minecraftforge.common.ForgeChunkManager
import net.minecraftforge.common.ForgeChunkManager.LoadingCallback
import net.minecraftforge.common.ForgeChunkManager.Ticket
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object ChunkloaderUpgradeHandler : LoadingCallback {
    @JvmField
    val restoredTickets = mutableMapOf<String, Ticket>()

    override fun ticketsLoaded(tickets: MutableList<Ticket>, world: World) {
        for (ticket in tickets) {
            val data = ticket.modData
            val address = data.getString("address")
            restoredTickets[address] = ticket
            if (data.hasKey("x") && data.hasKey("z")) {
                val x = data.getInteger("x")
                val z = data.getInteger("z")
                OpenComputers.log.info("Restoring chunk loader ticket for upgrade at chunk ($x, $z) with address $address.")

                ForgeChunkManager.forceChunk(ticket, ChunkPos(x, z))
            }
        }
    }

    @JvmStatic
    @SubscribeEvent
    fun onWorldSave(e: WorldEvent.Save) {
        // Any tickets that were not reassigned by the time the world gets saved
        // again can be considered orphaned, so we release them.
        // TODO figure out a better event *after* tile entities were restored
        // but *before* the world is saved, because the tickets are saved first,
        // so if the save is because the game is being quit the tickets aren't
        // actually being cleared. This will *usually* not be a problem, but it
        // has room for improvement.
        restoredTickets.values.forEach { ticket ->
            try {
                val data = ticket.modData
                OpenComputers.log.warn("A chunk loader ticket has been orphaned! Address: ${data.getString("address")}, position: (${data.getInteger("x")}, ${data.getInteger("z")}). Removing...")
                ForgeChunkManager.releaseTicket(ticket)
            } catch (_: Throwable) {
                // Ignored.
            }
        }
        restoredTickets.clear()
    }

    // Note: it might be necessary to use pre move to force load the target chunk
    // in case the robot moves across a chunk border into an otherwise unloaded
    // chunk (I think it would just fail to move otherwise).
    // Update 2014-06-21: did some testing, seems not to be necessary. My guess
    // is that the access to the block in the direction the robot moves causes
    // the chunk it might move into to get loaded.

    @JvmStatic
    @SubscribeEvent
    fun onMove(e: RobotMoveEvent.Post) {
        val machineNode = e.agent.machine().node()!!
        machineNode.reachableNodes().forEach { node ->
            val host = node.host()
            if (host is UpgradeChunkloader) {
                updateLoadedChunk(host)
            }
        }
    }

    @JvmStatic
    fun updateLoadedChunk(loader: UpgradeChunkloader) {
        val blockPos = BlockPosition(loader.host)
        val centerChunk = ChunkPos(blockPos.x shr 4, blockPos.z shr 4)
        val robotChunks = (-1..1).flatMap { x ->
            (-1..1).map { z ->
                ChunkPos(centerChunk.x + x, centerChunk.z + z)
            }
        }.toSet()

        loader.ticket?.let { ticket ->
            ticket.chunkList.filterIsInstance<ChunkPos>()
                .filter { chunk -> !robotChunks.contains(chunk) }
                .forEach { chunk -> ForgeChunkManager.unforceChunk(ticket, chunk) }

            for (chunk in robotChunks) {
                ForgeChunkManager.forceChunk(ticket, chunk)
            }

            ticket.modData.setString("address", loader.node().address())
            ticket.modData.setInteger("x", centerChunk.x)
            ticket.modData.setInteger("z", centerChunk.z)
        }
    }
}
