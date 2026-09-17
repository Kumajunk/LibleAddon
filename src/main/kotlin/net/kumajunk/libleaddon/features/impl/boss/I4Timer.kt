package net.kumajunk.libleaddon.features.impl.boss

import com.odtheking.odin.events.MessageEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.sendCommand
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import net.kumajunk.libleaddon.utils.addonMessage
import java.util.regex.Pattern

object I4Timer : Module(
    name = "I4 Timer(LA)",
    description = "Measures the time for I4 completion."
) {
    private val i4TimerParty by BooleanSetting("Announce to Party", false, "Announce I4 time to party chat.")

    private var i4Start: Long = 0
    private val goldorBossPattern = Pattern.compile("^\\[BOSS] Goldor: Who dares trespass into my domain\\?$")
    private val devicePattern = Regex("(\\w{1,16}) (activated|completed) a (lever|device|terminal)! \\((\\d)/([78])\\)")

    init {
        on<MessageEvent.Chat> {
            val message = message.noControlCodes

            if (goldorBossPattern.matcher(message).matches()) {
                i4Start = System.currentTimeMillis()
                return@on
            }

            if (i4Start == 0L) return@on

            val match = devicePattern.find(message) ?: return@on

            val playerName = match.groupValues[1]
            val deviceType = match.groupValues[3]

            val toPlayer = DungeonUtils.dungeonTeammates
                .find { it.name == playerName } ?: return@on

            if (deviceType != "device") return@on

            if (toPlayer.clazz.name.equals("Berserk", true)) {
                val duration = (System.currentTimeMillis() - i4Start) / 1000.0
                val formattedTime = String.format("%.2f", duration)

                addonMessage("I4 took §b${formattedTime}s")

                if (i4TimerParty) {
                    sendCommand("pc I4 took ${formattedTime}s")
                }

                i4Start = 0L
            }
        }
    }
}
