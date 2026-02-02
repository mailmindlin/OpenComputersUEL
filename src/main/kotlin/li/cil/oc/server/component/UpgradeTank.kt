package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.internal.Agent
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.FluidTank
import net.minecraftforge.fluids.FluidTankInfo
import net.minecraftforge.fluids.IFluidTank

class UpgradeTank(val owner: EnvironmentHost, capacity: Int) : ManagedEnvironmentKt(), IFluidTank, DeviceInfo {
    override val node = nodeFactory(Visibility.None).create()

    private val deviceInfo_ by lazy {
        mapOf(
            DeviceAttribute.Class to DeviceClass.Generic,
            DeviceAttribute.Description to "Tank upgrade",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "Superblubb V10",
            DeviceAttribute.Capacity to tank.capacity.toString()
        )
    }

    override fun getDeviceInfo() = deviceInfo_

    // ----------------------------------------------------------------------- //

    val tank = FluidTank(capacity)

    override fun load(nbt: NBTTagCompound) {
        super.load(nbt)
        tank.readFromNBT(nbt)
    }

    override fun save(nbt: NBTTagCompound) {
        super.save(nbt)
        tank.writeToNBT(nbt)
    }

    // ----------------------------------------------------------------------- //

    override fun getFluid(): FluidStack? = tank.fluid

    override fun getFluidAmount(): Int = tank.fluidAmount

    override fun getCapacity(): Int = tank.capacity

    override fun getInfo(): FluidTankInfo = tank.info

    override fun fill(stack: FluidStack?, doFill: Boolean): Int {
        val amount = tank.fill(stack, doFill)
        if (doFill && amount > 0) {
            node!!.sendToVisible("computer.signal", "tank_changed", tankIndex, amount)
        }
        return amount
    }

    override fun drain(maxDrain: Int, doDrain: Boolean): FluidStack? {
        val amount = tank.drain(maxDrain, doDrain)
        if (doDrain && amount != null && amount.amount > 0) {
            node!!.sendToVisible("computer.signal", "tank_changed", tankIndex, -amount.amount)
        }
        return amount
    }

    private val tankIndex: Int
        get() {
            return when (owner) {
                is Agent -> {
                    val agent = owner as Agent
                    if (agent.tank() != null) {
                        val tanks = (0 until agent.tank().tankCount()).map { agent.tank().getFluidTank(it) }
                        val index = tanks.indexOf(this)
                        maxOf(index, 0) + 1
                    } else {
                        1
                    }
                }
                else -> 1
            }
        }
}
