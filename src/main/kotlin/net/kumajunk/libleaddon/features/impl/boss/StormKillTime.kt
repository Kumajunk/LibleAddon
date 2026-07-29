package net.kumajunk.libleaddon.features.impl.boss

import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.clickgui.settings.impl.SelectorSetting
import com.odtheking.odin.events.ChatPacketEvent
import com.odtheking.odin.events.LevelEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.alert
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.sendCommand
import com.odtheking.odin.utils.toFixed
import net.kumajunk.libleaddon.utils.addonMessage

object StormKillTime : Module(
    name = "Storm Kill Time(LA)",
    description = "test"
) {
    val methodList = listOf("type1", "type2")
    private val displayFormat by SelectorSetting(
        name = "Display format",
        default = "type1",
        options = methodList,
        desc = "Choose the counting format for displaying kill times."
    )

    private val announceInParty by BooleanSetting(
        name = "Announce in Party",
        desc = "Whether Storm kill times are announced in the party."
    )
    // Stormのセリフパターン (Crush攻撃検出)
    private val crushPattern = Regex("""^\[BOSS] Storm: (Ouch, that hurt!|Oof)$""")

    // type1
    private val crushTime: MutableList<Long> = mutableListOf()
    private val totalTimes: MutableList<Long> = mutableListOf()

    // type2
    private var stormStartTime = 0L

    init {
        // Crushパターン検出でタイマー開始
        on<ChatPacketEvent> {
            val msg = value.noControlCodes
            if (methodList[displayFormat] == "type1") {
                if (crushPattern.matches(msg)) {
                    crushTime.add(System.currentTimeMillis())
                }
                if (msg.contains("Storm is enraged")) {
                    val enragedTime = System.currentTimeMillis()
                    val crushTime = crushTime.last()
                    val totalTime = enragedTime - crushTime
                    totalTimes.add(totalTime)
                    addonMessage("§fStorm enraged in §b${(totalTime / 1000.0).toFixed(3)}s§f!")
                    if (announceInParty) {
                        sendCommand("pc Storm enraged in ${(totalTime / 1000.0).toFixed(3)}s!")
                    }
                }
                if (msg.contains("I should have known that I stood no chance")) {
                    val killTime = System.currentTimeMillis()
                    val crushTime = crushTime.last()
                    val totalTime = killTime - crushTime
                    totalTimes.add(totalTime)
                    addonMessage("§fStorm killed in §b${(totalTime / 1000.0).toFixed(3)}s§f! Took §b${((totalTimes[0] + totalTimes[1]) / 1000.0).toFixed(3)}s §ftotal.")
                    if (announceInParty) {
                        sendCommand("pc Storm killed in ${(totalTime / 1000.0).toFixed(3)}s! Took ${((totalTimes[0] + totalTimes[1]) / 1000.0).toFixed(3)}s total.")
                    }
                }
            } else if (methodList[displayFormat] == "type2") {
                if (msg.contains("[BOSS] Storm: Pathetic Maxor, just like expected.")) {
                    stormStartTime = System.currentTimeMillis()
                }
                if (msg.contains("Storm is enraged")) {
                    val enragedTime = System.currentTimeMillis()
                    val totalTime = enragedTime - stormStartTime
                    addonMessage("§fStorm enraged in §b${(totalTime / 1000.0).toFixed(2)}s§f!")
                    if (announceInParty) {
                        sendCommand("pc Storm enraged in ${(totalTime / 1000.0).toFixed(2)}s!")
                    }
                    alert((totalTime / 1000.0).toFixed(2))
                }
                if (msg.contains("I should have known that I stood no chance")) {
                    val killTime = System.currentTimeMillis()
                    val totalTime = killTime - stormStartTime
                    addonMessage("§fStorm killed in §b${(totalTime / 1000.0).toFixed(2)}s§f!")
                    if (announceInParty) {
                        sendCommand("pc Storm killed in ${(totalTime / 1000.0).toFixed(2)}s!")
                    }
                    alert((totalTime / 1000.0).toFixed(2))
                }
            }
        }

        // ワールドアンロード時にリセット
        on<LevelEvent.Unload> {
            crushTime.clear()
            totalTimes.clear()
            stormStartTime = 0L
        }
    }
}