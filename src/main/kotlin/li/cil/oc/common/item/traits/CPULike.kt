package li.cil.oc.common.item.traits

import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.api.Machine as ApiMachine
import li.cil.oc.api.driver.item.MutableProcessor
import li.cil.oc.integration.opencomputers.DriverCPU
import li.cil.oc.util.Tooltip
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumHand
import net.minecraft.util.text.TextComponentTranslation
import net.minecraft.world.World

interface CPULike : Delegate {
    val cpuTier: Int

    override val tooltipData: Array<Any>
        get() = arrayOf(Settings.get.cpuComponentSupport[cpuTier])

    override fun tooltipExtended(stack: ItemStack, tooltip: MutableList<String>) {
        tooltip.addAll(Tooltip.get("cpu.Architecture", ApiMachine.getArchitectureName(DriverCPU.architecture(stack))))
    }

    override fun onItemRightClick(stack: ItemStack, world: World, player: EntityPlayer): ActionResult<ItemStack> {
        if (player.isSneaking) {
            if (!world.isRemote) {
                val driver = Driver.driverFor(stack)
                if (driver is MutableProcessor) {
                    val architectures = driver.allArchitectures().toList()
                    if (architectures.isNotEmpty()) {
                        val currentIndex = architectures.indexOf(driver.architecture(stack))
                        val newIndex = (currentIndex + 1) % architectures.size
                        val archClass = architectures[newIndex]
                        val archName = ApiMachine.getArchitectureName(archClass)
                        driver.setArchitecture(stack, archClass)
                        player.sendMessage(TextComponentTranslation(Settings.namespace + "tooltip.cpu.Architecture", archName))
                    }
                    player.swingArm(EnumHand.MAIN_HAND)
                }
            }
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, stack)
    }
}
