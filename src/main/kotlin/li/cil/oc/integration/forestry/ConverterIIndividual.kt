package li.cil.oc.integration.forestry

import com.google.common.collect.ImmutableMap
import com.google.common.collect.Maps
import forestry.api.apiculture.*
import forestry.api.arboriculture.*
import forestry.api.genetics.*
import forestry.api.lepidopterology.*
import li.cil.oc.api.driver.Converter


private interface IAlleleConverter<A : IAllele> {
    fun convert(allele: A): Any
}

private inline fun <reified A: IAllele> ImmutableMap.Builder<Class<out IAllele>, IAlleleConverter<*>>.converter(crossinline f: (A) -> Any) {
    this.put(A::class.java, object : IAlleleConverter<A> {
        override fun convert(allele: A): Any = f(allele)
    })
}
/*
* Partially copied from:
* https://github.com/OpenMods/OpenPeripheral
*/
class ConverterIIndividual : Converter {
    private abstract class GenomeAccess {
        fun getAllele(genome: IGenome, chromosome: Int): IAllele? {
            val genotype = genome.chromosomes
            val ch = genotype[chromosome] ?: return null
            return getAllele(ch)
        }

        protected abstract fun getAllele(chromosome: IChromosome): IAllele?
    }

    private abstract class GenomeReader<G : IGenome, E>(private val genome: G) where E : Enum<E>, E : IChromosomeType {
        private inline fun <reified A : IAllele> GenomeAccess.getAllele(chromosome: E): A? = this.getAllele(A::class.java, chromosome) as? A

        private fun <A : IAllele> GenomeAccess.getAllele(cls: Class<A>, chromosome: E): IAllele? {
            require(chromosome.alleleClass == cls)
            return this.getAllele(genome, chromosome.ordinal)
        }

        protected inline fun <reified A : IAllele> GenomeAccess.convertAllele(chromosome: E): Any = convertAllele(this, A::class.java, chromosome)
        private fun <A : IAllele> convertAllele(access: GenomeAccess, cls: Class<A>, chromosome: E): Any {
            @Suppress("UNCHECKED_CAST") // invariant of `getAllele`
            val allele = (access.getAllele(cls, chromosome) ?: return "missing") as A

            @Suppress("UNCHECKED_CAST") // Invariant of `converters`
            val converter = (converters[cls] ?: return allele.alleleName) as IAlleleConverter<A>
            return converter.convert(allele)
        }

        protected abstract fun addAlleleInfo(access: GenomeAccess, result: MutableMap<String, Any>)

        val activeInfo: Map<String, Any>
            get() {
                val result: MutableMap<String, Any> =
                    Maps.newHashMap()
                addAlleleInfo(ACTIVE, result)
                return result
            }

        val inactiveInfo: Map<String, Any>
            get() {
                val result: MutableMap<String, Any> =
                    Maps.newHashMap()
                addAlleleInfo(INACTIVE, result)
                return result
            }
    }

    private class BeeGenomeReader(genome: IBeeGenome) : GenomeReader<IBeeGenome, EnumBeeChromosome>(genome) {
        override fun addAlleleInfo(access: GenomeAccess, result: MutableMap<String, Any>) {
            result["species"] = access.convertAllele<IAlleleBeeSpecies>(EnumBeeChromosome.SPECIES)
            result["speed"] = access.convertAllele<IAlleleFloat>(EnumBeeChromosome.SPEED)
            result["lifespan"] = access.convertAllele<IAlleleInteger>(EnumBeeChromosome.LIFESPAN)
            result["fertility"] = access.convertAllele<IAlleleInteger>(EnumBeeChromosome.FERTILITY)
            result["temperatureTolerance"] = access.convertAllele<IAlleleTolerance>(EnumBeeChromosome.TEMPERATURE_TOLERANCE)
            result["neverSleeps"] = access.convertAllele<IAlleleBoolean>(EnumBeeChromosome.NEVER_SLEEPS)
            result["humidityTolerance"] = access.convertAllele<IAlleleTolerance>(EnumBeeChromosome.HUMIDITY_TOLERANCE)
            result["toleratesRain"] = access.convertAllele<IAlleleBoolean>(EnumBeeChromosome.TOLERATES_RAIN)
            result["caveDwelling"] = access.convertAllele<IAlleleBoolean>(EnumBeeChromosome.CAVE_DWELLING)
            result["flowerProvider"] = access.convertAllele<IAlleleFlowers>(EnumBeeChromosome.FLOWER_PROVIDER)
            result["flowering"] = access.convertAllele<IAlleleInteger>(EnumBeeChromosome.FLOWERING)
            result["effect"] = access.convertAllele<IAlleleBeeEffect>(EnumBeeChromosome.EFFECT)
            result["territory"] = access.convertAllele<IAlleleArea>(EnumBeeChromosome.TERRITORY)
        }
    }

