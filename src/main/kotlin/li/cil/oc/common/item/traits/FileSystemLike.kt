package li.cil.oc.common.item.traits

import li.cil.oc.Localization
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.common.GuiType
import net.minecraft.client.util.ITooltipFlag
import li.cil.oc.common.item.data.DriveData
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumHand
import net.minecraft.world.World

interface FileSystemLike : Delegate {
    override val tooltipName: String?
        get() = null

    val kiloBytes: Int

    override fun tooltipLines(stack: ItemStack, world: World?, tooltip: MutableList<String>, flag: ITooltipFlag) {
        if (stack.hasTagCompound()) {
            val nbt = stack.tagCompound!!
            if (nbt.hasKey(Settings.namespace + "data")) {
                val data = nbt.getCompoundTag(Settings.namespace + "data")
                if (data.hasKey(Settings.namespace + "fs.label")) {
                    tooltip.add(data.getString(Settings.namespace + "fs.label"))
                }
                if (flag.isAdvanced && data.hasKey("fs")) {
                    val fsNbt = data.getCompoundTag("fs")
                    if (fsNbt.hasKey("capacity.used")) {
                        val used = fsNbt.getLong("capacity.used")
                        tooltip.add(Localization.Tooltip.DiskUsage(used, kiloBytes * 1024L))
                    }
                }
            }
            val driveData = DriveData(stack)
            tooltip.add(Localization.Tooltip.DiskMode(driveData.isUnmanaged))
            tooltip.add(Localization.Tooltip.DiskLock(driveData.lockInfo))
        }
        super.tooltipLines(stack, world, tooltip, flag)
    }

    override fun onItemRightClick(stack: ItemStack, world: World, player: EntityPlayer): ActionResult<ItemStack> {
        if (!player.isSneaking && (!stack.hasTagCompound() || !stack.tagCompound!!.hasKey(Settings.namespace + "lootFactory"))) {
            player.openGui(OpenComputers, GuiType.Drive.id, world, 0, 0, 0)
            player.swingArm(EnumHand.MAIN_HAND)
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, stack)
    }
}
