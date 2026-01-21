package li.cil.oc.client

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.common.block.Cable as BlockCable
import li.cil.oc.common.block.Case as BlockCase
import li.cil.oc.common.block.Screen as BlockScreen
// import li.cil.oc.common.block
import li.cil.oc.api.internal.Colored
import li.cil.oc.util.Color
import li.cil.oc.util.ItemColorizer
import li.cil.oc.util.ItemUtils
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.color.IBlockColor
import net.minecraft.client.renderer.color.IItemColor
import net.minecraft.item.EnumDyeColor
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess


private inline fun <reified T> colorByTier(crossinline extract: T.() -> Int): (IBlockState, IBlockAccess?, BlockPos?, Int) -> UInt {
  return { state, world, pos, tintIndex ->
    if (pos == null) 0xFFFFFFFFu else when (val te = world!!.getTileEntity(pos)) {
      is Colored -> te.color.toUInt()
      else -> when (val block = state.block) {
        is T -> Color.rgbValues(Color.byTier[block.extract()])
        else -> 0xFFFFFFFFu
      }
    }
  }
}
object ColorHandler {
  @OptIn(ExperimentalStdlibApi::class)
  fun init() {
    register(ApiItems.get(Constants.BlockName.Cable).block()) { state, world, pos, tintIndex ->
      when (val block = state.block) {
          is BlockCable -> block.colorMultiplierOverride?.toUInt() ?: 0xFFFFFFFFu
          else -> 0xFFFF_FFFFu
      }
    };

    register(
      ApiItems.get(Constants.BlockName.CaseTier1).block(),
      ApiItems.get(Constants.BlockName.CaseTier2).block(),
      ApiItems.get(Constants.BlockName.CaseTier3).block(),
      ApiItems.get(Constants.BlockName.CaseCreative).block(),
      handler = colorByTier<BlockCase>(BlockCase::tier)
    )

    register(
      ApiItems.get(Constants.BlockName.ChameliumBlock).block()
    ) { state, _, _, _ ->
      Color.rgbValues(Color.byOreName[Color.dyes[state.block.getMetaFromState(state).coerceIn(0..<Color.dyes.size)]]!!)
    }

    register(
      ApiItems.get(Constants.BlockName.Print).block()
    ) { _, _, _, tintIndex -> tintIndex.toUInt() }

    register(
      ApiItems.get(Constants.BlockName.ScreenTier1).block(),
      ApiItems.get(Constants.BlockName.ScreenTier2).block(),
      ApiItems.get(Constants.BlockName.ScreenTier3).block(),
      handler = colorByTier<BlockScreen>(BlockScreen::tier)
    )

    register(
      Item.getItemFromBlock(ApiItems.get(Constants.BlockName.Cable).block()),
    ) { stack, tintIndex -> if (ItemColorizer.hasColor(stack)) ItemColorizer.getColor(stack).toUInt() else tintIndex.toUInt() }

    register(
      Item.getItemFromBlock(ApiItems.get(Constants.BlockName.CaseTier1).block()),
      Item.getItemFromBlock(ApiItems.get(Constants.BlockName.CaseTier2).block()),
      Item.getItemFromBlock(ApiItems.get(Constants.BlockName.CaseTier3).block()),
      Item.getItemFromBlock(ApiItems.get(Constants.BlockName.CaseCreative).block()),
    ) { stack, tintIndex -> Color.rgbValues(Color.byTier[ItemUtils.caseTier(stack)])  }

    register(
      Item.getItemFromBlock(ApiItems.get(Constants.BlockName.ChameliumBlock).block())
    ) { stack, tintIndex -> Color.rgbValues[EnumDyeColor.byDyeDamage(stack.itemDamage)]!! }

    register(
      Item.getItemFromBlock(ApiItems.get(Constants.BlockName.ScreenTier1).block()),
      Item.getItemFromBlock(ApiItems.get(Constants.BlockName.ScreenTier2).block()),
      Item.getItemFromBlock(ApiItems.get(Constants.BlockName.ScreenTier3).block()),
      Item.getItemFromBlock(ApiItems.get(Constants.BlockName.Print).block()),
      Item.getItemFromBlock(ApiItems.get(Constants.BlockName.Robot).block())
    ) { stack, tintIndex -> tintIndex.toUInt() }

    register(
      ApiItems.get(Constants.ItemName.HoverBoots).item()
    ) { stack, tintIndex ->
      if (tintIndex == 1) {
        if (ItemColorizer.hasColor(stack)) ItemColorizer.getColor(stack).toUInt() else 0x66DD55u
      } else 0xFFFFFFu
    }
  }

  fun register(vararg blocks: Block, handler: (IBlockState, IBlockAccess?, BlockPos?, Int) -> UInt) {
    val bch = object : IBlockColor {
      override fun colorMultiplier(state: IBlockState, world: IBlockAccess?, pos: BlockPos?, tintIndex: Int): Int = handler(state, world, pos, tintIndex).toInt()
    };
    Minecraft.getMinecraft().blockColors.registerBlockColorHandler(bch, *blocks)
  }

  fun register(vararg items: Item, handler: (ItemStack, Int) -> UInt) {
    val ich = object : IItemColor {
      override fun colorMultiplier(stack: ItemStack, tintIndex: Int): Int = handler(stack, tintIndex).toInt()
    }
    Minecraft.getMinecraft().itemColors.registerItemColorHandler(ich, *items)
  }
}
