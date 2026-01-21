package li.cil.oc.common.launch

import com.google.common.eventbus.EventBus
import net.minecraftforge.fml.common.DummyModContainer
import net.minecraftforge.fml.common.LoadController
import net.minecraftforge.fml.common.ModMetadata

class CoreModContainer : DummyModContainer(ModMetadata().apply {
    authorList.add("Sangar")
    modId = "opencomputers|core"
    version = "@VERSION@"
    name = "OpenComputers (Core)"
    url = "https://oc.cil.li/"
    description = "OC core mod used for class transformer and as API owner to avoid cyclic dependencies."
}) {
    override fun registerBus(bus: EventBus, controller: LoadController): Boolean = true
}
