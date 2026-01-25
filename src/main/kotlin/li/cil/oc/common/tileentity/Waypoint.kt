package li.cil.oc.common.tileentity

import li.cil.oc.Settings
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Component
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.EventHandler
import li.cil.oc.common.tileentity.traits.RedstoneAware
import li.cil.oc.common.tileentity.traits.Rotatable
import li.cil.oc.common.tileentity.traits.isClient
import li.cil.oc.common.tileentity.traits.position
import li.cil.oc.server.component.result
import li.cil.oc.server.network.Waypoints
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumParticleTypes
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import li.cil.oc.common.tileentity.traits.Rotatable as TraitRotatable
import li.cil.oc.common.tileentity.traits.RedstoneAware as TraitRedstoneAware
import li.cil.oc.common.tileentity.traits.Tickable as TraitTickable

class Waypoint: TileEntityBase.TEEnvironmentBase(), TraitRotatable, TraitRedstoneAware, TraitTickable {
    @JvmField
    val node: Component = ApiNetwork.newNode(this, Visibility.Network)
        .withComponent("waypoint")
        .create()
    override fun node(): Node = node

    override val rotatableDelegate: Rotatable.RotatableDelegate = register(Rotatable::RotatableDelegate)
    override val redstoneDelegate: RedstoneAware.Delegate = register(RedstoneAware::Delegate)

    @JvmField
    var label = ""

    override val validFacings: Array<EnumFacing> = EnumFacing.values()

    // ----------------------------------------------------------------------- //

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function(): string -- Get the current label of this waypoint.""")
    fun getLabel(context: Context, args: Arguments): Array<Any?> = result(label)

    @Callback(doc = """function(value:string) -- Set the label for this waypoint.""")
    fun setLabel(context: Context, args: Arguments): Array<Any?>? {
        label = args.checkString(0).take(32)
        context.pause(0.5)
        return null
    }

    // ----------------------------------------------------------------------- //

    override fun updateEntity() {
        super<TEEnvironmentBase>.updateEntity()
        if (isClient) {
            val facing = facing()!!
            val origin = position.toVec3().add(
                facing.xOffset * 0.5,
                facing.yOffset * 0.5,
                facing.zOffset * 0.5
            )
            val dx = (world.rand.nextFloat() - 0.5f) * 0.8f
            val dy = (world.rand.nextFloat() - 0.5f) * 0.8f
            val dz = (world.rand.nextFloat() - 0.5f) * 0.8f
            val vx = (world.rand.nextFloat() - 0.5f) * 0.2f + facing.xOffset * 0.3f
            val vy = (world.rand.nextFloat() - 0.5f) * 0.2f + facing.yOffset * 0.3f - 0.5f
            val vz = (world.rand.nextFloat() - 0.5f) * 0.2f + facing.zOffset * 0.3f
            world.spawnParticle(
                EnumParticleTypes.PORTAL,
                origin.x + dx, origin.y + dy, origin.z + dz,
                vx.toDouble(), vy.toDouble(), vz.toDouble()
            )
        }
    }

    override fun initialize() {
        super<TEEnvironmentBase>.initialize()
        EventHandler.scheduleServer { Waypoints.add(this) }
    }

    override fun dispose() {
        super<TEEnvironmentBase>.dispose()
        Waypoints.remove(this)
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private val LabelTag = Settings.namespace + "label"
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super<TEEnvironmentBase>.readFromNBTForServer(nbt)
        label = nbt.getString(LabelTag)
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super<TEEnvironmentBase>.writeToNBTForServer(nbt)
        nbt.setString(LabelTag, label)
    }

    @SideOnly(Side.CLIENT)
    override fun readFromNBTForClient(nbt: NBTTagCompound) {
        super.readFromNBTForClient(nbt)
        label = nbt.getString(LabelTag)
    }

    override fun writeToNBTForClient(nbt: NBTTagCompound) {
        super.writeToNBTForClient(nbt)
        nbt.setString(LabelTag, label)
    }
}
