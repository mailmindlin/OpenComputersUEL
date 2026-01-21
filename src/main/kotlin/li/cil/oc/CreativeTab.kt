package li.cil.oc

import net.minecraft.creativetab.CreativeTabs
import li.cil.oc.api.Items
import net.minecraft.item.ItemStack

object CreativeTab: CreativeTabs(CreativeTabs.getNextID(), OpenComputers.Name) {
  private val stack by lazy { Items.get(Constants.BlockName.CaseTier1).createItemStack(1) }

  override fun createIcon(): ItemStack = stack

  override fun getTranslationKey(): String = getTabLabel()
}
