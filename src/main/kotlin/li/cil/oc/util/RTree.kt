package li.cil.oc.util

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class RTree<Data>(private val M: Int, private val coordinate: (Data) -> Triple<Double, Double, Double>) {
    init {
        if (M < 2) throw IllegalArgumentException("maxEntries must be larger or equal to 2.")
    }

    // Used for quick checks whether values are in the tree, e.g. for updates.
    private val entries = mutableMapOf<Data, Leaf>()

    private val m = max(M / 2, 1)

    private var root = NonLeaf()

    @Synchronized
    operator fun get(value: Data): Triple<Double, Double, Double>? =
        entries[value]?.let { Triple(it.bounds.min.x, it.bounds.min.y, it.bounds.min.z) }

    // Allows debug rendering of the tree.
    @Synchronized
    fun allBounds(): List<Pair<Pair<Triple<Double, Double, Double>, Triple<Double, Double, Double>>, Int>> =
        root.allBounds(0)

    @Synchronized
    fun add(value: Data): Boolean {
        val replaced = remove(value)
        val entry = Leaf(value, Point(coordinate(value)))
        entries[value] = entry
        val newNode = root.add(entry)
        if (newNode != root) {
            root = NonLeaf(newNode, root)
        }
        return !replaced
    }

    @Synchronized
    fun remove(value: Data): Boolean {
        val node = entries.remove(value) ?: return false
        val change = root.remove(node)
        assert(change?.contains(node) == true || change?.contains(root) == true)
        val children = root.children
        if (children.size == 1) {
            val first = children.firstOrNull()
            if (first is NonLeaf) {
                root = first
                return true
            }
        }
        root.bounds = Rectangle.around(root.children)
        return true
    }

    @Synchronized
    fun query(from: Triple<Double, Double, Double>, to: Triple<Double, Double, Double>): List<Data> =
        root.query(Rectangle(Point(from), Point(to)))

    private abstract inner class Node {
        abstract val bounds: Rectangle

        open fun allBounds(level: Int): List<Pair<Pair<Triple<Double, Double, Double>, Triple<Double, Double, Double>>, Int>> =
            listOf(bounds.asTuple() to level)

        open val isLeaf: Boolean = true

        abstract fun add(value: Node): Node

        abstract fun remove(value: Node): Set<Node>?

        abstract fun query(query: Rectangle): List<Data>
    }

    private inner class NonLeaf() : Node() {
        constructor(vararg nodes: Node) : this() {
            for (child in nodes) {
                children.add(child)
                bounds = bounds.including(child.bounds)
            }
        }

        val children = mutableSetOf<Node>()

        override var bounds = Rectangle(Point.PositiveInfinity, Point.NegativeInfinity)

        override fun allBounds(level: Int): List<Pair<Pair<Triple<Double, Double, Double>, Triple<Double, Double, Double>>, Int>> =
            super.allBounds(level) + children.flatMap { it.allBounds(level + 1) }

        override val isLeaf: Boolean get() = children.firstOrNull() is Leaf

        override fun add(value: Node): Node {
            assert(value != this)
            uncheckedAdd(value)
            return if (children.size > M) {
                split()
            } else {
                bounds = bounds.including(value.bounds)
                this
            }
        }

        private fun uncheckedAdd(value: Node) {
            var bestChild: Node? = null
            var bestGrowth = Double.POSITIVE_INFINITY
            var bestVolume = Double.POSITIVE_INFINITY
            for (child in children) {
                if (!child.isLeaf || value is Leaf) {
                    val oldVolume = child.bounds.volume
                    val volume = child.bounds.including(value.bounds).volume
                    val growth = volume - oldVolume
                    if (growth < bestGrowth || (growth == bestGrowth && volume < bestVolume)) {
                        bestChild = child
                        bestGrowth = growth
                        bestVolume = volume
                    }
                }
            }
            if (bestChild != null) {
                children.add(bestChild.add(value))
            } else {
                // Empty root or node while inserting children of removing child node.
                children.add(value)
            }
        }

        override fun remove(value: Node): Set<Node>? {
            if (bounds.intersects(value.bounds)) {
                for (child in children.toList()) {
                    val change = child.remove(value)
                    if (change != null) {
                        if (change.contains(child)) {
                            // Underflow after removing node or child was the node to remove.
                            children.remove(child)
                            if (child is NonLeaf) {
                                for (c in child.children) {
                                    uncheckedAdd(c)
                                }
                                if (children.size > M) {
                                    // Escalate overflow.
                                    return setOf(split())
                                }
                            } else {
                                assert(child == value)
                            }
                            if (children.size < m) {
                                // Escalate underflow.
                                return setOf(this)
                            }
                            // Done handling tree adjustment, bubble result up.
                            bounds = Rectangle.around(children)
                            return setOf(value)
                        } else if (change.contains(value)) {
                            // Removal, bubble result up.
                            bounds = Rectangle.around(children)
                            return setOf(value)
                        } else {
                            // Overflow due to split after underflow.
                            val changeNode = change.first()
                            assert(changeNode is NonLeaf)
                            uncheckedAdd(changeNode)
                            return if (children.size > M) {
                                // Escalate overflow.
                                setOf(split())
                            } else {
                                // Done handling tree adjustment, bubble result up.
                                bounds = Rectangle.around(children)
                                setOf(value)
                            }
                        }
                    }
                }
            }
            return null
        }

        override fun query(query: Rectangle): List<Data> =
            if (query.intersects(bounds)) {
                children.flatMap { child -> child.query(query) }
            } else emptyList()

        private fun split(): NonLeaf {
            val values = children.toTypedArray()
            var seed1: Node? = null
            var seed2: Node? = null
            var worst = Double.NEGATIVE_INFINITY
            for (i in values.indices) {
                val si = values[i]
                for (j in i + 1 until values.size) {
                    val sj = values[j]
                    val vol1 = si.bounds.volume
                    val vol2 = sj.bounds.volume
                    val vol = si.bounds.including(sj.bounds).volume
                    val d = vol - vol1 - vol2
                    if (d > worst) {
                        seed1 = si
                        seed2 = sj
                        worst = d
                    }
                }
            }

            val s1 = seed1!!
            val s2 = seed2!!

            val r1 = SplitResult(mutableSetOf(s1), s1.bounds)
            val r2 = SplitResult(mutableSetOf(s2), s2.bounds)

            val list = values.toMutableSet()
            list.remove(s1)
            list.remove(s2)

            while (list.isNotEmpty()) {
                when {
                    m - r1.set.size >= list.size -> {
                        list.forEach { r1.add(it) }
                        list.clear()
                    }
                    m - r2.set.size >= list.size -> {
                        list.forEach { r2.add(it) }
                        list.clear()
                    }
                    else -> {
                        var bestValue: Node? = null
                        var r = r1
                        var best = Double.NEGATIVE_INFINITY
                        for (value in list) {
                            val newVol1 = r1.volumeIncluding(value)
                            val newVol2 = r2.volumeIncluding(value)
                            val growth1 = newVol1 - r1.volume
                            val growth2 = newVol2 - r2.volume
                            val d = abs(growth2 - growth1)
                            if (d > best) {
                                bestValue = value
                                r = if (growth1 < growth2 || (growth1 == growth2 && newVol1 < newVol2)) r1 else r2
                                best = d
                            }
                        }
                        list.remove(bestValue!!)
                        r.add(bestValue)
                    }
                }
            }

            children.clear()
            children.addAll(r1.set)
            bounds = r1.bounds

            val LL = NonLeaf()
            LL.children.addAll(r2.set)
            LL.bounds = r2.bounds
            return LL
        }
    }

    private inner class Leaf(val data: Data, point: Point) : Node() {
        override val bounds = Rectangle(point, point)

        override fun add(value: Node): Node = value

        override fun remove(value: Node): Set<Node>? =
            if (value == this) setOf(this) else null

        override fun query(query: Rectangle): List<Data> =
            if (query.intersects(bounds)) listOf(data) else emptyList()
    }

    private class Point(val x: Double, val y: Double, val z: Double) {
        constructor(p: Triple<Double, Double, Double>) : this(p.first, p.second, p.third)

        fun min(other: Point) = Point(min(x, other.x), min(y, other.y), min(z, other.z))

        fun max(other: Point) = Point(max(x, other.x), max(y, other.y), max(z, other.z))

        fun asTuple() = Triple(x, y, z)

        companion object {
            val NegativeInfinity = Point(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY)
            val PositiveInfinity = Point(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
        }
    }

    private class Rectangle(val min: Point, val max: Point) {
        fun including(value: Rectangle) = Rectangle(value.min.min(min), value.max.max(max))

        fun intersects(value: Rectangle) =
            value.min.x <= max.x && value.min.y <= max.y && value.min.z <= max.z &&
                value.max.x >= min.x && value.max.y >= min.y && value.max.z >= min.z

        val volume: Double
            get() {
                val sx = max.x - min.x
                val sy = max.y - min.y
                val sz = max.z - min.z
                return sx * sy * sz
            }

        fun asTuple() = min.asTuple() to max.asTuple()

        companion object {
            fun around(values: Iterable<RTree<*>.Node>): Rectangle {
                var min = Point.PositiveInfinity
                var max = Point.NegativeInfinity
                for (value in values) {
                    min = value.bounds.min.min(min)
                    max = value.bounds.max.max(max)
                }
                return Rectangle(min, max)
            }
        }
    }

    private class SplitResult<Data>(val set: MutableSet<RTree<Data>.Node>, var bounds: Rectangle) {
        fun add(value: RTree<Data>.Node) {
            set.add(value)
            bounds = bounds.including(value.bounds)
        }

        val volume: Double get() = bounds.volume

        fun volumeIncluding(value: RTree<*>.Node): Double = bounds.including(value.bounds).volume
    }
}
