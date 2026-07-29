package net.kumajunk.libleaddon.features.impl.dungeon

import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.LevelEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.sendCommand
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import net.kumajunk.libleaddon.utils.addonMessage

object ScoreMilestone : Module(
    name = "Score Milestone(LA)",
    description = "Announces when you reach 270 & 300 score in dungeons."
) {
    private val announceInParty by BooleanSetting(
        name = "Announce in Party",
        desc = "Announce score milestones in party chat."
    )
    var announced270 = false
    var announced300 = false

    init {
        on<TickEvent.End> {
            if (!DungeonUtils.inDungeons) return@on

            val score = DungeonUtils.score

            if (score >= 270 && !announced270) {
                addonMessage("§f270 Score reached! §b(${DungeonUtils.dungeonTime})")
                if (announceInParty) sendCommand("pc 270 Score reached! (${DungeonUtils.dungeonTime})")
                announced270 = true
            }

            if (score >= 300 && !announced300) {
                addonMessage("§f300 Score reached! §b(${DungeonUtils.dungeonTime})")
                if (announceInParty) sendCommand("pc 300 Score reached! (${DungeonUtils.dungeonTime})")
                announced300 = true
            }
        }

        on<LevelEvent.Load> {
            announced270 = false
            announced300 = false
        }
    }
}