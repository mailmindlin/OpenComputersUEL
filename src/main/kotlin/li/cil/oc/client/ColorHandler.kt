package li.cil.oc.client

import li.cil.oc.Constants
import li.cil.oc.api.internal.Colored
import li.cil.oc.itemInfo
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
import li.cil.oc.common.block.Cable as BlockCable
import li.cil.oc.common.block.Case as BlockCase
import li.cil.oc.common.block.Screen as BlockScreen


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
    register(Constants.BlockInfo.Cable.block()) { state, world, pos, tintIndex ->
      when (val block = state.block) {
          is BlockCable -> block.colorMultiplierOverride?.toUInt() ?: 0xFFFFFFFFu
          else -> 0xFFFF_FFFFu
      }
    }

    register(
      Constants.BlockInfo.CaseTier1.block(),
      Constants.BlockInfo.CaseTier2.block(),
      Constants.BlockInfo.CaseTier3.block(),
      Constants.BlockInfo.CaseCreative.block(),
      handler = colorByTier<BlockCase>(BlockCase::tier)
    )

    register(
      Constants.BlockInfo.ChameliumBlock.block()
    ) { state, _, _, _ ->
      Color.rgbValues(Color.byOreName[Color.dyes[state.block.getMetaFromState(state).coerceIn(0..<Color.dyes.size)]]!!)
    }

    register(
      Constants.BlockInfo.Print.block()
    ) { _, _, _, tintIndex -> tintIndex.toUInt() }

    register(
      Constants.BlockInfo.ScreenTier1.block(),
      Constants.BlockInfo.ScreenTier2.block(),
      Constants.BlockInfo.ScreenTier3.block(),
      handler = colorByTier<BlockScreen>(BlockScreen::tier)
    )

    register(
      Item.getItemFromBlock(Constants.BlockInfo.Cable.block()),
    ) { stack, tintIndex -> if (ItemColorizer.hasColor(stack)) ItemColorizer.getColor(stack).toUInt() else tintIndex.toUInt() }

    register(
      Item.getItemFromBlock(Constants.BlockInfo.CaseTier1.block()),
      Item.getItemFromBlock(Constants.BlockInfo.CaseTier2.block()),
      Item.getItemFromBlock(Constants.BlockInfo.CaseTier3.block()),
      Item.getItemFromBlock(Constants.BlockInfo.CaseCreative.block()),
    ) { stack, tintIndex -> Color.rgbValues(Color.byTier[ItemUtils.caseTier(stack)])  }

    register(
      Item.getItemFromBlock(Constants.BlockInfo.ChameliumBlock.block())
    ) { stack, tintIndex -> Color.rgbValues[EnumDyeColor.byDyeDamage(stack.itemDamage)]!! }

    register(
      Item.getItemFromBlock(Constants.BlockInfo.ScreenTier1.block()),
      Item.getItemFromBlock(Constants.BlockInfo.ScreenTier2.block()),
      Item.getItemFromBlock(Constants.BlockInfo.ScreenTier3.block()),
      Item.getItemFromBlock(Constants.BlockInfo.Print.block()),
      Item.getItemFromBlock(Constants.BlockInfo.Robot.block())
    ) { stack, tintIndex -> tintIndex.toUInt() }

    register(
      Constants.ItemInfo.HoverBoots.item()
    ) { stack, tintIndex ->
      if (tintIndex == 1) {
        if (ItemColorizer.hasColor(stack)) ItemColorizer.getColor(stack).toUInt() else 0x66DD55u
      } else 0xFFFFFFu
    }
  }

  fun register(vararg blocks: Block, handler: (IBlockState, IBlockAccess?, BlockPos?, Int) -> UInt) {
    val bch = IBlockColor { state, world, pos, tintIndex -> handler(state, world, pos, tintIndex).toInt() }
    Minecraft.getMinecraft().blockColors.registerBlockColorHandler(bch, *blocks)
  }

  fun register(vararg items: Item, handler: (ItemStack, Int) -> UInt) {
    val ich = object : IItemColor {
      override fun colorMultiplier(stack: ItemStack, tintIndex: Int): Int = handler(stack, tintIndex).toInt()
    }
    Minecraft.getMinecraft().itemColors.registerItemColorHandler(ich, *items)
  }
}
