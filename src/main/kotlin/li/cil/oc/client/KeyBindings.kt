package li.cil.oc.client

import li.cil.oc.OpenComputers
import net.minecraft.client.settings.GameSettings
import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.fml.client.FMLClientHandler
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse

internal object KeyBindings {
  private val keyBindingChecks = arrayOf(this::isKeyBindingPressedVanilla);
  private val keyBindingNameGetters = arrayOf(this::getKeyBindingNameVanilla);
  // val keyBindingNameGetters = mutable.ArrayBuffer(getKeyBindingNameVanilla _)

  val showExtendedTooltips: Boolean get() = isKeyBindingPressed(extendedTooltip)
  val isPastingClipboard: Boolean get() = isKeyBindingPressed(clipboardPaste)

  fun getKeyBindingName(keyBinding: KeyBinding): String = keyBindingNameGetters
    .firstNotNullOfOrNull { it(keyBinding) }
    ?: "???"

  fun isKeyBindingPressed(keyBinding: KeyBinding): Boolean = keyBindingChecks.all { it(keyBinding) }

  private fun getKeyBindingNameVanilla(keyBinding: KeyBinding): String? =
    try {
      GameSettings.getKeyDisplayString(keyBinding.keyCode)
    } catch(_: Exception) {
      null
    }

  private fun isKeyBindingPressedVanilla(keyBinding: KeyBinding): Boolean = try {
    if (keyBinding.keyCode < 0)
      Mouse.isCreated() && Mouse.isButtonDown(keyBinding.keyCode + 100)
    else
      Keyboard.isCreated() && Keyboard.isKeyDown(keyBinding.keyCode)
  } catch (_: Exception) {
    false
  }

  val extendedTooltip: KeyBinding get() = FMLClientHandler.instance().client.gameSettings.keyBindSneak

  val clipboardPaste = KeyBinding("key.clipboardPaste", Keyboard.KEY_INSERT, OpenComputers.Name)
}
