package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.network.Node
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.tileentity.traits.*
import li.cil.oc.common.block.Cable as BlockCable
import li.cil.oc.util.Color
import li.cil.oc.util.ItemColorizer
import li.cil.oc.util.rgbValue
import net.minecraft.item.EnumDyeColor
import net.minecraft.item.ItemStack
import net.minecraft.util.math.AxisAlignedBB

class Cable : TileEntityBase.TEEnvironmentBase(), Environment, NotAnalyzable, ImmibisMicroblock, Colored {
    @JvmField
    val node: Node = ApiNetwork.newNode(this, Visibility.None).create()
    override fun node(): Node = node

    @JvmField
    @Suppress("unused", "PropertyName", "SpellCheckingInspection")
    val ImmibisMicroblocks_TransformableBlockMarker: Any? = null

    override val colorDelegate: Colored.Delegate = Colored.Delegate(this)

    init {
        behaviors.register(colorDelegate)
        setColor(Color.rgbValues(EnumDyeColor.SILVER).toInt())
    }

    fun createItemStack(): ItemStack {
        val stack = ApiItems.get(Constants.BlockName.Cable).createItemStack(1)
        if (color != EnumDyeColor.SILVER.rgbValue) {
            ItemColorizer.setColor(stack, color.toInt())
        }
        return stack
    }

    fun fromItemStack(stack: ItemStack) {
        if (ItemColorizer.hasColor(stack)) {
            setColor(ItemColorizer.getColor(stack))
        }
    }

    override fun controlsConnectivity(): Boolean = true

    override val consumesDye: Boolean
        get() = true

    override fun onColorChanged() {
        super.onColorChanged()
        if (world != null && isServer) {
            ApiNetwork.joinOrCreateNetwork(this)
        }
    }

    override fun getRenderBoundingBox(): AxisAlignedBB = BlockCable.bounds(world, pos).offset(x.toDouble(), y.toDouble(), z.toDouble())
}
