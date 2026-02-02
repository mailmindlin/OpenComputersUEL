package li.cil.oc.integration.cofh.tileentity

import cofh.api.core.ISecurable
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.fml.common.FMLCommonHandler
import org.apache.commons.lang3.text.WordUtils

internal class EnvironmentSecureTile(tileEntity: ISecurable) : ManagedTileEntityEnvironment<ISecurable>(tileEntity, "secure_tile") {
    @Callback(doc = "function(name:string):boolean --  Returns whether the player with the given name can access the component")
    fun canPlayerAccess(context: Context?, args: Arguments): Array<Any> {
        val server = FMLCommonHandler.instance().minecraftServerInstance
        val player: EntityPlayer? = server.playerList.getPlayerByUsername(args.checkString(0))
        return arrayOf(player != null && tileEntity.canPlayerAccess(player))
    }

    @Callback(doc = "function():string --  Returns the type of the access.")
    fun getAccess(context: Context?, args: Arguments?): Array<Any> {
        return arrayOf(WordUtils.capitalize(tileEntity!!.access.name))
    }

    @Callback(doc = "function():string --  Returns the name of the owner.")
    fun getOwnerName(context: Context?, args: Arguments?): Array<Any> {
        return arrayOf(tileEntity!!.ownerName)
    }
}
/*class DriverSecureTile : DriverSidedTileEntity() {
    override fun getTileEntityClass(): Class<*> {
        return ISecurable::class.java
    }

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing): ManagedEnvironment {
        return Environment(world.getTileEntity(pos) as ISecurable?)
    }


}
*/