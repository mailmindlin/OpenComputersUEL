package li.cil.oc.integration.top

import mcjty.theoneprobe.api.ITheOneProbe
import java.util.function.Function

class GetTheOneProbe: Function<ITheOneProbe, Void?> {
    override fun apply(api: ITheOneProbe): Void? {
        api.registerProvider(TOPInfoProvider())
        return null
    }
}