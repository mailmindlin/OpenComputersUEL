package li.cil.oc.util

data class ScreenResolution(val width: Int, val height: Int) {
    val pixels: Int get() = width * height
}

internal infix fun Int.by(height: Int) = ScreenResolution(this, height)