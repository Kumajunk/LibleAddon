package net.kumajunk.libleaddon.features.impl.dungeon.map

import com.odtheking.odin.clickgui.settings.Setting.Companion.withDependency
import com.odtheking.odin.clickgui.settings.impl.*
import com.odtheking.odin.events.LevelEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.events.core.onReceive
import com.odtheking.odin.features.Module
import com.odtheking.odin.features.impl.dungeon.map.DungeonScan
import com.odtheking.odin.utils.Color
import com.odtheking.odin.utils.Color.Companion.withAlpha
import com.odtheking.odin.utils.Colors
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.render.hollowFill
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket

object DungeonMap : Module(
    "Dungeon Map(LA)",
    description = "Displays the dungeon map."
) {
    private val disableBoss by BooleanSetting("Disable in Boss", true, desc = "Disables the map during boss fights.")

    val backgroundOutline by ColorSetting("Background Outline", Colors.BLACK, true, desc = "The color of the background border.")
    val backgroundColor by ColorSetting("Background Color", Colors.BLACK.withAlpha(0.2f), true, desc = "Background color of the map.")
    val textScaling by NumberSetting("Text Scaling", 0.45f, 0.1f, 1f, 0.05f, desc = "Scale of room name text.")
    val textMode by SelectorSetting("Text Mode", "Both", arrayListOf("Both", "Name Only", "Secrets Only"), desc = "Controls whether room name, secrets, or both are displayed.")

    private val playerDropdown by DropdownSetting("Player Settings")
    var playerHeadSize by NumberSetting("Player Head Size", 1f, 0f, 2f, 0.2f, desc = "The size of player heads on the map.").withDependency { playerDropdown }
    var playerHeadBackgroundSize by NumberSetting("Player Head BG Size", 1, 0, 10, 1, desc = "Size of player head background.").withDependency { playerDropdown }
    var playerNamesScaling by NumberSetting("Player Names Scaling", 0.75f, 0.1f, 2f, 0.05f, desc = "The scale of player names displayed on the map.").withDependency { playerDropdown }
    var playerNameColor by ColorSetting("Player Name Color", Color(70, 70, 70), false, desc = "Color of player names.").withDependency { playerDropdown }
    var showNameAlways by BooleanSetting("Show Name Always", false, desc = "Always show player names on the map.").withDependency { playerDropdown }

    private val roomDropdown by DropdownSetting("Room Settings")
    val normalRoomColor by ColorSetting("Normal Room", Color(107, 58, 17), true, desc = "Color of normal rooms.").withDependency { roomDropdown }
    val puzzleRoomColor by ColorSetting("Puzzle Room", Color(117, 0, 133), true, desc = "Color of puzzle rooms.").withDependency { roomDropdown }
    val trapRoomColor by ColorSetting("Trap Room", Color(216, 127, 51), true, desc = "Color of trap rooms.").withDependency { roomDropdown }
    val bloodRoomColor by ColorSetting("Blood Room", Color(255, 0, 0), true, desc = "Color of blood rooms.").withDependency { roomDropdown }
    val entranceRoomColor by ColorSetting("Entrance Room", Color(20, 133, 0), true, desc = "Color of entrance rooms.").withDependency { roomDropdown }
    val fairyRoomColor by ColorSetting("Fairy Room", Color(224, 0, 255), true, desc = "Color of fairy rooms.").withDependency { roomDropdown }
    val championRoomColor by ColorSetting("Champion Room", Color(254, 223, 0), true, desc = "Color of champion rooms.").withDependency { roomDropdown }
    val unknownRoomColor by ColorSetting("Unknown Room", Color(40, 40, 40), true, desc = "Color of unknown rooms hinted by a door with no discovered room on the other side.").withDependency { roomDropdown }

    val disablePred by BooleanSetting("Disable Prediction", false, desc = "Disables special-column room type prediction.")

    private val mapHud by HUD("Dungeon Map(LA)", "Displays the LA version of the dungeon map.", false) { example ->
        when {
            (!DungeonUtils.inDungeons || (disableBoss && DungeonUtils.inBoss)) && !example -> 0 to 0
            example -> renderExampleMap()
            else    -> renderDungeonMap()
        }
    }

    private const val MAP_PX = 128

    private fun GuiGraphicsExtractor.renderExampleMap(): Pair<Int, Int> {
        fill(0, 0, MAP_PX, MAP_PX, backgroundColor.rgba)
        hollowFill(0, 0, MAP_PX, MAP_PX, 1, backgroundOutline)
        centeredText(mc.font, "MAP", MAP_PX / 2, MAP_PX / 2 - mc.font.lineHeight / 2, Colors.WHITE.rgba)
        return MAP_PX to MAP_PX
    }

    private fun GuiGraphicsExtractor.renderDungeonMap(): Pair<Int, Int> {
        fill(0, 0, MAP_PX, MAP_PX, backgroundColor.rgba)
        hollowFill(0, 0, MAP_PX, MAP_PX, 1, Colors.gray26)

        renderMap()


        return MAP_PX to MAP_PX
    }

    val roomSecrets = mutableMapOf<String, Int>()
    var currentSecrets: Int = 0

    private val secretRegex = Regex("(\\d+)/(\\d+) Secrets")

    init {
        on<LevelEvent.Load> {
            roomSecrets.clear()
            currentSecrets = 0
        }

        onReceive<ClientboundSystemChatPacket> { event ->
            val packet = event.packet as? ClientboundSystemChatPacket ?: return@onReceive
            if (!packet.overlay) return@onReceive

            val content = packet.content().string.noControlCodes

            val match = secretRegex.find(content) ?: return@onReceive
            val (current, _) = match.destructured
            val currentInt = current.toIntOrNull() ?: return@onReceive

            currentSecrets = currentInt

            // Save secret count to map if current room is detected
            getCurrentRoomName()?.let { roomName ->
                roomSecrets[roomName] = currentInt
            }
        }
    }

    /**
     * Helper to get the current room name from the player position
     */
    fun getCurrentRoomName(): String? {
        val player = mc.player ?: return null
        val px = player.x
        val pz = player.z

        // Convert world block coordinates to dungeon 6x6 tile coordinates (0..5)
        // Hypixel dungeon grid starts around (-185, -185) with 32-block tile size
        val tileX = ((px + 185.0) / 32.0).toInt().coerceIn(0, 5)
        val tileZ = ((pz + 185.0) / 32.0).toInt().coerceIn(0, 5)

        // 1. Try finding room in DungeonScan.rooms matching the tile
        val roomByTile = DungeonScan.rooms.find { r ->
            r.tiles.any { tile -> tile.x == tileX && tile.z == tileZ }
        }
        val name1 = roomByTile?.name ?: roomByTile?.data?.name
        if (!name1.isNullOrEmpty()) return name1

        // 2. Fallback to scanned tiles direct lookup
        val tileIndex = tileX + tileZ * 6
        val scannedRoom = DungeonScan.tiles.getOrNull(tileIndex)?.room
        return scannedRoom?.name ?: scannedRoom?.data?.name
    }
}