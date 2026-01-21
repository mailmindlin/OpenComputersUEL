package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.SidedEnvironment
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

class Keyboard : traits.Environment(), traits.Rotatable, traits.ImmibisMicroblock, SidedEnvironment, Analyzable {
    override val validFacings: Array<EnumFacing> = EnumFacing.values()

    @JvmField
    val keyboard = run {
        val keyboardItem = api.Items.get(Constants.BlockName.Keyboard).createItemStack(1)
        api.Driver.driverFor(keyboardItem, javaClass).createEnvironment(keyboardItem, this)
    }

    override fun getNode(): Node = keyboard.node()

    fun hasNodeOnSide(side: EnumFacing): Boolean =
        side != facing && (isOnWall || side.opposite != forward)

    // ----------------------------------------------------------------------- //

    @SideOnly(Side.CLIENT)
    override fun canConnect(side: EnumFacing): Boolean = hasNodeOnSide(side)

    override fun sidedNode(side: EnumFacing): Node? = if (hasNodeOnSide(side)) node else null

    // Override automatic analyzer implementation for sided environments.
    override fun onAnalyze(player: EntityPlayer, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Array<Node> = arrayOf(node)

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

    private val isOnWall: Boolean get() = facing != EnumFacing.UP && facing != EnumFacing.DOWN

    private val forward: EnumFacing get() = if (isOnWall) EnumFacing.UP else yaw
}
