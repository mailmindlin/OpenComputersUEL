package li.cil.oc.common.item

import li.cil.oc.CreativeTab
import li.cil.oc.Settings
import li.cil.oc.client.renderer.item.HoverBootRenderer
import li.cil.oc.common.item.data.HoverBootsData
import li.cil.oc.common.item.traits.Chargeable
import li.cil.oc.common.item.traits.SimpleItem
import li.cil.oc.common.tileentity.DiskDrive
import li.cil.oc.util.ItemColorizer
import li.cil.oc.util.Tooltip
import net.minecraft.block.BlockCauldron
import net.minecraft.client.model.ModelBiped
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Blocks
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.item.EnumRarity
import net.minecraft.item.ItemArmor
import net.minecraft.item.ItemStack
import net.minecraft.potion.Potion
import net.minecraft.potion.PotionEffect
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import kotlin.math.max
import kotlin.math.min

class HoverBoots : ItemArmor(ArmorMaterial.DIAMOND, 0, EntityEquipmentSlot.FEET), Chargeable {
    init {
        setNoRepair()
        creativeTab = CreativeTab
    }

    // ------- Copied from SimpleItem ------- //
    fun createItemStack(amount: Int = 1): ItemStack = ItemStack(this, amount)

    override fun isBookEnchantable(stack: ItemStack, book: ItemStack): Boolean = false

    override fun doesSneakBypassUse(stack: ItemStack, world: IBlockAccess, pos: BlockPos, player: EntityPlayer): Boolean {
        val te = world.getTileEntity(pos)
        return if (te is DiskDrive) {
            true
        } else {
            super.doesSneakBypassUse(stack, world, pos, player)
        }
    }

    @SideOnly(Side.CLIENT)
    override fun addInformation(stack: ItemStack, world: World?, tooltip: MutableList<String>, flag: ITooltipFlag) {
        tooltip.addAll(Tooltip.get(javaClass.simpleName.lowercase()))

        if (stack.hasTagCompound() && stack.tagCompound!!.hasKey(Settings.namespace + "data")) {
            val data = stack.tagCompound!!.getCompoundTag(Settings.namespace + "data")
            if (data.hasKey("node") && data.getCompoundTag("node").hasKey("address")) {
                tooltip.add("\u00a78${data.getCompoundTag("node").getString("address").substring(0, 13)}...\u00a77")
            }
        }
    }

    // ------- ------- //

    override fun getRarity(stack: ItemStack): EnumRarity = EnumRarity.UNCOMMON

    override fun maxCharge(stack: ItemStack): Double = Settings.get.bufferHoverBoots

    override fun getCharge(stack: ItemStack): Double = HoverBootsData(stack).charge

    override fun setCharge(stack: ItemStack, amount: Double) {
        val data = HoverBootsData(stack)
        data.charge = min(maxCharge(stack), max(0.0, amount))
        data.save(stack)
    }

    override fun canCharge(stack: ItemStack): Boolean = true

    override fun charge(stack: ItemStack, amount: Double, simulate: Boolean): Double {
        val data = HoverBootsData(stack)
        return Chargeable.applyCharge(amount, data.charge, Settings.get.bufferHoverBoots) { used ->
            if (!simulate) {
                data.charge += used
                data.save(stack)
            }
        }
    }

    @SideOnly(Side.CLIENT)
    override fun getArmorModel(entityLiving: EntityLivingBase, itemStack: ItemStack, armorSlot: EntityEquipmentSlot, default: ModelBiped): ModelBiped? {
        return if (armorSlot == armorType) {
            HoverBootRenderer.lightColor = if (ItemColorizer.hasColor(itemStack)) ItemColorizer.getColor(itemStack) else 0x66DD55
            HoverBootRenderer
        } else {
            super.getArmorModel(entityLiving, itemStack, armorSlot, default)
        }
    }

    override fun getArmorTexture(stack: ItemStack, entity: Entity, slot: EntityEquipmentSlot, subType: String?): String? {
        return if (entity.world.isRemote) HoverBootRenderer.texture.toString() else null
    }

    override fun onArmorTick(world: World, player: EntityPlayer, stack: ItemStack) {
        super.onArmorTick(world, player, stack)
        if (!Settings.get.ignorePower && player.getActivePotionEffect(Potion.getPotionFromResourceLocation("slowness")) == null && getCharge(stack) == 0.0) {
            player.addPotionEffect(PotionEffect(Potion.getPotionFromResourceLocation("slowness"), 20, 1))
        }
    }

    override fun onEntityItemUpdate(entity: EntityItem): Boolean {
        if (entity != null && entity.world != null && !entity.world.isRemote && ItemColorizer.hasColor(entity.item)) {
            val pos = entity.position
            val state = entity.world.getBlockState(pos)
            if (state.block == Blocks.CAULDRON) {
                val level = state.getValue(BlockCauldron.LEVEL)
                if (level > 0) {
                    ItemColorizer.removeColor(entity.item)
                    entity.world.setBlockState(pos, state.withProperty(BlockCauldron.LEVEL, level - 1), 3)
                    return true
                }
            }
        }
        return super.onEntityItemUpdate(entity)
    }

    override fun showDurabilityBar(stack: ItemStack): Boolean = true

    override fun getDurabilityForDisplay(stack: ItemStack): Double {
        val data = HoverBootsData(stack)
        return 1 - data.charge / Settings.get.bufferHoverBoots
    }

    override fun getMaxDamage(stack: ItemStack): Int = Settings.get.bufferHoverBoots.toInt()

    // Always show energy bar.
    override fun isDamaged(stack: ItemStack): Boolean = true

    // Contradictory as it may seem with the above, this avoids actual damage value changing.
    override fun isDamageable(): Boolean = false

    override fun setDamage(stack: ItemStack, damage: Int) {
        // Subtract energy when taking damage instead of actually damaging the item.
        charge(stack, -damage.toDouble(), simulate = false)

        // Set to 0 for old boots that may have been damaged before.
        super.setDamage(stack, 0)
    }
}
