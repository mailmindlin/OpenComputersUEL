package li.cil.oc.common

import li.cil.oc.api.driver.item.Slot as DriverSlot

object Slot {
    @JvmField val None: String = DriverSlot.None
    @JvmField val Any: String = DriverSlot.Any
    const val Filtered: String = "filtered"

    @JvmField val Card: String = DriverSlot.Card
    @JvmField val ComponentBus: String = DriverSlot.ComponentBus
    @JvmField val Container: String = DriverSlot.Container
    @JvmField val CPU: String = DriverSlot.CPU
    const val EEPROM: String = "eeprom"
    @JvmField val Floppy: String = DriverSlot.Floppy
    @JvmField val HDD: String = DriverSlot.HDD
    @JvmField val Memory: String = DriverSlot.Memory
    @JvmField val RackMountable: String = DriverSlot.RackMountable
    @JvmField val Tablet: String = DriverSlot.Tablet
    const val Tool: String = "tool"
    @JvmField val Upgrade: String = DriverSlot.Upgrade

    @JvmField val All: Array<String> = arrayOf(Card, ComponentBus, Container, CPU, EEPROM, Floppy, HDD, Memory, RackMountable, Tablet, Tool, Upgrade)
}
