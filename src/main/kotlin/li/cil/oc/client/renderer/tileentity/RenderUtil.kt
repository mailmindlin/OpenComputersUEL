package li.cil.oc.client.renderer.tileentity

object RenderUtil {
    fun shouldShowErrorLight(hash: Int): Boolean {
        val time = System.currentTimeMillis() + hash
        val timeSlice = time / 500
        return timeSlice % 2 == 0L
    }
}
