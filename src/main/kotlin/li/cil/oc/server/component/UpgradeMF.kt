package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.driver.DriverBlock
import li.cil.oc.api.network.*
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.common.event.BlockChangeHandler
import li.cil.oc.common.event.BlockChangeHandler.ChangeListener
import li.cil.oc.server.network.NetworkObject as ServerNetwork
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedWorld.getTileEntity
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.Vec3d

/**
 * Mostly stolen from [li.cil.oc.common.tileentity.Adapter]
 *
 * @author Sangar, Vexatos
 */
class UpgradeMF(
    val host: EnvironmentHost,
    val coord: BlockPosition,
    val dir: EnumFacing
) : ManagedEnvironmentKt(), ChangeListener, DeviceInfo {
    override val node = Network.newNode(this, Visibility.None)
        .withConnector()
        .create()

    private var otherEnv: Environment? = null
    private var otherDrv: Pair<ManagedEnvironment, DriverBlock>? = null
    private var blockData: BlockData? = null

    override fun canUpdate(): Boolean = true

    override val deviceInfo = mapOf(
        DeviceAttribute.Class to DeviceClass.Bus,
        DeviceAttribute.Description to "Remote Adapter",
        DeviceAttribute.Vendor to Constants.DeviceInfo.Scummtech,
        DeviceAttribute.Product to "ERR NAME NOT FOUND"
    )

    private fun otherNode(tile: TileEntity, f: (Node) -> Unit) {
        ServerNetwork.getNetworkNode(tile, dir)?.let { otherNode ->
            f(otherNode)
        }
    }

    private fun updateBoundState() {
        val coordWorld = coord.world
        if (node != null && node.network() != null && coordWorld != null &&
            coordWorld.provider.dimension == host.world.provider.dimension &&
            coord.toVec3().distanceTo(Vec3d(host.xPosition, host.yPosition, host.zPosition)) <= Settings.get.mfuRange
        ) {
            when (val te = host.world().getTileEntity(coord)) {
                is Environment -> {
                    // Remove any previous environment
                    (otherEnv as? TileEntity)?.let { envTile ->
                        otherNode(envTile) { node.disconnect(it) }
                    }
                    otherEnv = te
                    // Remove any driver that might be there.
                    otherDrv?.let { (environment, _) ->
                        node.disconnect(environment.node())
                        blockData?.let { environment.save(it.data) }
                        environment.node()?.remove()
                    }
                    otherDrv = null
                    otherNode(te) { node.connect(it) }
                }
                else -> {
                    // Remove any environment that might have been there.
                    (otherEnv as? TileEntity)?.let { envTile ->
                        otherNode(envTile) { node.disconnect(it) }
                    }
                    otherEnv = null
                    val world = coord.world!!
                    val newDriver = Driver.driverFor(world, coord.toBlockPos(), dir)
                    when {
                        newDriver != null -> {
                            val (oldEnvironment, driver) = otherDrv ?: (null to null)
                            if (oldEnvironment != null && newDriver != driver) {
                                // This is... odd. Maybe moved by some other mod? First, clean up.
                                otherDrv = null
                                blockData = null
                                node.disconnect(oldEnvironment.node())

                                // Then rebuild - if we have something.
                                val environment = newDriver.createEnvironment(world, coord.toBlockPos(), dir)
                                if (environment != null) {
                                    otherDrv = environment to newDriver
                                    blockData = BlockData(environment.javaClass.name, NBTTagCompound())
                                    node.connect(environment.node())
                                }
                            } else if (oldEnvironment == null) {
                                // A challenger appears. Maybe.
                                val environment = newDriver.createEnvironment(world, coord.toBlockPos(), dir)
                                if (environment != null) {
                                    otherDrv = environment to newDriver
                                    val currentBlockData = blockData
                                    if (currentBlockData != null && currentBlockData.name == environment.javaClass.name) {
                                        environment.load(currentBlockData.data)
                                    }
                                    blockData = BlockData(environment.javaClass.name, NBTTagCompound())
                                    node.connect(environment.node())
                                }
                            }
                        }
                        else -> {
                            otherDrv?.let { (environment, _) ->
                                // We had something there, but it's gone now...
                                node.disconnect(environment.node())
                                blockData?.let { environment.save(it.data) }
                                environment.node()?.remove()
                                otherDrv = null
                            }
                        }
                    }
                }
            }
        }
    }

    private fun disconnect() {
        (otherEnv as? TileEntity)?.let { envTile ->
            otherNode(envTile) { node.disconnect(it) }
        }
        otherEnv = null
        otherDrv?.let { (environment, _) ->
            node.disconnect(environment.node())
            blockData?.let { environment.save(it.data) }
            environment.node()?.remove()
        }
        otherDrv = null
    }

    override fun onBlockChanged() {
        updateBoundState()
    }

    override fun update() {
        super.update()
        otherDrv?.let { (env, _) ->
            if (env.canUpdate()) {
                env.update()
            }
        }
        if (Settings.get.isTickMultiple(host.world)) {
            val distance = coord.toVec3().distanceTo(Vec3d(host.xPosition(), host.yPosition(), host.zPosition()))
            if (!node.tryChangeBuffer(-Settings.get.mfuCost * Settings.get.tickFrequency * distance)) {
                disconnect()
            }
        }
    }

    override fun onConnect(node: Node) {
        super.onConnect(node)
        if (node == this.node) {
            // Not checking for range yet because host may be a moving adapter, who knows?
            BlockChangeHandler.addListener(this, coord)
            updateBoundState()
        }
    }

    override fun onDisconnect(node: Node) {
        super.onDisconnect(node)
        (otherEnv as? TileEntity)?.let { env ->
            otherNode(env) { otherNode ->
                if (node == otherNode) {
                    otherEnv = null
                }
            }
        }
        otherDrv?.let { (env, _) ->
            if (node == env.node()) {
                otherDrv = null
            }
        }
        if (node == this.node) {
            BlockChangeHandler.removeListener(this)
        }
    }

    override fun load(nbt: NBTTagCompound) {
        super.load(nbt)
        val blockNbt = nbt.getCompoundTag(Settings.namespace + "adapter.block")
        if (blockNbt.hasKey("name") && blockNbt.hasKey("data")) {
            blockData = BlockData(blockNbt.getString("name"), blockNbt.getCompoundTag("data"))
        }
    }

    override fun save(nbt: NBTTagCompound) {
        super.save(nbt)
        val blockNbt = NBTTagCompound()
        blockData?.let { data ->
            otherDrv?.first?.save(data.data)
            blockNbt.setString("name", data.name)
            blockNbt.setTag("data", data.data)
        }
        nbt.setTag(Settings.namespace + "adapter.block", blockNbt)
    }

    // ----------------------------------------------------------------------- //

    private data class BlockData(val name: String, val data: NBTTagCompound)
}
