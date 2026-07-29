package net.kumajunk.libleaddon.features.impl.dungeon

import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.LevelEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import net.kumajunk.libleaddon.utils.addonMessage

object AutoPotionBag : Module(
    name = "Auto Open Potion Bag(LA)",
    description = "Automatically open the potion bag when entering a dungeon."
) {
    private var bagOpened = false

    init {
        on<LevelEvent.Load> {
            bagOpened = false
        }

        on<TickEvent.End> {
            if (bagOpened) return@on
            
            if (mc.screen != null) return@on
            
            if (!DungeonUtils.inDungeons) return@on
            
            addonMessage("§aOpening Potion Bag.")
            mc.player?.connection?.sendCommand("potionbag")
            bagOpened = true
        }
    }
}