    private class ButterflyGenomeReader(genome: IButterflyGenome) : GenomeReader<IButterflyGenome, EnumButterflyChromosome>(genome) {
        override fun addAlleleInfo(access: GenomeAccess, result: MutableMap<String, Any>) {
            result["species"] = access.convertAllele<IAlleleButterflySpecies>(EnumButterflyChromosome.SPECIES)
            result["size"] = access.convertAllele<IAlleleFloat>(EnumButterflyChromosome.SIZE)
            result["speed"] = access.convertAllele<IAlleleFloat>(EnumButterflyChromosome.SPEED)
            result["lifespan"] = access.convertAllele<IAlleleInteger>(EnumButterflyChromosome.LIFESPAN)
            result["metabolism"] = access.convertAllele<IAlleleInteger>(EnumButterflyChromosome.METABOLISM)
            result["fertility"] = access.convertAllele<IAlleleInteger>(EnumButterflyChromosome.FERTILITY)
            result["temperatureTolerance"] = access.convertAllele<IAlleleTolerance>(EnumButterflyChromosome.TEMPERATURE_TOLERANCE)
            result["humidityTolerance"] = access.convertAllele<IAlleleTolerance>(EnumButterflyChromosome.HUMIDITY_TOLERANCE)
            result["nocturnal"] = access.convertAllele<IAlleleBoolean>(EnumButterflyChromosome.NOCTURNAL)
            result["tolerantFlyer"] = access.convertAllele<IAlleleBoolean>(EnumButterflyChromosome.TOLERANT_FLYER)
            result["fireResist"] = access.convertAllele<IAlleleBoolean>(EnumButterflyChromosome.FIRE_RESIST)
            result["flowerProvider"] = access.convertAllele<IAlleleFlowers>(EnumButterflyChromosome.FLOWER_PROVIDER)
            result["effect"] = access.convertAllele<IAlleleButterflyEffect>(EnumButterflyChromosome.EFFECT)
            result["cocoon"] = access.convertAllele<IAlleleButterflyCocoon>(EnumButterflyChromosome.COCOON)
        }
    }

    private class TreeGenomeReader(genome: ITreeGenome) : GenomeReader<ITreeGenome, EnumTreeChromosome>(genome) {
        override fun addAlleleInfo(access: GenomeAccess, result: MutableMap<String, Any>) {
            result["species"] = access.convertAllele<IAlleleTreeSpecies>(EnumTreeChromosome.SPECIES)
            result["fireproof"] = access.convertAllele<IAlleleBoolean>(EnumTreeChromosome.FIREPROOF)
            result["height"] = access.convertAllele<IAlleleFloat>(EnumTreeChromosome.HEIGHT)
            result["fertility"] = access.convertAllele<IAlleleFloat>(EnumTreeChromosome.FERTILITY)
            result["fruits"] = access.convertAllele<IAlleleFruit>(EnumTreeChromosome.FRUITS)
            result["yield"] = access.convertAllele<IAlleleFloat>(EnumTreeChromosome.YIELD)
            result["sappiness"] = access.convertAllele<IAlleleFloat>(EnumTreeChromosome.SAPPINESS)
            result["effect"] = access.convertAllele<IAlleleLeafEffect>(EnumTreeChromosome.EFFECT)
            result["maturation"] = access.convertAllele<IAlleleInteger>(EnumTreeChromosome.MATURATION)
            result["girth"] = access.convertAllele<IAlleleInteger>(EnumTreeChromosome.GIRTH)
        }
    }

    override fun convert(value: Any, output: MutableMap<Any, Any>) {
        val individual = value as? IIndividual ?: return
        output["displayName"] = individual.displayName
        output["ident"] = individual.ident

        val isAnalyzed = individual.isAnalyzed
        output["isAnalyzed"] = isAnalyzed
        output["isSecret"] = individual.isSecret

        if (individual is IIndividualLiving) {
            val living = individual
            output["health"] = living.health
            output["maxHealth"] = living.maxHealth
        }

        var genomeReader: GenomeReader<*, *>? = null
        when (individual) {
            is IBee -> {
                val bee = individual
                output["type"] = "bee"
                output["canSpawn"] = bee.canSpawn()
                output["generation"] = bee.generation
                output["hasEffect"] = bee.hasEffect()
                output["isAlive"] = bee.isAlive
                output["isNatural"] = bee.isNatural

                if (isAnalyzed) genomeReader = BeeGenomeReader(bee.genome)
            }
            is IButterfly -> {
                val butterfly = individual
                output["type"] = "butterfly"
                output["size"] = butterfly.size
                if (isAnalyzed) genomeReader = ButterflyGenomeReader(butterfly.genome)
            }
            is ITree -> {
                val tree = individual
                output["type"] = "tree"
                output["plantType"] = tree.displayName
                if (isAnalyzed) genomeReader = TreeGenomeReader(tree.genome)
            }
        }

        if (genomeReader != null) {
            output["active"] = genomeReader.activeInfo
            output["inactive"] = genomeReader.inactiveInfo
        }
    }

    companion object {
        private val ACTIVE: GenomeAccess = object : GenomeAccess() {
            override fun getAllele(chromosome: IChromosome): IAllele? {
                return chromosome.activeAllele
            }
        }

        private val INACTIVE: GenomeAccess = object : GenomeAccess() {
            override fun getAllele(chromosome: IChromosome): IAllele? {
                return chromosome.inactiveAllele
            }
        }

        private val converters: Map<Class<out IAllele>, IAlleleConverter<*>> = run {

            val builder = ImmutableMap.builder<Class<out IAllele>, IAlleleConverter<*>>()
            builder.converter<IAlleleFloat> { allele -> allele.value }
            builder.converter<IAlleleInteger> { allele -> allele.value }
            builder.converter<IAlleleBoolean> { allele -> allele.value }
            builder.converter<IAlleleArea> { allele -> allele.value }
            builder.build()
        }
    }
}
