package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.SidedEnvironment
import li.cil.oc.common.tileentity.traits.Rotatable
import li.cil.oc.common.tileentity.traits.isServer
import li.cil.oc.util.setNewCompoundTag
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import li.cil.oc.common.tileentity.traits.Rotatable as TraitRotatable
import li.cil.oc.common.tileentity.traits.ImmibisMicroblock as TraitImmibisMicroblock

class Keyboard : TileEntityBase.TEEnvironmentBase(), TraitRotatable, TraitImmibisMicroblock, SidedEnvironment, Analyzable {
    override val validFacings: Array<EnumFacing> = EnumFacing.values()

    override val rotatableDelegate: Rotatable.RotatableDelegate = register(Rotatable::RotatableDelegate)

    @JvmField
    @Suppress("unused", "PropertyName", "SpellCheckingInspection")
    val ImmibisMicroblocks_TransformableTileEntityMarker: Any? = null

    @JvmField
    val keyboard = run {
        val keyboardItem = ApiItems.get(Constants.BlockName.Keyboard).createItemStack(1)
        Driver.driverFor(keyboardItem, javaClass).createEnvironment(keyboardItem, this)
    }

    override fun node(): Node = keyboard.node()

    fun hasNodeOnSide(side: EnumFacing): Boolean =
        side != facing() && (isOnWall || side.opposite != forward)

    // ----------------------------------------------------------------------- //

    @SideOnly(Side.CLIENT)
    override fun canConnect(side: EnumFacing): Boolean = hasNodeOnSide(side)

    override fun sidedNode(side: EnumFacing): Node? = if (hasNodeOnSide(side)) node() else null

    // Override automatic analyzer implementation for sided environments.
    override fun onAnalyze(player: EntityPlayer, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Array<Node> = arrayOf(node())

    // ----------------------------------------------------------------------- //

    companion object {
        private val KeyboardTag = Settings.namespace + "keyboard"
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)
        if (isServer) {
            keyboard.load(nbt.getCompoundTag(KeyboardTag))
        }
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)
        if (isServer) {
            nbt.setNewCompoundTag(KeyboardTag) { keyboard.save(it) }
        }
    }

    // ----------------------------------------------------------------------- //

    private val isOnWall: Boolean get() = facing() != EnumFacing.UP && facing() != EnumFacing.DOWN

    private val forward: EnumFacing? get() = if (isOnWall) EnumFacing.UP else yaw
}
