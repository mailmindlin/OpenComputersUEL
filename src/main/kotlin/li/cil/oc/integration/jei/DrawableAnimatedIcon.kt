package li.cil.oc.integration.jei

import mezz.jei.api.gui.IDrawableAnimated
import mezz.jei.api.gui.ITickTimer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * Used to simulate an animated texture.
 *
 * @author Vexatos
 */
class DrawableAnimatedIcon(
    private val resourceLocation: ResourceLocation,
    private val u: Int,
    private val v: Int,
    private val width: Int,
    private val height: Int,
    private val textureWidth: Int,
    private val textureHeight: Int,
    private val tickTimer: ITickTimer,
    private val uOffset: Int,
    private val vOffset: Int,
    private val paddingTop: Int = 0,
    private val paddingBottom: Int = 0,
    private val paddingLeft: Int = 0,
    private val paddingRight: Int = 0
) : IDrawableAnimated {

    override fun getWidth(): Int = width + paddingLeft + paddingRight

    override fun getHeight(): Int = height + paddingTop + paddingBottom

    @SideOnly(Side.CLIENT)
    override fun draw(minecraft: Minecraft) {
        draw(minecraft, 0, 0)
    }

    @SideOnly(Side.CLIENT)
    override fun draw(minecraft: Minecraft, xOffset: Int, yOffset: Int) {
        val animationValue = tickTimer.value

        val uOffsetTotal = uOffset * animationValue
        val vOffsetTotal = vOffset * animationValue

        minecraft.textureManager.bindTexture(resourceLocation)
        val x = xOffset + this.paddingLeft
        val y = yOffset + this.paddingTop
        val u = this.u + uOffsetTotal
        val v = this.v + vOffsetTotal
        Gui.drawModalRectWithCustomSizedTexture(x, y, u.toFloat(), v.toFloat(), width, height, textureWidth.toFloat(), textureHeight.toFloat())
    }
}
