package li.cil.oc.common.tileentity

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.common.SaveHandler
import li.cil.oc.common.tileentity.behaviors.Behavior
import li.cil.oc.common.tileentity.behaviors.BehaviorContainer
import li.cil.oc.common.tileentity.traits.Environment
import li.cil.oc.common.tileentity.traits.TileEntityTrait
import li.cil.oc.common.tileentity.traits.isServer
import net.minecraft.block.state.IBlockState
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.NetworkManager
import net.minecraft.network.play.server.SPacketUpdateTileEntity
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

internal inline fun <T: TileEntityBase, U: Behavior> T.register(f: (T) -> U): U
    = behaviors.register(f(this))

abstract class TileEntityBase : TileEntity(), TileEntityTrait {

    /**
     * Container for all behaviors registered by this tile entity.
     * Subclasses register behaviors in configureBehaviors().
     */
    val behaviors = BehaviorContainer(this)

    override fun asTileEntity(): TileEntity = this

    // ----------------------------------------------------------------------- //
    // Lifecycle
    // ----------------------------------------------------------------------- //

    override fun validate() {
        super.validate()
        initialize()
    }

    override fun invalidate() {
        super.invalidate()
        dispose()
    }

    override fun onChunkUnload() {
        super.onChunkUnload()
        try {
            dispose()
        } catch (t: Throwable) {
            OpenComputers.log.error("Failed properly disposing a tile entity, things may leak and or break.", t)
        }
    }

    protected open fun initialize() {
        // Initialize all behaviors
        behaviors.initialize()
    }

    open fun dispose() {
        // Dispose all behaviors in reverse order
        behaviors.dispose()

        // Client-side sound cleanup is handled by subclasses or event handlers
    }

    open fun updateEntity() {
        // Update all behaviors
        behaviors.update()

        if (Settings.get.periodicallyForceLightUpdate &&
            world.totalWorldTime % 40 == 0L &&
            getBlockType().getLightValue(world.getBlockState(pos), world, pos) > 0) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3)
        }
    }

    // ----------------------------------------------------------------------- //
    // Block refresh
    // ----------------------------------------------------------------------- //

    override fun shouldRefresh(world: World, pos: BlockPos, oldState: IBlockState, newState: IBlockState): Boolean =
        oldState.block != newState.block

    // ----------------------------------------------------------------------- //
    // NBT Serialization
    // ----------------------------------------------------------------------- //

    open fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBT(nbt)
        // Read all behaviors' server-side state
        behaviors.readFromNBTForServer(nbt)
    }

    open fun writeToNBTForServer(nbt: NBTTagCompound) {
        nbt.setBoolean(TileEntityTrait.IsServerDataTag, true)
        super.writeToNBT(nbt)
        // Write all behaviors' server-side state
        behaviors.writeToNBTForServer(nbt)
    }

    @SideOnly(Side.CLIENT)
    open fun readFromNBTForClient(nbt: NBTTagCompound) {
        // Read all behaviors' client-side state
        behaviors.readFromNBTForClient(nbt)
    }

    open fun writeToNBTForClient(nbt: NBTTagCompound) {
        nbt.setBoolean(TileEntityTrait.IsServerDataTag, false)
        // Write all behaviors' client-side state
        behaviors.writeToNBTForClient(nbt)
    }

    override fun hasCapability(capability: Capability<*>, facing: EnumFacing?): Boolean
        = super.hasCapability(capability, facing) || behaviors.hasCapability(capability, facing)

    override fun <T : Any?> getCapability(capability: Capability<T>, facing: EnumFacing?): T?
        = super.getCapability(capability, facing) ?: behaviors.getCapability(capability, facing)

    // ----------------------------------------------------------------------- //
    // NBT Dispatch
    // ----------------------------------------------------------------------- //

    override fun readFromNBT(nbt: NBTTagCompound) {
        if (isServer || nbt.getBoolean(TileEntityTrait.IsServerDataTag)) {
            readFromNBTForServer(nbt)
        } else {
            readFromNBTForClient(nbt)
        }
    }

    override fun writeToNBT(nbt: NBTTagCompound): NBTTagCompound {
        if (isServer) {
            writeToNBTForServer(nbt)
        }
        return nbt
    }

    // ----------------------------------------------------------------------- //
    // Network Packets
    // ----------------------------------------------------------------------- //

    override fun getUpdatePacket(): SPacketUpdateTileEntity {
        return SPacketUpdateTileEntity(pos, blockMetadata, updateTag)
    }

    override fun getUpdateTag(): NBTTagCompound {
        val nbt = super.getUpdateTag()

        // See comment on savingForClients variable.
        SaveHandler.savingForClients = true
        try {
            try {
                writeToNBTForClient(nbt)
            } catch (e: Throwable) {
                OpenComputers.log.warn("There was a problem writing a TileEntity description packet. Please report this if you see it!", e)
            }
        } finally {
            SaveHandler.savingForClients = false
        }

        return nbt
    }

    override fun onDataPacket(manager: NetworkManager, packet: SPacketUpdateTileEntity) {
        try {
            readFromNBTForClient(packet.nbtCompound)
        } catch (e: Throwable) {
            OpenComputers.log.warn("There was a problem reading a TileEntity description packet. Please report this if you see it!", e)
        }
    }

    abstract class TEEnvironmentBase: TileEntityBase(), Environment {
        override val environmentDelegate: Environment.Delegate = register(Environment::Delegate)
    }
}
