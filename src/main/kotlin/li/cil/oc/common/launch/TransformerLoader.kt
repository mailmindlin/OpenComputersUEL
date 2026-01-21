package li.cil.oc.common.launch

import li.cil.oc.common.asm.ClassTransformer
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.SortingIndex
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions

@SortingIndex(1001)
@TransformerExclusions("li.cil.oc.common.asm")
class TransformerLoader : IFMLLoadingPlugin {
    @JvmField
    val instance: TransformerLoader = this

    override fun getModContainerClass(): String = "li.cil.oc.common.launch.CoreModContainer"

    override fun getASMTransformerClass(): Array<String> = arrayOf(ClassTransformer::class.java.name)

    override fun getAccessTransformerClass(): String? = null

    override fun getSetupClass(): String? = null

    override fun injectData(data: MutableMap<String, Any>) {}
}
