package li.cil.oc.integration.minecraft

import li.cil.oc.Settings
import li.cil.oc.api.event.GeolyzerEvent
import li.cil.oc.util.BlockPosition
import net.minecraft.block.Block
import net.minecraft.block.BlockCrops
import net.minecraft.block.BlockStem
import net.minecraft.block.properties.PropertyInteger
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraftforge.fluids.FluidRegistry
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object EventHandlerVanilla {
    @SubscribeEvent
    fun onGeolyzerScan(e: GeolyzerEvent.Scan) {
        val world = e.host.world()
        val blockPos = BlockPosition(e.host)
        val includeReplaceable = when (val value = e.options["includeReplaceable"]) {
            is Boolean -> value
            else -> true
        }

        val noise = ByteArray(e.data.size)
        world.rand.nextBytes(noise)
        // Map to [-1, 1). The additional /33f is for normalization below.
        noise.map { it / 128f / 33f }.forEachIndexed { i, v -> e.data[i] = v }

        val w = e.maxX - e.minX + 1
        val d = e.maxZ - e.minZ + 1
        for (ry in e.minY..e.maxY) {
            for (rz in e.minZ..e.maxZ) {
                for (rx in e.minX..e.maxX) {
                    val pos = blockPos.toBlockPos().add(rx, ry, rz)
                    val x = blockPos.x + rx
                    val y = blockPos.y + ry
                    val z = blockPos.z + rz
                    val index = (rx - e.minX) + ((rz - e.minZ) + (ry - e.minY) * d) * w
                    if (world.isBlockLoaded(pos) && !world.isAirBlock(pos)) {
                        val blockState = world.getBlockState(pos)
                        val block = blockState.block
                        if (block != Blocks.AIR && (includeReplaceable || isFluid(block) || !block.isReplaceable(world, blockPos.toBlockPos()))) {
                            val distance = sqrt((rx * rx + ry * ry + rz * rz).toFloat())
                            e.data[index] = e.data[index] * distance * Settings.get.geolyzerNoise + blockState.getBlockHardness(world, pos)
                        } else {
                            e.data[index] = 0f
                        }
                    } else {
                        e.data[index] = 0f
                    }
                }
            }
        }
    }

    private fun isFluid(block: Block): Boolean = FluidRegistry.lookupFluidForBlock(block) != null

    private fun getGrowth(blockState: IBlockState): Float? {
        val ageProperty = blockState.propertyKeys.find { prop ->
            prop is PropertyInteger && prop.name == "age"
        } as? PropertyInteger

        return ageProperty?.let { prop ->
            val value = blockState.getValue(prop).toFloat()
            val maxValue = prop.allowedValues.maxOrNull() ?: return null
            max(0f, min(1f, value / maxValue))
        }
    }

    @SubscribeEvent
    fun onGeolyzerAnalyze(e: GeolyzerEvent.Analyze) {
        val world = e.host.world()
        val blockState = world.getBlockState(e.pos).getActualState(world, e.pos)
        val block = blockState.block

        e.data["name"] = Block.REGISTRY.getNameForObject(block)
        e.data["hardness"] = blockState.getBlockHardness(world, e.pos)
        e.data["harvestLevel"] = block.getHarvestLevel(blockState)
        e.data["harvestTool"] = block.getHarvestTool(blockState)
        e.data["color"] = blockState.getMapColor(world, e.pos).colorValue

        // backward compatibility
        e.data["metadata"] = try {
            block.getMetaFromState(blockState)
        } catch (ex: IllegalArgumentException) {
            0
        }

        e.data["properties"] = buildMap<String, Any> {
            for (prop in blockState.properties.keys) {
                put(prop.name, blockState.getValue(prop))
            }
        }

        if (Settings.get.insertIdsInConverters) {
            e.data["id"] = Block.getIdFromBlock(block)
        }

        val growth = when {
            block is BlockCrops || block is BlockStem || block == Blocks.COCOA ||
            block == Blocks.NETHER_WART || block == Blocks.CHORUS_FLOWER -> getGrowth(blockState)
            block == Blocks.MELON_BLOCK || block == Blocks.PUMPKIN || block == Blocks.CACTUS ||
            block == Blocks.REEDS || block == Blocks.CHORUS_PLANT -> 1f
            else -> null
        }

        growth?.let { e.data["growth"] = it }
    }
}
