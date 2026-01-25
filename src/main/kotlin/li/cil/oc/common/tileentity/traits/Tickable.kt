package li.cil.oc.common.tileentity.traits

import net.minecraft.util.ITickable

interface Tickable: TileEntityTrait, ITickable {
    override fun update() {
//        asTileEntity().updateEntity()
    }
}
