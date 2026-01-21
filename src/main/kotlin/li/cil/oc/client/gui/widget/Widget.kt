package li.cil.oc.client.gui.widget

abstract class Widget {
    var owner: WidgetContainer? = null

    abstract val x: Int

    abstract val y: Int

    abstract val width: Int

    abstract val height: Int

    abstract fun draw()
}
