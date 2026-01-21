package li.cil.oc.common.block

import net.minecraft.block.material.Material
import net.minecraft.block.state.IBlockState

class FakeEndstone : SimpleBlock(Material.ROCK) {
    init {
        setHardness(3f)
        setResistance(15f)
    }

    override fun hasTileEntity(state: IBlockState): Boolean = false
}
