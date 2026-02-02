package li.cil.oc.integration.computercraft

import dan200.computercraft.api.lua.ILuaObject
import li.cil.oc.api.driver.Converter
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ManagedPeripheral
import li.cil.oc.api.prefab.AbstractValue

class ConverterLuaObject : Converter {
    override fun convert(value: Any, output: MutableMap<Any, Any>) {
        if (value is ILuaObject) {
            output["value"] = LuaObjectValue(value)
        }
    }

    class LuaObjectValue : AbstractValue, ManagedPeripheral {
        private val value: ILuaObject?
        private val helper: CallableHelper?

        // For loading when values were saved in a computer state.
        @Suppress("unused")
        constructor() {
            value = null
            helper = null
        }

        constructor(value: ILuaObject) {
            this.value = value
            helper = CallableHelper(value.methodNames)
        }

        override fun methods(): Array<out String> {
            return value?.methodNames
                ?: arrayOf() // Loaded userdata, missing context.
        }

        @Throws(Exception::class)
        override fun invoke(method: String, context: Context, args: Arguments): Array<out Any?>? {
            val value = value ?: return arrayOf(null, "ComputerCraft userdata cannot be persisted")
            val index = helper!!.methodIndex(method)
            val argArray = helper.convertArguments(args)
            return value.callMethod(DriverPeripheral.Environment.UnsupportedLuaContext.instance(), index, argArray)
        }
    }
}
