package net.kumajunk.libleaddon.features.impl.dungeon

import com.odtheking.odin.events.MessageEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.sendCommand
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import net.kumajunk.libleaddon.utils.addonMessage

object LeapAnnounce : Module(
    name = "Leap Announce(LA)",
    description = "Announces when you use Leap ability in dungeons."
) {
    private val leapedRegex = Regex("You have teleported to (\\w{1,16})!")

    init {
        on<MessageEvent.Chat> {
            if (!DungeonUtils.inDungeons) return@on

            leapedRegex.find(message)?.groupValues?.get(1)?.let {
                cancel()

                val playerClass = DungeonUtils.leapTeammates
                    .find { player -> player.name == it }
                    ?.clazz
                    ?: return@let

                sendCommand("pc Leaped to [${playerClass.name[0]}] $it!")
                addonMessage("§aLeaped to [${playerClass.name[0]}] $it!")
            }
        }
    }
}