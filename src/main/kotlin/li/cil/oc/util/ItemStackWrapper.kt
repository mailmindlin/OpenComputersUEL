package li.cil.oc.util

import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import java.util.Objects

class ItemStackWrapper(val inner: ItemStack) : Comparable<ItemStackWrapper>, Cloneable {
    val id: Int
        get() = if (inner.item != null) Item.getIdFromItem(inner.item) else 0

    val damage: Int
        get() = if (inner.item != null) inner.itemDamage else 0

    override fun compareTo(other: ItemStackWrapper): Int {
        return if (this.id == other.id) this.damage - other.damage
        else this.id - other.id
    }

    override fun hashCode(): Int = Objects.hash(id, damage)

    override fun equals(other: Any?): Boolean {
        if (other is ItemStackWrapper) {
            return compareTo(other) == 0
        }
        return false
    }

    public override fun clone(): ItemStackWrapper = ItemStackWrapper(inner)

    override fun toString(): String = inner.toString()
}
