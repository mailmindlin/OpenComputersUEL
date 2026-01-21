package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.client.Textures
import li.cil.oc.client.PacketSender as ClientPacketSender
import li.cil.oc.common.item.data.DriveData
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.item.ItemStack

class Drive(
    playerInventory: InventoryPlayer,
    val driveStack: () -> ItemStack
) : GuiScreen(), li.cil.oc.client.gui.traits.Window {

    override val windowHeight = 120

    override val backgroundImage get() = Textures.GUI.Drive

    protected var managedButton: ImageButton? = null
    protected var unmanagedButton: ImageButton? = null
    protected var lockedButton: ImageButton? = null

    override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            0 -> {
                ClientPacketSender.sendDriveMode(unmanaged = false)
                DriveData.setUnmanaged(driveStack(), unmanaged = false)
            }
            1 -> {
                ClientPacketSender.sendDriveMode(unmanaged = true)
                DriveData.setUnmanaged(driveStack(), unmanaged = true)
            }
            2 -> {
                ClientPacketSender.sendDriveLock()
                DriveData.lock(driveStack(), playerInventory.player)
            }
        }
        updateButtonStates()
    }

    fun updateButtonStates() {
        val data = DriveData(driveStack())
        unmanagedButton?.toggled = data.isUnmanaged
        managedButton?.toggled = !(unmanagedButton?.toggled ?: false)
        lockedButton?.toggled = data.isLocked
        lockedButton?.enabled = !data.isLocked
    }

    override fun initGui() {
        super.initGui()
        managedButton = ImageButton(
            0, guiLeft + 11, guiTop + 11, 74, 18,
            Textures.GUI.ButtonDriveMode,
            text = Localization.Drive.Managed,
            textColor = 0x608060,
            canToggle = true
        )
        unmanagedButton = ImageButton(
            1, guiLeft + 91, guiTop + 11, 74, 18,
            Textures.GUI.ButtonDriveMode,
            text = Localization.Drive.Unmanaged,
            textColor = 0x608060,
            canToggle = true
        )
        lockedButton = ImageButton(
            2, guiLeft + 11, guiTop + windowHeight - 42, 44, 18,
            Textures.GUI.ButtonDriveMode,
            text = Localization.Drive.ReadOnlyLock,
            textColor = 0x608060,
            canToggle = true
        )
        add(buttonList, managedButton!!)
        add(buttonList, unmanagedButton!!)
        add(buttonList, lockedButton!!)
        updateButtonStates()
    }

    override fun updateScreen() {
        super.updateScreen()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, dt: Float) {
        super.drawScreen(mouseX, mouseY, dt)
        fontRenderer.drawSplitString(
            Localization.Drive.Warning,
            guiLeft + 11, guiTop + 37,
            xSize - 20, 0x404040
        )
        fontRenderer.drawSplitString(
            Localization.Drive.LockWarning,
            guiLeft + 61, guiTop + windowHeight - 48,
            xSize - 68, 0x404040
        )
    }
}
