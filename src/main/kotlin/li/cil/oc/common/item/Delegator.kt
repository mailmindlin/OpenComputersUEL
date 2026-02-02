package li.cil.oc.common.item

import li.cil.oc.CreativeTab
import li.cil.oc.OpenComputers
import li.cil.oc.api.driver.item.Chargeable
import li.cil.oc.api.event.RobotRenderEvent.MountPoint
import li.cil.oc.api.internal.Robot
import li.cil.oc.client.renderer.item.UpgradeRenderer
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.integration.opencomputers.Item as OpenComputersItem
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.notEmpty
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.EnumAction
import net.minecraft.item.EnumRarity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.NonNullList
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

open class Delegator : Item(), li.cil.oc.api.driver.item.UpgradeRenderer, Chargeable {
    init {
        setHasSubtypes(true)
        creativeTab = CreativeTab
    }

    // ----------------------------------------------------------------------- //
    // SubItem
    // ----------------------------------------------------------------------- //

    override fun getItemStackLimit(stack: ItemStack): Int {
        val subItem = subItem(stack)
        return if (subItem != null) {
            val address = OpenComputersItem.address(stack)
            if (address != null) 1 else subItem.maxStackSize
        } else {
            maxStackSize
        }
    }

    private val subItems: MutableList<Delegate> = mutableListOf()

    fun add(subItem: Delegate): Int {
        val itemId = subItems.size
        subItems.add(subItem)
        return itemId
    }

    fun subItem(damage: Int): Delegate? {
        return if (damage >= 0 && damage < subItems.size) subItems[damage] else null
    }

    override fun getSubItems(tab: CreativeTabs, list: NonNullList<ItemStack>) {
        // Workaround for MC's untyped lists...
        if (isInCreativeTab(tab)) {
            subItems.indices
                .filter { subItems[it].showInItemList }
                .map { subItems[it].createItemStack() }
                .sortedBy { it.translationKey }
                .forEach { list.add(it) }
        }
    }

    // ----------------------------------------------------------------------- //
    // Item
    // ----------------------------------------------------------------------- //

    override fun getTranslationKey(stack: ItemStack): String {
        val subItem = subItem(stack)
        return if (subItem != null) "item.oc.${subItem.unlocalizedName}" else translationKey
    }

    override fun isBookEnchantable(itemA: ItemStack, itemB: ItemStack): Boolean = false

    override fun getRarity(stack: ItemStack): EnumRarity {
        val subItem = subItem(stack)
        return subItem?.rarity(stack) ?: EnumRarity.COMMON
    }

    override fun getContainerItem(stack: ItemStack): ItemStack {
        val subItem = subItem(stack)
        return subItem?.getContainerItem(stack) ?: super.getContainerItem(stack)
    }

    override fun hasContainerItem(stack: ItemStack): Boolean {
        val subItem = subItem(stack)
        return subItem?.hasContainerItem(stack) ?: super.hasContainerItem(stack)
    }

    // ----------------------------------------------------------------------- //

    override fun doesSneakBypassUse(stack: ItemStack, world: IBlockAccess, pos: BlockPos, player: EntityPlayer): Boolean {
        val subItem = subItem(stack)
        return subItem?.doesSneakBypassUse(world, pos, player) ?: super.doesSneakBypassUse(stack, world, pos, player)
    }

    override fun onItemUseFirst(player: EntityPlayer, world: World, pos: BlockPos, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float, hand: EnumHand): EnumActionResult {
        val stack = player.getHeldItem(hand)
        val subItem = subItem(stack)
        return subItem?.onItemUseFirst(stack, player, BlockPosition(pos, world), side, hitX, hitY, hitZ)
            ?: super.onItemUseFirst(player, world, pos, side, hitX, hitY, hitZ, hand)
    }

    override fun onItemUse(player: EntityPlayer, world: World, pos: BlockPos, hand: EnumHand, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): EnumActionResult {
        val stack = player.getHeldItem(hand)
        val subItem = subItem(stack) ?: return super.onItemUse(player, world, pos, hand, side, hitX, hitY, hitZ)
        return if (subItem.onItemUse(stack, player, BlockPosition(pos, world), side, hitX, hitY, hitZ))
            EnumActionResult.SUCCESS
        else EnumActionResult.PASS
    }

