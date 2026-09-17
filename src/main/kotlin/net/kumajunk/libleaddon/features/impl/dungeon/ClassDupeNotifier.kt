package net.kumajunk.libleaddon.features.impl.dungeon

import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.clickgui.settings.impl.ColorSetting
import com.odtheking.odin.clickgui.settings.impl.DropdownSetting
import com.odtheking.odin.events.MessageEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.*
import com.odtheking.odin.utils.handlers.schedule
import com.odtheking.odin.utils.render.textDim
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import net.kumajunk.libleaddon.utils.ScoreboardUtils.getDungeonPlayerClasses
import net.kumajunk.libleaddon.utils.addonMessage

object ClassDupeNotifier : Module(
    name = "Class Dupe Notifier(LA)",
    description = "Notifies you when a class duplication is detected in dungeons."
) {
    private val allowMageDupe = +BooleanSetting(
        name = "Allow Mage Dupe",
        default = false,
        desc = "Whether to allow duplicate Mages without notification."
    )
    private val partyNotify by BooleanSetting(
        name = "Notify to Party",
        default = true,
        desc = "Sends a message to party chat if someone fails to warp."
    )
    private val soundDropdown by DropdownSetting("Sound Setting Dropdown")
    private val soundSettings = createSoundSettings("Notification Sound", "block.note_block.pling") { soundDropdown }
    private val hud by HUD(name, desc = "Displays duplicate classes Announcement on HUD.", false) {
        if (!it && !drawHUD) return@HUD 0 to 0
        textDim("DUPLICATE CLASS DETECTED", 0, 0, color)
    }
    private val color by ColorSetting(
        name = "HUD Color",
        default = Colors.MINECRAFT_RED,
        desc = "Color of the duplicate class announcement on the HUD."
    )

    var drawHUD = false

    private fun checkDupeClass() : List<String> {
        val clazzList = getDungeonPlayerClasses().values.map { it.className }
        val counts = clazzList
            .groupingBy { it }
            .eachCount()
        
        val duplicates = counts.filter { it.value > 1 }.keys.toMutableList()
        if (allowMageDupe.value) {
            duplicates.remove("Mage")
        }
        
        return duplicates
    }

    init {
        on<MessageEvent.Chat> {
            if (!DungeonUtils.inDungeons) return@on
            val msg = message.noControlCodes
            if (msg.contains("Starting in") && !msg.contains("1")) {
                val classes = checkDupeClass()
                if (classes.isNotEmpty()) {
                    drawHUD = true
                    playSoundSettings(soundSettings())
                    val dupeNames = classes.joinToString(", ")
                    if (partyNotify) sendCommand("pc Duplicate class: $dupeNames")
                    addonMessage("§cDuplicate classes detected: §b$dupeNames")
                    schedule(20) { drawHUD = false}
                    return@on
                } else {
                    addonMessage("§aNo duplicate classes found.")
                    return@on
                }
            }
        }

    }
}