package li.cil.oc.integration.util

import li.cil.oc.Settings

object Power {
    // Applied Energistics 2

    fun fromAE(value: Double): Double = value * Settings.get().ratioAppliedEnergistics2

    fun toAE(value: Double): Double = value / Settings.get().ratioAppliedEnergistics2

    // Factorization

    fun fromCharge(value: Double): Double = value * Settings.get().ratioFactorization

    fun toCharge(value: Double): Double = value / Settings.get().ratioFactorization

    // Galacticraft

    fun fromGC(value: Float): Double = value * Settings.get().ratioGalacticraft

    fun toGC(value: Double): Float = (value / Settings.get().ratioGalacticraft).toFloat()

    // IndustrialCraft 2

    fun fromEU(value: Double): Double = value * Settings.get().ratioIndustrialCraft2

    fun toEU(value: Double): Double = value / Settings.get().ratioIndustrialCraft2

    // Mekanism

    fun fromJoules(value: Double): Double = value * Settings.get().ratioMekanism

    fun toJoules(value: Double): Double = value / Settings.get().ratioMekanism

    // Redstone Flux

    fun fromRF(value: Int): Double = value * Settings.get().ratioRedstoneFlux

    fun toRF(value: Double): Int = (value / Settings.get().ratioRedstoneFlux).toInt()

    // RotaryCraft

    fun fromWA(value: Long): Double = value * Settings.get().ratioRotaryCraft

    fun toWA(value: Double): Long = (value / Settings.get().ratioRotaryCraft).toLong()

    // Tesla

    fun fromTesla(value: Long): Double = value * Settings.get().ratioRedstoneFlux

    fun toTesla(value: Double): Long = (value / Settings.get().ratioRedstoneFlux).toLong()
}
