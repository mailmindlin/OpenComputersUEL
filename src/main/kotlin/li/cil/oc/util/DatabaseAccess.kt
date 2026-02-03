package li.cil.oc.util

import li.cil.oc.api.network.Component
import li.cil.oc.api.network.Node
import li.cil.oc.server.component.UpgradeDatabase

object DatabaseAccess {
    @JvmStatic
    fun databases(node: Node): Iterable<UpgradeDatabase> {
        return node.network()!!.nodes().mapNotNull { n ->
            (n as? Component)?.host() as? UpgradeDatabase
        }
    }

    @JvmStatic
    fun database(node: Node, address: String): UpgradeDatabase {
        val networkNode = node.network()!!.node(address)
            as? Component
            ?: throw IllegalArgumentException("no such component")

        return networkNode.host()
            as? UpgradeDatabase
            ?: throw IllegalArgumentException("not a database")
    }

    @JvmStatic
    fun withDatabase(node: Node, address: String, f: (UpgradeDatabase) -> Result): Result {
        return f(database(node, address))
    }
}
