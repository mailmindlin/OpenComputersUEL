package li.cil.oc.common.tileentity.traits

import li.cil.oc.api.Network as ApiNetwork

interface ImmibisMicroblock : TileEntityTrait {
//    @JvmField
//    val ImmibisMicroblocks_TransformableTileEntityMarker: Any? = null

    open fun ImmibisMicroblocks_isSideOpen(side: Int): Boolean = true

    open fun ImmibisMicroblocks_onMicroblocksChanged() {
        ApiNetwork.joinOrCreateNetwork(this.asTileEntity())
    }
}
