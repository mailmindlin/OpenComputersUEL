package li.cil.oc.util

internal class Stack<T>(private val inner: MutableList<T> = mutableListOf()): Iterable<T> by inner {
    fun clear() {
        this.inner.clear()
    }
    fun push(item: T) {
        this.inner.add(item)
    }
    fun pop(): T = this.inner.removeLast()
    fun tryPop(): T? = this.inner.removeLastOrNull()
    fun peek(): T? = this.inner.lastOrNull()
    operator fun contains(element: T) = this.inner.contains(element)
    fun isNotEmpty(): Boolean = this.inner.isNotEmpty()
    fun isEmpty(): Boolean = this.inner.isEmpty()
    val size: Int
        get() = this.inner.size
    val top: T
        get() = this.inner.last()
}