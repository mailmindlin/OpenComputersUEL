package li.cil.oc.client.gui.widget

interface WidgetContainer {
    val widgets: MutableList<Widget>

    fun <T : Widget> addWidget(widget: T): T {
        widgets.add(widget)
        widget.owner = this
        return widget
    }

    val windowX: Int
        get() = 0

    val windowY: Int
        get() = 0

    val windowZ: Float
        get() = 0f

    fun drawWidgets() {
        widgets.forEach { it.draw() }
    }
}
