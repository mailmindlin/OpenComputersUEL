package li.cil.oc.client.renderer.block

import li.cil.oc.Constants
import li.cil.oc.common.init.Items
import li.cil.oc.client.Textures
import li.cil.oc.common.Tier
import li.cil.oc.common.block.property.PropertyTile
import li.cil.oc.common.block.Screen
import li.cil.oc.common.tileentity.Screen as TileEntityScreen
import li.cil.oc.util.Color
import net.minecraft.block.state.IBlockState
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.IBakedModel
import net.minecraft.client.renderer.block.model.ItemOverrideList
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.world.World
import net.minecraftforge.common.property.IExtendedBlockState
import java.util.Collections

object ScreenModel : SmartBlockModelBase() {
    override fun getOverrides(): ItemOverrideList = ItemOverride

    override fun getQuads(state: IBlockState?, side: EnumFacing?, rand: Long): List<BakedQuad> {
        val safeSide = side ?: EnumFacing.SOUTH
        if (state is IExtendedBlockState) {
            val tile = state.getValue(PropertyTile)
            if (tile is TileEntityScreen) {
                val facing = tile.toLocal(safeSide)!!

                val (x, y) = tile.localPosition()
                var px = xy2part(x, tile.width - 1)
                var py = xy2part(y, tile.height - 1)
                if ((safeSide == EnumFacing.DOWN || tile.facing() == EnumFacing.DOWN) && safeSide != tile.facing()) {
                    px = 2 - px
                    py = 2 - py
                }
                val rotation =
                    if (safeSide == EnumFacing.UP) tile.yaw?.horizontalIndex ?: 0
                    else if (safeSide == EnumFacing.DOWN) -(tile.yaw?.horizontalIndex ?: 0)
                    else 0

                fun pitch() = if (tile.pitch == EnumFacing.NORTH) 0 else 1
                val texture =
                    if (tile.width == 1 && tile.height == 1) {
                        if (facing == EnumFacing.SOUTH)
                            Textures.Block.Screen.SingleFront[pitch()]
                        else
                            Textures.Block.Screen.Single[safeSide.ordinal]
                    } else if (tile.width == 1) {
                        if (facing == EnumFacing.SOUTH)
                            Textures.Block.Screen.VerticalFront[pitch()][py]
                        else
                            Textures.Block.Screen.Vertical[pitch()][py][facing.ordinal]
                    } else if (tile.height == 1) {
                        if (facing == EnumFacing.SOUTH)
                            Textures.Block.Screen.HorizontalFront[pitch()][px]
                        else
                            Textures.Block.Screen.Horizontal[pitch()][px][facing.ordinal]
                    } else {
                        if (facing == EnumFacing.SOUTH)
                            Textures.Block.Screen.MultiFront[pitch()][py][px]
                        else
                            Textures.Block.Screen.Multi[pitch()][py][px][facing.ordinal]
                    }

                return listOf(bakeQuad(safeSide, Textures.getSprite(texture), tile.color, rotation))
            }
        }
        return super.getQuads(state, safeSide, rand)
    }

    private fun xy2part(value: Int, high: Int) = if (value == 0) 2 else if (value == high) 0 else 1

    class ItemModel(val stack: ItemStack) : SmartBlockModelBase() {
        val color = when (val block = Items.get(stack)?.block()) {
            is Screen -> Color.byTier[block.tier]
            else -> Color.byTier[Tier.One]
        }

        override fun getQuads(state: IBlockState?, side: EnumFacing?, rand: Long): List<BakedQuad> {
            val result =
                if (side == EnumFacing.NORTH || side == null)
                    Textures.Block.Screen.SingleFront[0]
                else
                    Textures.Block.Screen.Single[side.ordinal]
            return listOf(bakeQuad(side ?: EnumFacing.SOUTH, Textures.getSprite(result), Color.rgbValues(color).toInt() ?: 0xFFFFFF, 0))
        }
    }

    object ItemOverride : ItemOverrideList(Collections.emptyList()) {
        override fun handleItemState(originalModel: IBakedModel, stack: ItemStack, world: World?, entity: EntityLivingBase?): IBakedModel {
            return ItemModel(stack)
        }
    }
}
