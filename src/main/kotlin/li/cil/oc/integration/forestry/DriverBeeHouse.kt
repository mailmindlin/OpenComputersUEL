package li.cil.oc.integration.forestry

import com.google.common.collect.Sets
import forestry.api.apiculture.IBeeHousing
import forestry.api.genetics.AlleleManager
import forestry.api.genetics.IAllele
import forestry.api.genetics.IAlleleSpecies
import forestry.api.genetics.IMutation
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.*

class DriverBeeHouse : DriverSidedTileEntity() {
    override fun getTileEntityClass(): Class<*> = IBeeHousing::class.java

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing)
        = (world.getTileEntity(pos) as? IBeeHousing)?.let(::Environment)

    class Environment(tileEntity: IBeeHousing) : ManagedTileEntityEnvironment<IBeeHousing>(tileEntity, "bee_housing"), NamedBlock {
        override fun preferredName(): String = "bee_housing"
        override fun priority(): Int = 0

        @Callback(doc = "function():boolean -- Can the bees breed?")
        fun canBreed(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(tileEntity.beekeepingLogic.canWork())
        }

        @Callback(doc = "function():table -- Get the drone")
        fun getDrone(context: Context?, args: Arguments?): Array<Any?>? {
            val drone = tileEntity.beeInventory.drone ?: return null
            return arrayOf(AlleleManager.alleleRegistry.getIndividual(drone))
        }

        @Callback(doc = "function():table -- Get the queen")
        fun getQueen(context: Context?, args: Arguments?): Array<Any?>? {
            val queen = tileEntity.beeInventory.queen ?: return null
            return arrayOf(AlleleManager.alleleRegistry.getIndividual(queen))
        }

        @Callback(doc = "function():table -- Get the full breeding list thingy.")
        fun getBeeBreedingData(context: Context?, args: Arguments?): Array<Any>? {
            val beeRoot = AlleleManager.alleleRegistry.getSpeciesRoot("rootBees") ?: return null

            val result: MutableSet<Map<String, Any>> = Sets.newHashSet()
            for (mutation in beeRoot.getMutations(false)) {
                val mutationMap = HashMap<String, Any>()

                val allele1: IAllele? = mutation.allele0
                if (allele1 != null) {
                    mutationMap["allele1"] = allele1.alleleName
                }

                val allele2: IAllele? = mutation.allele1
                if (allele2 != null) {
                    mutationMap["allele2"] = allele2.alleleName
                }

                mutationMap["chance"] = mutation.baseChance
                mutationMap["specialConditions"] = mutation
                    .specialConditions.toTypedArray()

                val template = mutation.template
                if (template != null && template.size > 0) {
                    mutationMap["result"] = template[0].alleleName
                }
                result.add(mutationMap)
            }
            return arrayOf(result)
        }

        @Callback(doc = "function():table -- Get all known bees mutations")
        fun listAllSpecies(context: Context?, args: Arguments?): Array<Any>? {
            val beeRoot = AlleleManager.alleleRegistry.getSpeciesRoot("rootBees") ?: return null

            val result: MutableSet<IAlleleSpecies> = Sets.newHashSet()
            for (mutation in beeRoot.getMutations(false)) {
                val template = mutation.template
                if (template == null || template.size <= 0) {
                    continue
                }

                val allele = template[0] as? IAlleleSpecies ?: continue

                result.add(allele)
            }
            return arrayOf(result)
        }

        @Callback(doc = "function(beeName:string):table -- Get the parents for a particular mutation")
        fun getBeeParents(context: Context?, args: Arguments): Array<Any>? {
            val beeRoot = AlleleManager.alleleRegistry.getSpeciesRoot("rootBees") ?: return null

            val result: MutableSet<IMutation> = Sets.newHashSet()
            val childType = args.checkString(0).lowercase(Locale.getDefault())
            for (mutation in beeRoot.getMutations(false)) {
                val template = mutation.template
                if (template == null || template.size < 1) {
                    continue
                }

                val allele = template[0] as? IAlleleSpecies ?: continue

                val species = allele
                val uid = species.uid.lowercase(Locale.getDefault())
                val localizedName = species.alleleName.lowercase(Locale.getDefault())
                if (localizedName == childType || uid == childType) {
                    result.add(mutation)
                }
            }
            return arrayOf(result)
        }
    }
}