    override fun onItemRightClick(world: World, player: EntityPlayer, hand: EnumHand): ActionResult<ItemStack> {
        val stack = player.getHeldItem(hand)
        val subItem = subItem(stack)
        return subItem?.onItemRightClick(stack, world, player) ?: super.onItemRightClick(world, player, hand)
    }

    // ----------------------------------------------------------------------- //

    override fun onItemUseFinish(stack: ItemStack, world: World, entity: EntityLivingBase): ItemStack {
        val subItem = subItem(stack)
        return subItem?.onItemUseFinish(stack, world, entity) ?: super.onItemUseFinish(stack, world, entity)
    }

    override fun getItemUseAction(stack: ItemStack): EnumAction {
        val subItem = subItem(stack)
        return subItem?.getItemUseAction(stack) ?: super.getItemUseAction(stack)
    }

    override fun getMaxItemUseDuration(stack: ItemStack): Int {
        val subItem = subItem(stack)
        return subItem?.getMaxItemUseDuration(stack) ?: super.getMaxItemUseDuration(stack)
    }

    override fun onPlayerStoppedUsing(stack: ItemStack, world: World, entity: EntityLivingBase, timeLeft: Int) {
        val subItem = subItem(stack)
        if (subItem != null) {
            subItem.onPlayerStoppedUsing(stack, entity, timeLeft)
        } else {
            super.onPlayerStoppedUsing(stack, world, entity, timeLeft)
        }
    }

    fun internalGetItemStackDisplayName(stack: ItemStack): String = super.getItemStackDisplayName(stack)

    override fun getItemStackDisplayName(stack: ItemStack): String {
        val subItem = subItem(stack)
        return if (subItem != null) {
            subItem.displayName(stack) ?: super.getItemStackDisplayName(stack)
        } else {
            super.getItemStackDisplayName(stack)
        }
    }

    @SideOnly(Side.CLIENT)
    override fun addInformation(stack: ItemStack, world: World?, tooltip: MutableList<String>, flag: ITooltipFlag) {
        super.addInformation(stack, world, tooltip, flag)
        val subItem = subItem(stack)
        if (subItem != null) {
            try {
                subItem.tooltipLines(stack, world, tooltip, flag)
            } catch (t: Throwable) {
                OpenComputers.log.warn("Error in item tooltip.", t)
            }
        }
    }

    override fun getDurabilityForDisplay(stack: ItemStack): Double {
        val subItem = subItem(stack)
        return subItem?.durability(stack) ?: super.getDurabilityForDisplay(stack)
    }

    override fun showDurabilityBar(stack: ItemStack): Boolean {
        val subItem = subItem(stack)
        return subItem?.showDurabilityBar(stack) ?: super.showDurabilityBar(stack)
    }

    override fun onUpdate(stack: ItemStack, world: World, player: Entity, slot: Int, selected: Boolean) {
        val subItem = subItem(stack)
        if (subItem != null) {
            subItem.update(stack, world, player, slot, selected)
        } else {
            super.onUpdate(stack, world, player, slot, selected)
        }
    }

    override fun toString(): String = translationKey

    // ----------------------------------------------------------------------- //

    override fun canCharge(stack: ItemStack): Boolean {
        val subItem = subItem(stack)
        return subItem is Chargeable
    }

    override fun charge(stack: ItemStack, amount: Double, simulate: Boolean): Double {
        val subItem = subItem(stack)
        return if (subItem is Chargeable) {
            subItem.charge(stack, amount, simulate)
        } else {
            amount
        }
    }

    // ----------------------------------------------------------------------- //

    override fun computePreferredMountPoint(stack: ItemStack, robot: Robot, availableMountPoints: Set<String>): String =
        UpgradeRenderer.preferredMountPoint(stack, availableMountPoints)

    override fun render(stack: ItemStack, mountPoint: MountPoint, robot: Robot, pt: Float) =
        UpgradeRenderer.render(stack, mountPoint)

    companion object {
        @JvmStatic
        internal fun subItem(stack: ItemStack): Delegate? {
            stack.notEmpty() ?: return null;
            val item = stack.item
            return (item as? Delegator)?.subItem(stack.itemDamage)
        }
    }
}
