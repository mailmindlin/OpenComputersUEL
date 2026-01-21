package li.cil.oc.common.item

import net.minecraft.client.renderer.block.model.ModelResourceLocation
import net.minecraft.item.ItemStack
import net.minecraftforge.client.event.ModelBakeEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

interface CustomModel {
    @SideOnly(Side.CLIENT)
    fun getModelLocation(stack: ItemStack): ModelResourceLocation

    @SideOnly(Side.CLIENT)
    fun registerModelLocations() {}

    @SideOnly(Side.CLIENT)
    fun bakeModels(bakeEvent: ModelBakeEvent) {}
}
