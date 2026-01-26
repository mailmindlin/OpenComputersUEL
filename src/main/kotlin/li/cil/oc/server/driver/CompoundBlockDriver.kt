package li.cil.oc.server.driver

import com.google.common.base.Strings
import li.cil.oc.api.driver.DriverBlock
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.network.ManagedEnvironment
import net.minecraft.inventory.IInventory
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class CompoundBlockDriver(val sidedBlocks: Array<DriverBlock>) : DriverBlock {
    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing): CompoundBlockEnvironment? {
        val list = sidedBlocks.mapNotNull { driver ->
            driver.createEnvironment(world, pos, side)?.let { environment ->
                driver.javaClass.name to environment
            }
        }
        return if (list.isEmpty()) null
        else CompoundBlockEnvironment(cleanName(tryGetName(world, pos, list.map { it.second })), list)
    }

    override fun worksWith(world: World, pos: BlockPos, side: EnumFacing): Boolean =
        sidedBlocks.all { it.worksWith(world, pos, side) }

    override fun equals(other: Any?): Boolean = when (other) {
        is CompoundBlockDriver -> other.sidedBlocks.size == sidedBlocks.size &&
            sidedBlocks.intersect(other.sidedBlocks.toSet()).size == sidedBlocks.size
        else -> false
    }

    override fun hashCode(): Int = sidedBlocks.contentHashCode()

    // TODO rework this method
    private fun tryGetName(world: World, pos: BlockPos, environments: List<ManagedEnvironment>): String {
        environments.filterIsInstance<NamedBlock>().sortedBy { it.priority() }.lastOrNull()?.let { named ->
            return named.preferredName()
        }
        try {
            val tileEntity = world.getTileEntity(pos)
            if (tileEntity is IInventory && !Strings.isNullOrEmpty(tileEntity.name)) {
                return tileEntity.name.removePrefix("container.")
            }
        } catch (_: Throwable) {
        }
        try {
            val block = world.getBlockState(pos).block
            val stack = if (Item.getItemFromBlock(block) != null) {
                ItemStack(block, 1, block.damageDropped(world.getBlockState(pos)))
            } else null
            if (stack != null) {
                return stack.translationKey.removePrefix("tile.")
            }
        } catch (_: Throwable) {
        }
        try {
            val tileEntity = world.getTileEntity(pos)
            if (tileEntity != null) {
                return TileEntity.getKey(tileEntity.javaClass)!!.path
            }
        } catch (_: Throwable) {
        }
        return "component"
    }

    private fun cleanName(name: String): String {
        val safeStart = if (name.matches(Regex("""^[^a-zA-Z_]"""))) "_$name" else name
        val identifier = safeStart.replace(Regex("""[^\w_]"""), "_").trim()
        return if (Strings.isNullOrEmpty(identifier)) "component"
        else identifier.lowercase()
    }
}
