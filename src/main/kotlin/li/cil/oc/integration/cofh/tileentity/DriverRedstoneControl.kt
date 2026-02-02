package li.cil.oc.integration.cofh.tileentity

import cofh.api.tileentity.IRedstoneControl
import cofh.api.tileentity.IRedstoneControl.ControlMode
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class DriverRedstoneControl : DriverSidedTileEntity() {
    override fun getTileEntityClass(): Class<*> = IRedstoneControl::class.java

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing)
        = (world.getTileEntity(pos) as? IRedstoneControl)?.let(::Environment)

    class Environment(tileEntity: IRedstoneControl) : ManagedTileEntityEnvironment<IRedstoneControl>(tileEntity, "redstone_control") {
        @Callback(doc = "function():boolean --  Returns whether the control is disabled.")
        fun getControlDisable(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(tileEntity.control == ControlMode.DISABLED)
        }

        @Callback(doc = "function():int --  Returns the control status.")
        fun getControlSetting(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(tileEntity.control.ordinal)
        }

        @Callback(doc = "function():string --  Returns the control status.")
        fun getControlSettingName(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(tileEntity.control.name)
        }

        @Callback(doc = "function(int):string --  Returns the name of the given control")
        fun getControlName(context: Context?, args: Arguments): Array<Any> {
            val m = ControlMode.values()[args.checkInteger(0)]
            return arrayOf(m.name)
        }

        @Callback(doc = "function():boolean --  Returns whether the component is powered.")
        fun isPowered(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(tileEntity.isPowered)
        }

        @Callback(doc = "function():boolean --  Sets whether the control tp disabled.")
        fun setControlDisable(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(tileEntity.setControl(ControlMode.DISABLED))
        }

        @Callback(doc = "function(state:int):boolean --  Sets the control status to the given value.")
        fun setControlSetting(context: Context?, args: Arguments): Array<Any> {
            tileEntity.setControl(if (args.isInteger(0)) {
                ControlMode.values()[args.checkInteger(0)]
            } else {
                ControlMode.valueOf(args.checkString(0))
            })

            return arrayOf(true)
        }
    }
}
