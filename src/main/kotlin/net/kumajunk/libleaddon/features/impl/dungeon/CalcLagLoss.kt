package net.kumajunk.libleaddon.features.impl.dungeon

import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.LevelEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.events.core.onReceive
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.noControlCodes
import net.kumajunk.libleaddon.utils.addonMessage
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket

object CalcLagLoss : Module(
    name = "Calc Lag Loss(LA)",
    description = "Calculates lag loss in dungeons."
) {
    var startTime = 0L
    var finishTime = 0L
    var notLagTime = 0L
    val regex = Regex("""\s*Team Score:.*""")

    init {
        on<LevelEvent.Load>() {
            startTime = 0L
            finishTime = 0L
            notLagTime = 0L
        }

        onReceive<ClientboundSystemChatPacket> {
            val msg = content.string.noControlCodes
            if (msg.contains("Mort:") && msg.contains("I found this map")) {
                startTime = System.currentTimeMillis()
                return@onReceive
            }

            val isMatched = regex.containsMatchIn(msg)
            if (isMatched) {
                finishTime = System.currentTimeMillis()
                val totalTime = finishTime - startTime
                val lagLoss = totalTime - notLagTime
                val lagLossSeconds = lagLoss.toDouble() / 1000.0
                val lagLossPercentage = (lagLoss.toDouble() / totalTime.toDouble()) * 100.0

                addonMessage("§aApproximately §f%.2fs §alost to lag§8(%.2f%%)".format(lagLossSeconds, lagLossPercentage))
                return@onReceive
            }
        }

        on<TickEvent.Server> {
            if (startTime == 0L || finishTime != 0L) return@on
            notLagTime += 50L
        }
    }
}