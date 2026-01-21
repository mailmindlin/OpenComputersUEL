package li.cil.oc.common

object InventorySlots {
    @JvmField
    val computer: Array<Array<InventorySlot>> = arrayOf(
        arrayOf(
            InventorySlot(Slot.Card, Tier.One),
            InventorySlot(Slot.Card, Tier.One),
            InventorySlot(Slot.Memory, Tier.One),
            InventorySlot(Slot.HDD, Tier.One),
            InventorySlot(Slot.CPU, Tier.One),
            InventorySlot(Slot.Memory, Tier.One),
            InventorySlot(Slot.EEPROM, Tier.Any)
        ),

        arrayOf(
            InventorySlot(Slot.Card, Tier.Two),
            InventorySlot(Slot.Card, Tier.One),
            InventorySlot(Slot.Memory, Tier.Two),
            InventorySlot(Slot.Memory, Tier.Two),
            InventorySlot(Slot.HDD, Tier.Two),
            InventorySlot(Slot.HDD, Tier.One),
            InventorySlot(Slot.CPU, Tier.Two),
            InventorySlot(Slot.EEPROM, Tier.Any)
        ),

        arrayOf(
            InventorySlot(Slot.Card, Tier.Three),
            InventorySlot(Slot.Card, Tier.Two),
            InventorySlot(Slot.Card, Tier.Two),
            InventorySlot(Slot.Memory, Tier.Three),
            InventorySlot(Slot.Memory, Tier.Three),
            InventorySlot(Slot.HDD, Tier.Three),
            InventorySlot(Slot.HDD, Tier.Two),
            InventorySlot(Slot.Floppy, Tier.One),
            InventorySlot(Slot.CPU, Tier.Three),
            InventorySlot(Slot.EEPROM, Tier.Any)
        ),

        arrayOf(
            InventorySlot(Slot.Card, Tier.Three),
            InventorySlot(Slot.Card, Tier.Three),
            InventorySlot(Slot.Card, Tier.Three),
            InventorySlot(Slot.Memory, Tier.Three),
            InventorySlot(Slot.Memory, Tier.Three),
            InventorySlot(Slot.HDD, Tier.Three),
            InventorySlot(Slot.HDD, Tier.Three),
            InventorySlot(Slot.Floppy, Tier.One),
            InventorySlot(Slot.CPU, Tier.Three),
            InventorySlot(Slot.EEPROM, Tier.Any)
        )
    )

    @JvmField
    val server: Array<Array<InventorySlot>> = arrayOf(
        arrayOf(
            InventorySlot(Slot.Card, Tier.Two),
            InventorySlot(Slot.Card, Tier.Two),
            InventorySlot(Slot.CPU, Tier.Two),
            InventorySlot(Slot.ComponentBus, Tier.Two),
            InventorySlot(Slot.Memory, Tier.Two),
            InventorySlot(Slot.Memory, Tier.Two),
            InventorySlot(Slot.HDD, Tier.Two),
            InventorySlot(Slot.HDD, Tier.Two),
            InventorySlot(Slot.EEPROM, Tier.Any)
        ),

        arrayOf(
            InventorySlot(Slot.Card, Tier.Three),
            InventorySlot(Slot.Card, Tier.Two),
            InventorySlot(Slot.CPU, Tier.Three),
            InventorySlot(Slot.ComponentBus, Tier.Three),
            InventorySlot(Slot.ComponentBus, Tier.Three),
            InventorySlot(Slot.Memory, Tier.Three),
            InventorySlot(Slot.Memory, Tier.Three),
            InventorySlot(Slot.Memory, Tier.Three),
            InventorySlot(Slot.HDD, Tier.Three),
            InventorySlot(Slot.HDD, Tier.Three),
            InventorySlot(Slot.HDD, Tier.Three),
            InventorySlot(Slot.Card, Tier.Two),
            InventorySlot(Slot.EEPROM, Tier.Any)
        ),

        arrayOf(
            InventorySlot(Slot.Card, Tier.Three),
            InventorySlot(Slot.Card, Tier.Three),
            InventorySlot(Slot.CPU, Tier.Three),
            InventorySlot(Slot.ComponentBus, Tier.Three),
            InventorySlot(Slot.ComponentBus, Tier.Three),
            InventorySlot(Slot.ComponentBus, Tier.Three),
            InventorySlot(Slot.Memory, Tier.Three),
            InventorySlot(Slot.Memory, Tier.Three),
            InventorySlot(Slot.Memory, Tier.Three),
            InventorySlot(Slot.Memory, Tier.Three),
            InventorySlot(Slot.HDD, Tier.Three),
            InventorySlot(Slot.HDD, Tier.Three),
            InventorySlot(Slot.HDD, Tier.Three),
            InventorySlot(Slot.HDD, Tier.Three),
            InventorySlot(Slot.Card, Tier.Two),
            InventorySlot(Slot.Card, Tier.Two),
            InventorySlot(Slot.EEPROM, Tier.Any)
        ),

        arrayOf(
            InventorySlot(Slot.Card, Tier.Three),
            InventorySlot(Slot.Card, Tier.Three),
            InventorySlot(Slot.CPU, Tier.Three),
            InventorySlot(Slot.ComponentBus, Tier.Three),
            InventorySlot(Slot.ComponentBus, Tier.Three),
            InventorySlot(Slot.ComponentBus, Tier.Three),
            InventorySlot(Slot.Memory, Tier.Three),
            InventorySlot(Slot.Memory, Tier.Three),
            InventorySlot(Slot.Memory, Tier.Three),
            InventorySlot(Slot.Memory, Tier.Three),
            InventorySlot(Slot.HDD, Tier.Three),
            InventorySlot(Slot.HDD, Tier.Three),
            InventorySlot(Slot.HDD, Tier.Three),
            InventorySlot(Slot.HDD, Tier.Three),
            InventorySlot(Slot.Card, Tier.Three),
            InventorySlot(Slot.Card, Tier.Three),
            InventorySlot(Slot.EEPROM, Tier.Any)
        )
    )

    @JvmField
    val relay: Array<InventorySlot> = arrayOf(
        InventorySlot(Slot.CPU, Tier.Three),
        InventorySlot(Slot.Memory, Tier.Three),
        InventorySlot(Slot.HDD, Tier.Three),
        InventorySlot(Slot.Card, Tier.Three)
    )

    @JvmField
    val switch: Array<InventorySlot> = arrayOf(
        InventorySlot(Slot.CPU, Tier.Three),
        InventorySlot(Slot.Memory, Tier.Three),
        InventorySlot(Slot.HDD, Tier.Three)
    )

    data class InventorySlot(val slot: String, val tier: Int)
}
