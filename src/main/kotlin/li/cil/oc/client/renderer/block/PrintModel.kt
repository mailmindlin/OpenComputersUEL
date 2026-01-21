package li.cil.oc.client.renderer.block

import com.google.common.base.Strings
import li.cil.oc.Settings
import li.cil.oc.client.KeyBindings
import li.cil.oc.client.Textures
import li.cil.oc.common.block
import li.cil.oc.common.item.data.PrintData
import li.cil.oc.common.tileentity
import li.cil.oc.util.Color
import li.cil.oc.util.ExtendedAABB
import li.cil.oc.util.ExtendedAABB.AABBExtensions
import net.minecraft.block.state.IBlockState
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.IBakedModel
import net.minecraft.client.renderer.block.model.ItemOverrideList
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.EnumDyeColor
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.world.World
import net.minecraftforge.common.property.IExtendedBlockState
import java.util.Collections

object PrintModel : SmartBlockModelBase() {
    override fun getOverrides(): ItemOverrideList = ItemOverride

    override fun getQuads(state: IBlockState?, side: EnumFacing?, rand: Long): List<BakedQuad> {
        if (state is IExtendedBlockState) {
            val tile = state.getValue(block.property.PropertyTile.Tile)
            if (tile is tileentity.Print) {
                val faces = mutableListOf<BakedQuad>()

                for (shape in tile.shapes) {
                    if (!Strings.isNullOrEmpty(shape.texture)) {
                        val bounds = shape.bounds.rotateTowards(tile.facing)
                        val texture = resolveTexture(shape.texture)
                        faces.addAll(bakeQuads(makeBox(bounds.min, bounds.max), Array(6) { texture }, shape.tint ?: White))
                    }
                }

                return faces
            }
        }
        return super.getQuads(state, side, rand)
    }

    private fun resolveTexture(name: String): Any {
        val texture = Textures.getSprite(name)
        return if (texture.iconName == "missingno") Textures.getSprite("minecraft:blocks/$name")
        else texture
    }

    class ItemModel(val stack: ItemStack) : SmartBlockModelBase() {
        val data = PrintData(stack)

        override fun getQuads(state: IBlockState?, side: EnumFacing?, rand: Long): List<BakedQuad> {
            val faces = mutableListOf<BakedQuad>()

            val shapes =
                if (data.hasActiveState && KeyBindings.showExtendedTooltips)
                    data.stateOn
                else
                    data.stateOff
            for (shape in shapes) {
                val bounds = shape.bounds
                val texture = resolveTexture(shape.texture)
                faces.addAll(bakeQuads(makeBox(bounds.min, bounds.max), Array(6) { texture }, shape.tint ?: White))
            }
            if (shapes.isEmpty()) {
                val bounds = ExtendedAABB.unitBounds
                val texture = resolveTexture("${Settings.resourceDomain}:blocks/white")
                faces.addAll(bakeQuads(makeBox(bounds.min, bounds.max), Array(6) { texture }, Color.rgbValues(EnumDyeColor.LIME)))
            }

            return faces
        }
    }

    object ItemOverride : ItemOverrideList(Collections.emptyList()) {
        override fun handleItemState(originalModel: IBakedModel, stack: ItemStack, world: World?, entity: EntityLivingBase?): IBakedModel {
            return ItemModel(stack)
        }
    }
}
