package li.cil.oc.client.renderer.block

import li.cil.oc.api.component.RackMountable
import li.cil.oc.api.event.RackMountableRenderEvent
import li.cil.oc.client.Textures
import li.cil.oc.common.block
import li.cil.oc.common.tileentity
import net.minecraft.block.state.IBlockState
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.IBakedModel
import net.minecraft.client.renderer.block.model.ItemOverrideList
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.property.IExtendedBlockState
import java.util.Collections

class ServerRackModel(val parent: IBakedModel) : SmartBlockModelBase() {
    override fun getOverrides(): ItemOverrideList = ItemOverride

    override fun getQuads(state: IBlockState?, side: EnumFacing?, rand: Long): List<BakedQuad> {
        if (state is IExtendedBlockState) {
            val tile = state.getValue(block.property.PropertyTile.Tile)
            if (tile is tileentity.Rack) {
                val facing = tile.facing
                val faces = mutableListOf<BakedQuad>()

                for (enumSide in EnumFacing.values()) {
                    if (enumSide != facing) {
                        faces.addAll(bakeQuads(Case[enumSide.index], serverRackTexture, null))
                    }
                }

                val textures = serverTexture
                val defaultFront = Textures.getSprite(Textures.Block.RackFront)
                for (slot in 0 until 4) {
                    val mountable = tile.getMountable(slot)
                    if (mountable is RackMountable) {
                        val event = RackMountableRenderEvent.Block(tile, slot, tile.lastData(slot), side)
                        MinecraftForge.EVENT_BUS.post(event)
                        if (!event.isCanceled) {
                            if (event.frontTextureOverride != null) {
                                for (i in 2 until 6) {
                                    textures[i] = event.frontTextureOverride
                                }
                            } else {
                                for (i in 2 until 6) {
                                    textures[i] = defaultFront
                                }
                            }
                            faces.addAll(bakeQuads(Servers[slot], textures, null))
                        }
                    }
                }

                return faces
            }
        }
        return super.getQuads(state, side, rand)
    }

    protected val serverRackTexture: Array<out Any>
        get() = arrayOf(
            Textures.getSprite(Textures.Block.GenericTop),
            Textures.getSprite(Textures.Block.GenericTop),
            Textures.getSprite(Textures.Block.RackSide),
            Textures.getSprite(Textures.Block.RackSide),
            Textures.getSprite(Textures.Block.RackSide),
            Textures.getSprite(Textures.Block.RackSide)
        )

    protected val serverTexture: Array<Any>
        get() = arrayOf(
            Textures.getSprite(Textures.Block.GenericTop),
            Textures.getSprite(Textures.Block.GenericTop),
            Textures.getSprite(Textures.Block.RackFront),
            Textures.getSprite(Textures.Block.RackFront),
            Textures.getSprite(Textures.Block.RackFront),
            Textures.getSprite(Textures.Block.RackFront)
        )

    protected val Case = arrayOf(
        makeBox(Vec3d(0 / 16.0, 0 / 16.0, 0 / 16.0), Vec3d(16 / 16.0, 2 / 16.0, 16 / 16.0)),
        makeBox(Vec3d(0 / 16.0, 14 / 16.0, 0 / 16.0), Vec3d(16 / 16.0, 16 / 16.0, 16 / 16.0)),
        makeBox(Vec3d(0 / 16.0, 2 / 16.0, 0 / 16.0), Vec3d(16 / 16.0, 14 / 16.0, 0.99 / 16.0)),
        makeBox(Vec3d(0 / 16.0, 2 / 16.0, 15.01 / 16.0), Vec3d(16 / 16.0, 14 / 16.0, 16 / 16.0)),
        makeBox(Vec3d(0 / 16.0, 2 / 16.0, 0 / 16.0), Vec3d(0.99 / 16.0, 14 / 16.0, 16 / 16.0)),
        makeBox(Vec3d(15.01 / 16.0, 2 / 16.0, 0 / 16.0), Vec3d(16 / 16.0, 14 / 16.0, 16 / 16.0))
    )

    protected val Servers = arrayOf(
        makeBox(Vec3d(0.5 / 16.0, 11 / 16.0, 0.5 / 16.0), Vec3d(15.5 / 16.0, 14 / 16.0, 15.5 / 16.0)),
        makeBox(Vec3d(0.5 / 16.0, 8 / 16.0, 0.5 / 16.0), Vec3d(15.5 / 16.0, 11 / 16.0, 15.5 / 16.0)),
        makeBox(Vec3d(0.5 / 16.0, 5 / 16.0, 0.5 / 16.0), Vec3d(15.5 / 16.0, 8 / 16.0, 15.5 / 16.0)),
        makeBox(Vec3d(0.5 / 16.0, 2 / 16.0, 0.5 / 16.0), Vec3d(15.5 / 16.0, 5 / 16.0, 15.5 / 16.0))
    )

    object ItemOverride : ItemOverrideList(Collections.emptyList()) {
        override fun handleItemState(originalModel: IBakedModel, stack: ItemStack, world: World?, entity: EntityLivingBase?): IBakedModel {
            return (originalModel as ServerRackModel).parent
        }
    }
}
