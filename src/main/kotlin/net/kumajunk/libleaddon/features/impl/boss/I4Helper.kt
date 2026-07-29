package net.kumajunk.libleaddon.features.impl.boss

import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.clickgui.settings.impl.ColorSetting
import com.odtheking.odin.events.BlockUpdateEvent
import com.odtheking.odin.events.ChatPacketEvent
import com.odtheking.odin.events.RenderEvent
import com.odtheking.odin.events.LevelEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.events.core.onReceive
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.Color.Companion.withAlpha
import com.odtheking.odin.utils.Colors
import com.odtheking.odin.utils.alert
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.render.drawFilledBox
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import com.odtheking.odin.utils.skyblock.dungeon.M7Phases
import net.kumajunk.libleaddon.utils.addonMessage
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB

object I4Helper : Module(
    name = "I4 Helper(LA)",
    description = "Highlights blocks for the 4th device (I4) in Floor 7."
) {
    private val redColor by ColorSetting("Incomplete Color", Colors.MINECRAFT_RED.withAlpha(0.5f), true, desc = "Highlight color for incomplete (terracotta) blocks.")
    private val greenColor by ColorSetting("Complete Color", Colors.MINECRAFT_GREEN.withAlpha(0.5f), true, desc = "Highlight color for complete (emerald) blocks.")
    private val hideTitles by BooleanSetting("Hide Titles", true, desc = "Hides unnecessary titles displayed when a device or terminal is completed.")
    private val alertDone by BooleanSetting("Alert on Done", true, desc = "Sends a chat message and shows a title when you complete the device.")

    private val devicePositions = listOf(
        BlockPos(68, 130, 50), BlockPos(66, 130, 50), BlockPos(64, 130, 50),
        BlockPos(68, 128, 50), BlockPos(66, 128, 50), BlockPos(64, 128, 50),
        BlockPos(68, 126, 50), BlockPos(66, 126, 50), BlockPos(64, 126, 50)
    )

    private val roomBoundingBox = AABB(62.8, 120.0, 34.8, 64.2, 140.0, 36.2)
    private val highlighted = IntArray(9) { 0 } // 0: none, 1: red, 2: green
    private var notified = false

    init {
        on<BlockUpdateEvent> {
            if (!isInPosition || DungeonUtils.getF7Phase() != M7Phases.P3) return@on

            val index = devicePositions.indexOf(pos)
            if (index == -1) return@on

            when (updated.block) {
                Blocks.BLUE_TERRACOTTA -> highlighted[index] = 1
                Blocks.EMERALD_BLOCK -> highlighted[index] = 2
                else -> highlighted[index] = 0
            }
        }

        on<RenderEvent.Extract> {
            if (!isInPosition || DungeonUtils.getF7Phase() != M7Phases.P3) return@on

            devicePositions.forEachIndexed { index, pos ->
                val state = highlighted[index]
                if (state == 0) return@forEachIndexed

                val color = if (state == 1) redColor else greenColor
                drawFilledBox(AABB(pos), color, depth = true)
            }
        }

        onReceive<ClientboundSetSubtitleTextPacket> {
            if (!hideTitles || !isInPosition || DungeonUtils.getF7Phase() != M7Phases.P3) return@onReceive

            val titleText = text.string.noControlCodes
            val regex = Regex(".* (completed a device!|activated a terminal!|activated a lever!) \\(\\d/\\d\\)|The gate will open in 5 seconds!|The gate has been destroyed!")

            if (regex.containsMatchIn(titleText)) {
                it.cancel()
            }
        }

        on<ChatPacketEvent> {
            if (!isInPosition || notified) return@on

            val regex = Regex("(\\w{1,16}) (activated|completed) a (lever|device|terminal)! \\((\\d)/([78])\\)")
            val match = regex.find(value) ?: return@on
            val playerName = match.groupValues[1]

            if (playerName == mc.player?.name?.string) {
                notifyDone()
            }
        }

        on<LevelEvent.Load> {
            notified = false
            highlighted.fill(0)
        }
    }

    private val isInPosition: Boolean
        get() = mc.player?.let { roomBoundingBox.contains(it.position()) } == true

    private fun notifyDone() {
        if (notified) return
        notified = true
        if (alertDone) {
            addonMessage("§aDevice Done!")
            alert("§aDevice Done!")
        }
    }
}
