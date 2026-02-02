package li.cil.oc.client

import com.google.common.base.Strings
import li.cil.oc.OpenComputers
import li.cil.oc.api.detail.ManualAPI
import li.cil.oc.api.manual.ContentProvider
import li.cil.oc.api.manual.ImageProvider
import li.cil.oc.api.manual.ImageRenderer
import li.cil.oc.api.manual.PathProvider
import li.cil.oc.api.manual.TabIconRenderer
import li.cil.oc.common.GuiType
import li.cil.oc.util.Stack
import li.cil.oc.client.gui.Manual as GuiManual
import net.minecraft.client.Minecraft
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.fml.common.FMLCommonHandler

object Manual: ManualAPI {
  const val LanguageKey = "%LANGUAGE%"
  const val FallbackLanguage = "en_us"

  class History(val path: String, var offset: Int = 0)

  class Tab(val renderer: TabIconRenderer, val tooltip: String?, val path: String)

  val tabs = mutableListOf<Tab>()

  private val pathProviders = mutableListOf<PathProvider>()
  private val contentProviders = mutableListOf<ContentProvider>()
  private val imageProviders = mutableListOf<Pair<String, ImageProvider>>()

  internal val history = Stack<History>()

  init { reset() }

  override fun addTab(renderer: TabIconRenderer, tooltip: String, path: String) {
    tabs += Tab(renderer, tooltip, path)
    if (tabs.size > 7) {
      OpenComputers.log.warn("Gosh I'm popular! Too many tabs were added to the OpenComputers in-game manual, so some won't be shown. In case this actually happens, let me know and I'll look into making them scrollable or something...")
    }
  }

  override fun addProvider(provider: PathProvider) {
    pathProviders.add(provider)
  }

  override fun addProvider(provider: ContentProvider) {
    contentProviders.add(provider)
  }

  override fun addProvider(prefix: String, provider: ImageProvider) {
    imageProviders.add((if (Strings.isNullOrEmpty(prefix)) "" else prefix + ":") to provider)
  }

  override fun pathFor(stack: ItemStack): String? {
    for (provider in pathProviders) {
      try {
        return provider.pathFor(stack) ?: continue
      } catch (e: Exception) {
          OpenComputers.log.warn("A path provider threw an error when queried with an item.", e)
      }
    }
    return null
  }

  override fun pathFor(world: World, pos: BlockPos): String? {
    pathProviders
    for (provider in pathProviders) {
      try {
        return provider.pathFor(world, pos) ?: continue
      } catch (e: Exception) {
        OpenComputers.log.warn("A path provider threw an error when queried with a block.", e)
      }
    }
    return null
  }

  override fun contentFor(path: String): Iterable<String>? {
    val cleanPath = com.google.common.io.Files.simplifyPath(path)
    val language = try {
      FMLCommonHandler.instance().currentLanguage
    } catch (t: Exception) {
      OpenComputers.log.warn("The game threw an error when querying current language.", t)
      FallbackLanguage
    }
    return contentForWithRedirects(cleanPath.replace(LanguageKey, language))
      ?: contentForWithRedirects(cleanPath.replace(LanguageKey, FallbackLanguage))
  }

  override fun imageFor(href: String): ImageRenderer? {
    for ((prefix, provider) in Manual.imageProviders.asReversed()) {
      if (href.startsWith(prefix)) {
        try {
          return provider.getImage(href.removePrefix(prefix)) ?: continue
        } catch (e: Exception) {
          OpenComputers.log.warn("An image provider threw an error when queried.", e)
        }
      }
    }
    return null
  }

  override fun openFor(player: EntityPlayer) {
    if (player.entityWorld.isRemote) {
      player.openGui(OpenComputers.INSTANCE, GuiType.Manual.id, player.entityWorld, 0, 0, 0)
    }
  }

  override fun reset() {
    history.clear()
    history.push(History("$LanguageKey/index.md"))
  }

  override fun navigate(path: String) {
    when (val manual = Minecraft.getMinecraft().currentScreen) {
      is GuiManual -> manual.pushPage(path)
      else -> history.push(History(path))
    }
  }

  fun makeRelative(path: String, base: String): String =
    if (path.startsWith("/")) path
    else {
      val splitAt = base.lastIndexOf('/')
      if (splitAt >= 0) base.slice(0 until splitAt) + "/" + path
      else path
    }

  private tailrec fun contentForWithRedirects(path: String, seen: List<String> = emptyList()): Iterable<String>? {
    if (seen.contains(path))
      return listOf("Redirection loop: ") + seen + listOf(path)

    val content = doContentLookup(path) ?: return null
    val line = content.firstOrNull()
    if (line?.startsWith("#redirect ") == true) {
      return contentForWithRedirects(makeRelative(line.substring("#redirect ".length), path), seen + path)
    }
    return content
  }

  private fun doContentLookup(path: String): Iterable<String>? {
    for (provider in contentProviders) {
      try {
        return provider.getContent(path)
      } catch (e: Exception) {
        OpenComputers.log.warn("A content provider threw an error when queried.", e)
      }
    }
    return null
  }
}
