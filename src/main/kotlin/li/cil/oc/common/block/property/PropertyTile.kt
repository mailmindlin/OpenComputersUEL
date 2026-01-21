package li.cil.oc.common.block.property

import net.minecraft.tileentity.TileEntity
import net.minecraftforge.common.property.IUnlistedProperty

// Custom unlisted property used to pass a long tile entities to a block's renderer.
object PropertyTile : IUnlistedProperty<TileEntity> {
    override fun getName(): String = "tile"

    override fun isValid(value: TileEntity): Boolean = true

    override fun getType(): Class<TileEntity> = TileEntity::class.java

    override fun valueToString(value: TileEntity): String = value.toString()
}
