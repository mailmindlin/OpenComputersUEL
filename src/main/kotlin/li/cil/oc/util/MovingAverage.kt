package li.cil.oc.util

class MovingAverage(val size: Int) {
    private val data = IntArray(size) { 0 }
    private var head = 0
    private var cachedAverage = 0
    private var dirty = true

    fun get(): Int {
        if (dirty) {
            cachedAverage = data.sum() / size
            dirty = false
        }
        return cachedAverage
    }

    operator fun invoke(): Int = get()

    fun add(value: Int): MovingAverage {
        data[head] = value
        head = (head + 1) % size
        dirty = true
        return this
    }

    operator fun plusAssign(value: Int) {
        add(value)
    }
}
