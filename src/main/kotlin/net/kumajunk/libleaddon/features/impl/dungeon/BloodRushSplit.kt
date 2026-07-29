package net.kumajunk.libleaddon.features.impl.dungeon

import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.events.LevelEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.events.core.onReceive
import com.odtheking.odin.features.Module
import com.odtheking.odin.features.impl.dungeon.map.DungeonScan
import com.odtheking.odin.features.impl.dungeon.map.SpecialColumn
import com.odtheking.odin.features.impl.dungeon.map.tile.DungeonRoom
import com.odtheking.odin.features.impl.dungeon.map.tile.RoomType
import com.odtheking.odin.utils.noControlCodes
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import java.util.ArrayDeque

/**
 * Blood Rush中のスプリットタイムを計測・表示するモジュール
 */
object BloodRushSplit : Module(
    name = "Blood Rush Split(LA)",
    description = "Tracks and displays split times during Blood Rush."
) {
    private val showTotalTime by BooleanSetting(
        name = "Show Total Time",
        default = false,
        desc = "Displays the total time taken for the Blood Rush at the end."
    )

    private val rooms = mutableListOf<String>()
    private val clearTimes = mutableListOf<Long>()
    private var brStart = 0L
    private val omitRooms = listOf("Entrance", "Fairy", "Blood")

    init {
        // ワールドロード時にリセット
        on<LevelEvent.Load> { reset() }

        // チャットメッセージ検知
        onReceive<ClientboundSystemChatPacket> {
            val msg = content.string.noControlCodes

            // Blood Rush開始（Mortのメッセージ）
            if (msg.contains("Mort:") && msg.contains("I found this map")) {
                brStart = System.currentTimeMillis()
                clearTimes.add(0L)
            }

            // WITHERドア開放 -> スプリット記録
            if (brStart > 0 && msg.contains("opened a WITHER door")) {
                clearTimes.add(System.currentTimeMillis() - brStart)
            }

            // BLOODドア開放 -> ルート計算・結果表示
            if (brStart > 0 && msg.contains("BLOOD DOOR") && msg.contains("opened")) {
                clearTimes.add(System.currentTimeMillis() - brStart)

                // ルート計算
                val route = getBloodRushRoute()
                    .filter { it !in omitRooms }
                rooms.addAll(route)

                displaySplits()
                reset()
            }
        }
    }

    private fun getBloodRushRoute(): List<String> {
        val fromRoom = DungeonScan.rooms.find { it.type == RoomType.ENTRANCE } ?: return emptyList()
        val toRoom = DungeonScan.rooms.find { it.type == RoomType.BLOOD } ?: return emptyList()

        val visited = mutableSetOf<DungeonRoom>()
        val queue = ArrayDeque<Pair<DungeonRoom, List<DungeonRoom>>>()
        queue.add(fromRoom to listOf(fromRoom))

        while (queue.isNotEmpty()) {
            val (current, path) = queue.removeFirst()
            if (current == toRoom) {
                return path.mapNotNull { it.name }
            }
            if (current in visited) continue
            visited.add(current)

            for ((_, door) in DungeonScan.doors) {
                val originRoom = DungeonScan.tiles.getOrNull(door.originTileIndex)?.room
                val destRoom = DungeonScan.tiles.getOrNull(door.destinationTileIndex)?.room
                if (originRoom == null || destRoom == null) continue

                val adjacentRooms = listOf(originRoom, destRoom)
                if (current !in adjacentRooms) continue

                for (neighbor in adjacentRooms) {
                    if (neighbor != current && neighbor !in visited) {
                        queue.add(neighbor to (path + neighbor))
                    }
                }
            }
        }
        return emptyList()
    }

    /**
     * スプリットタイムをチャットに表示
     */
    private fun displaySplits() {
        val message = buildString {
            append("\n§f§m------------------------------§r\n")
            append("§c§lBlood Rush Splits:\n")
            for (i in rooms.indices) {
                if (i + 1 < clearTimes.size) {
                    val time = (clearTimes[i + 1] - clearTimes[i]) / 1000.0
                    append("§f${rooms[i]}: §b${String.format("%.2f", time)}s\n")
                }
            }
            if (showTotalTime) append("\n§b§lTotal Time§f: ${String.format("%.2f", (clearTimes.last() / 1000.0))}s \n")
            append("§f§m------------------------------§r\n")
        }
        mc.execute { mc.gui.chat.addClientSystemMessage(Component.literal(message)) }
    }

    /**
     * 状態をリセット
     */
    private fun reset() {
        SpecialColumn.unload()
        rooms.clear()
        clearTimes.clear()
        brStart = 0L
    }
}
