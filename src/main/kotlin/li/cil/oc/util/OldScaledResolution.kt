package li.cil.oc.util

import net.minecraft.client.Minecraft
import net.minecraft.util.math.MathHelper

class OldScaledResolution(minecraft: Minecraft, width: Int, height: Int) {
    val scaledWidth: Int
    val scaledHeight: Int

    init {
        var scaleFactor = 1
        var guiScale = minecraft.gameSettings.guiScale

        if (guiScale == 0) {
            guiScale = 1000
        }

        while (scaleFactor < guiScale && width / (scaleFactor + 1) >= 320 && height / (scaleFactor + 1) >= 240) {
            ++scaleFactor
        }

        if (minecraft.isUnicode && scaleFactor % 2 != 0 && scaleFactor != 1) {
            --scaleFactor
        }

        this.scaledWidth = MathHelper.ceil(width.toDouble() / scaleFactor.toDouble())
        this.scaledHeight = MathHelper.ceil(height.toDouble() / scaleFactor.toDouble())
    }
}
