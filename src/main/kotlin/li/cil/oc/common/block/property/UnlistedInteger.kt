package li.cil.oc.common.block.property

import net.minecraftforge.common.property.IUnlistedProperty

class UnlistedInteger(private val name: String) : IUnlistedProperty<Int> {
    override fun getName(): String = name

    override fun isValid(value: Int?): Boolean = value != null

    override fun getType(): Class<Int> = Int::class.javaObjectType

    override fun valueToString(value: Int): String = value.toString()
}
