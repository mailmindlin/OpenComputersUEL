package li.cil.oc.common

enum class GuiType(val subType: Category) {
    Adapter(Category.Block),
    Assembler(Category.Block),
    Case(Category.Block),
    Charger(Category.Block),
    Database(Category.Item),
    Disassembler(Category.Block),
    DiskDrive(Category.Block),
    DiskDriveMountable(Category.Item),
    DiskDriveMountableInRack(Category.Block),
    Drive(Category.Item),
    Drone(Category.Entity),
    Manual(Category.None),
    Printer(Category.Block),
    Rack(Category.Block),
    Raid(Category.Block),
    Relay(Category.Block),
    Robot(Category.Block),
    Screen(Category.Block),
    Server(Category.Item),
    ServerInRack(Category.Block),
    Switch(Category.Block),
    Tablet(Category.Item),
    TabletInner(Category.Item),
    Terminal(Category.Item),
    Waypoint(Category.Block);

    val id: Int get() = ordinal

    enum class Category {
        None,
        Block,
        Entity,
        Item
    }

    companion object {
        private val values = values()

        @JvmField
        val Categories: Map<Int, Category> = values.associate { it.ordinal to it.subType }

        @JvmStatic
        fun embedSlot(y: Int, slot: Int): Int = (y and 0x00FFFFFF) or (slot shl 24)

        @JvmStatic
        fun extractY(value: Int): Int = (value shl 8) shr 8

        @JvmStatic
        fun extractSlot(value: Int): Int = (value ushr 24) and 0xFF
    }
}
