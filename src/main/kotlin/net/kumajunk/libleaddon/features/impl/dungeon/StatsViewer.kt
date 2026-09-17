package net.kumajunk.libleaddon.features.impl.dungeon

import com.odtheking.odin.OdinMod.scope
import com.odtheking.odin.events.MessageEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.network.hypixelapi.RequestUtils
import com.odtheking.odin.utils.noControlCodes
import kotlinx.coroutines.launch
import net.kumajunk.libleaddon.commands.fetchCataStats

object StatsViewer : Module(
    name = "Stats Viewer(LA)",
    description = "Show dungeon stats in chat when people join with PartyFinder."
) {
    private val pfRegex = Regex("^Party Finder > (?:\\[.{1,7}])? ?(.{1,16}) joined the dungeon group! \\(.*\\)$")

    init {
        on<MessageEvent.Chat> {
            val msg = message.noControlCodes
            val (name) = pfRegex.find(msg)?.destructured ?: return@on
            if (name == mc.player?.name?.string) return@on
            scope.launch {
                val profile = RequestUtils.getProfile(name)
                fetchCataStats(profile, true)
            }
        }
    }
}