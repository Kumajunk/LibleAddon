package net.kumajunk.libleaddon.features.impl.dungeon

import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.clickgui.settings.impl.ColorSetting
import com.odtheking.odin.clickgui.settings.impl.DropdownSetting
import com.odtheking.odin.events.MessageEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.Colors
import com.odtheking.odin.utils.createSoundSettings
import com.odtheking.odin.utils.handlers.schedule
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.playSoundSettings
import com.odtheking.odin.utils.render.textDim
import com.odtheking.odin.utils.sendCommand
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import net.kumajunk.libleaddon.utils.ScoreboardUtils.getDungeonPlayerClasses
import net.kumajunk.libleaddon.utils.addonMessage

object WarpSuccessNotifier : Module(
    name = "Warp Success Notifier(LA)",
    description = "Notify when someone has not warped into the dungeon"
){

    private val partyNotify by BooleanSetting(
        name = "Notify to Party",
        default = true,
        desc = "Sends a message to party chat if someone fails to warp."
    )
    private val hud by HUD(name, desc = "Displays warp is not success Announcement on HUD.", false) {
        if (!it && !drawHUD) return@HUD 0 to 0
        textDim("SOMEONE IS NOT WARPED", 0, 0, color)
    }
    private val color by ColorSetting(
        name = "HUD Color",
        default = Colors.MINECRAFT_RED,
        desc = "Color of the HUD text."
    )

    private val soundDropDown by DropdownSetting("Sound")
    private val soundSettings = createSoundSettings("Notification Sound Setting", "entity.experience_orb.pickup") { soundDropDown }

    private var drawHUD = false

    init {
        on<MessageEvent.Chat> {
            if (!DungeonUtils.inDungeons) return@on
            val msg = message.noControlCodes
            if (msg.contains("Starting in") && msg.contains("4")) {
                val count = getDungeonPlayerClasses().size
                if (count < 5) {
                    playSoundSettings(soundSettings())
                    if (partyNotify) sendCommand("pc SOMEONE IS NOT WARPED!!!")
                    drawHUD = true
                    schedule(40) {
                        drawHUD = false
                    }
                } else {
                    addonMessage("Warp is successful!")
                }
            }
        }
    }
}