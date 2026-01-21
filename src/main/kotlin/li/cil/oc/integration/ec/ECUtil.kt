package li.cil.oc.integration.ec

import appeng.api.AEApi
import appeng.api.storage.data.IAEFluidStack
import extracells.api.ECApi
import extracells.api.gas.IAEGasStack
import extracells.api.gas.IGasStorageChannel

object ECUtil {
    @JvmField
    val isGasSystemEnabled = ECApi.instance().isGasSystemEnabled

    @JvmField
    val gasStorageChannel = if (isGasSystemEnabled) {
        AEApi.instance().storage().getStorageChannel(IGasStorageChannel::class.java)
    } else {
        null
    }

    @JvmStatic
    fun canSeeFluidInNetwork(fluid: IAEFluidStack?): Boolean {
        return fluid != null && ECApi.instance().canFluidSeeInTerminal(fluid.fluid)
    }
}
