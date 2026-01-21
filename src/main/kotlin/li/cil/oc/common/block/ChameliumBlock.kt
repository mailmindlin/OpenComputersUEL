package li.cil.oc.common.block

import net.minecraft.block.material.Material
import net.minecraft.block.properties.PropertyEnum
import net.minecraft.block.state.BlockStateContainer
import net.minecraft.block.state.IBlockState
import net.minecraft.item.EnumDyeColor

class ChameliumBlock : SimpleBlock(Material.ROCK) {
    companion object {
        @JvmField
        val Color: PropertyEnum<EnumDyeColor> = PropertyEnum.create("color", EnumDyeColor::class.java)
    }

    init {
        defaultState = blockState.baseState.withProperty(Color, EnumDyeColor.BLACK)
    }

    override fun damageDropped(state: IBlockState): Int = getMetaFromState(state)

    override fun getStateFromMeta(meta: Int): IBlockState =
        defaultState.withProperty(Color, EnumDyeColor.byDyeDamage(meta))

    override fun getMetaFromState(state: IBlockState): Int =
        state.getValue(Color).dyeDamage

    override fun createBlockState(): BlockStateContainer = BlockStateContainer(this, Color)

    override fun hasTileEntity(state: IBlockState): Boolean = false
}
