package li.cil.oc.util

import java.util.concurrent.atomic.AtomicReference

/**
 * https://gist.github.com/viktorklang/1057513
 *
 * A thread-safe enum pattern for Kotlin, based on the Scala implementation.
 */
abstract class ScalaEnum<T : ScalaEnum<T>.Value> {
    private val _values = AtomicReference(listOf<T>())

    val values: List<T> get() = _values.get()

    // Adds an EnumVal to our storage, uses CAS to make sure it's thread safe, returns the ordinal
    @Tailrec
    protected fun addEnumVal(newVal: T): Int {
        val oldVec = _values.get()
        val newVec = oldVec + newVal
        return if (_values.compareAndSet(oldVec, newVec)) {
            newVec.indexOf(newVal)
        } else {
            addEnumVal(newVal)
        }
    }

    abstract inner class Value {
        val ordinal: Int = addEnumVal(this as T)

        abstract val name: String

        override fun toString(): String = name

        override fun equals(other: Any?): Boolean = this === other

        override fun hashCode(): Int = 31 * (this.javaClass.hashCode() + name.hashCode() + ordinal)
    }
}

// Helper annotation to indicate tail recursion (Kotlin doesn't have @tailrec like Scala)
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION)
annotation class Tailrec
