package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.network.Node
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.tileentity.traits.Colored
import li.cil.oc.common.tileentity.traits.Environment
import li.cil.oc.common.tileentity.traits.ImmibisMicroblock
import li.cil.oc.common.tileentity.traits.NotAnalyzable
import li.cil.oc.common.block.Cable as BlockCable
import li.cil.oc.util.Color
import li.cil.oc.util.ItemColorizer
import net.minecraft.item.EnumDyeColor
import net.minecraft.item.ItemStack

class Cable : TileEntityBase(), Environment, NotAnalyzable, ImmibisMicroblock, Colored {
    @JvmField
    val node: Node = ApiNetwork.newNode(this, Visibility.None).create()

    override fun getNode(): Node = node

    init {
        setColor(Color.rgbValues(EnumDyeColor.SILVER).toInt())
    }

    fun createItemStack(): ItemStack {
        val stack = ApiItems.get(Constants.BlockName.Cable).createItemStack(1)
        if (color != Color.rgbValues(EnumDyeColor.SILVER).toInt()) {
            ItemColorizer.setColor(stack, color)
        }
        return stack
    }

    fun fromItemStack(stack: ItemStack) {
        if (ItemColorizer.hasColor(stack)) {
            setColor(ItemColorizer.getColor(stack))
        }
    }

    override fun controlsConnectivity(): Boolean = true

    override fun consumesDye(): Boolean = true

    override fun onColorChanged() {
        super.onColorChanged()
        if (world != null && isServer) {
            ApiNetwork.joinOrCreateNetwork(this)
        }
    }

    override fun getRenderBoundingBox() = BlockCable.bounds(world, pos).offset(x.toDouble(), y.toDouble(), z.toDouble())
}
