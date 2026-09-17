package net.kumajunk.libleaddon.features.impl.boss

import com.odtheking.odin.clickgui.settings.impl.NumberSetting
import com.odtheking.odin.events.MessageEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.handlers.schedule
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils.getF7Phase
import com.odtheking.odin.utils.skyblock.dungeon.M7Phases
import net.kumajunk.libleaddon.utils.addonMessage
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player

object HidePlayerOnLeap : Module(
    name = "Hide Player On Leap(LA)",
    description = "Hides other players when you use the leap ability on Floor 7."
) {
    private val hideTime by NumberSetting(
        name = "Hide Time",
        default = 3.0,
        min = 1.0,
        max = 10.0,
        increment = 0.5,
        desc = "Time in seconds to hide players after leaping."
    )

    private val leapRegex = Regex("""You have teleported to (\w+)!""")

    private var hidePlayers = false

    @JvmStatic
    fun shouldRenderPlayer(entity: Entity): Boolean {
        if (!enabled) return true
        if (!hidePlayers) return true
        if (entity !is Player) return true
        if (entity == mc.player) return true
        return false
    }

    init {
        on<MessageEvent.Chat> {
            val phase = getF7Phase()
            if (phase != M7Phases.P3 && phase != M7Phases.P4) return@on
            val msg = message.noControlCodes
            if (!leapRegex.containsMatchIn(msg)) return@on

            if (!enabled) return@on

            hidePlayers = true
            addonMessage("Hiding Players")

            schedule((hideTime * 20).toInt()) {
                hidePlayers = false
                addonMessage("Showing Players")
            }
        }
    }
}