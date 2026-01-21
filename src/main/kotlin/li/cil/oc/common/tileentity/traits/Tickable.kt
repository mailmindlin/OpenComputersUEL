package li.cil.oc.common.tileentity.traits

import net.minecraft.util.ITickable

interface Tickable : ITickable {
    fun updateEntity()

    override fun update() {
        updateEntity()
    }
}
