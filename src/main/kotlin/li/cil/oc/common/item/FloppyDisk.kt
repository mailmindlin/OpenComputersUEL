package li.cil.oc.common.item

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.FileSystemLike
import li.cil.oc.util.Color
import net.minecraft.client.renderer.block.model.ModelBakery
import net.minecraft.client.renderer.block.model.ModelResourceLocation
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import kotlin.math.max
import kotlin.math.min

open class FloppyDisk(parent: Delegator) : AbstractDelegate(parent), CustomModel, FileSystemLike {
    // Necessary for anonymous subclasses used for loot disks.
    override val unlocalizedName: String = "floppydisk"

    override val kiloBytes: Int = Settings.get.floppySize

    @SideOnly(Side.CLIENT)
    private fun modelLocationFromDyeName(name: String): ModelResourceLocation =
        ModelResourceLocation(Settings.resourceDomain + ":" + Constants.ItemName.Floppy + "_" + name, "inventory")

    @SideOnly(Side.CLIENT)
    override fun getModelLocation(stack: ItemStack): ModelResourceLocation {
        val dyeIndex = if (stack.hasTagCompound() && stack.tagCompound!!.hasKey(Settings.namespace + "color"))
            stack.tagCompound!!.getInteger(Settings.namespace + "color")
        else
            8
        return modelLocationFromDyeName(Color.dyes[max(0, min(15, dyeIndex))])
    }

    @SideOnly(Side.CLIENT)
    override fun registerModelLocations() {
        for (dyeName in Color.dyes) {
            val location = modelLocationFromDyeName(dyeName)
            ModelBakery.registerItemVariants(parent, ResourceLocation(location.namespace + ":" + location.path))
        }
    }

    override fun doesSneakBypassUse(world: IBlockAccess, pos: BlockPos, player: EntityPlayer): Boolean = true
}
