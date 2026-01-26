package li.cil.oc.common.item.traits

import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.api.driver.DriverItem
import li.cil.oc.common.item.Delegator
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.Rarity
import li.cil.oc.util.Tooltip
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.EnumAction
import net.minecraft.item.EnumRarity
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

interface Delegate {
    val parent: Delegator

    val unlocalizedName: String
        get() = javaClass.simpleName.lowercase()

    val tooltipName: String?
        get() = unlocalizedName

    val tooltipData: Array<Any>
        get() = emptyArray()

    var showInItemList: Boolean

    val itemId: Int

    val maxStackSize: Int
        get() = 64

    fun createItemStack(amount: Int = 1): ItemStack = ItemStack(parent, amount, itemId)

    // ----------------------------------------------------------------------- //

    fun doesSneakBypassUse(world: IBlockAccess, pos: BlockPos, player: EntityPlayer): Boolean = false

    fun onItemUseFirst(stack: ItemStack, player: EntityPlayer, position: BlockPosition, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): EnumActionResult = EnumActionResult.PASS

    fun onItemUse(stack: ItemStack, player: EntityPlayer, position: BlockPosition, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean = false

    fun onItemRightClick(stack: ItemStack, world: World, player: EntityPlayer): ActionResult<ItemStack> = ActionResult.newResult(EnumActionResult.PASS, stack)

    fun getItemUseAction(stack: ItemStack): EnumAction = EnumAction.NONE

    fun getMaxItemUseDuration(stack: ItemStack): Int = 0

    fun onItemUseFinish(stack: ItemStack, world: World, player: EntityLivingBase): ItemStack = stack

    fun onPlayerStoppedUsing(stack: ItemStack, player: EntityLivingBase, duration: Int) {}

    fun update(stack: ItemStack, world: World, player: Entity, slot: Int, selected: Boolean) {}

    // ----------------------------------------------------------------------- //

    fun rarity(stack: ItemStack): EnumRarity = Rarity.byTier(tierFromDriver(stack))

    fun tierFromDriver(stack: ItemStack): Int {
        val driver = Driver.driverFor(stack)
        return if (driver is DriverItem) driver.tier(stack) else 0
    }

    fun color(stack: ItemStack, pass: Int): Int = 0xFFFFFF

    fun getContainerItem(stack: ItemStack): ItemStack = ItemStack.EMPTY

    fun hasContainerItem(stack: ItemStack): Boolean = false

    fun displayName(stack: ItemStack): String? = null

    @SideOnly(Side.CLIENT)
    fun tooltipLines(stack: ItemStack, world: World?, tooltip: MutableList<String>, flag: ITooltipFlag) {
        val name = tooltipName
        if (name != null) {
            tooltip.addAll(Tooltip.get(name, *tooltipData))
            tooltipExtended(stack, tooltip)
        }
        tooltipCosts(stack, tooltip)
    }

    // For stuff that goes to the normal 'extended' tooltip, before the costs.
    fun tooltipExtended(stack: ItemStack, tooltip: MutableList<String>) {}

    fun tooltipCosts(stack: ItemStack, tooltip: MutableList<String>) {
        if (stack.hasTagCompound() && stack.tagCompound!!.hasKey(Settings.namespace + "data")) {
            val data = stack.tagCompound!!.getCompoundTag(Settings.namespace + "data")
            if (data.hasKey("node") && data.getCompoundTag("node").hasKey("address")) {
                tooltip.add("\u00a78${data.getCompoundTag("node").getString("address").substring(0, 13)}...\u00a77")
            }
        }
    }

    fun showDurabilityBar(stack: ItemStack): Boolean = false

    fun durability(stack: ItemStack): Double = 0.0
}