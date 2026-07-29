package net.kumajunk.libleaddon.features.impl.dungeon

import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.clickgui.settings.impl.ColorSetting
import com.odtheking.odin.clickgui.settings.impl.DropdownSetting
import com.odtheking.odin.clickgui.settings.impl.NumberSetting
import com.odtheking.odin.events.ChatPacketEvent
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.LevelEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.Colors
import com.odtheking.odin.utils.createSoundSettings
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.playSoundSettings
import com.odtheking.odin.utils.render.textDim
import com.odtheking.odin.utils.sendCommand
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import net.kumajunk.libleaddon.utils.addonMessage

/**
 * ダンジョンでクリプト（暗号室）を5つ集めるようリマインダーを表示
 */
object CryptReminder : Module(
    name = "Crypt Reminder(LA)",
    description = "Reminds you to collect crypts in dungeons."
) {
    // 設定項目
    private val reminderTime by NumberSetting(
        name = "Reminder Time",
        default = 60,
        min = 10,
        max = 180,
        increment = 5,
        desc = "Time in seconds to remind about crypts (default: 360s = 6min)",
        unit = "seconds"
    )

    private val announceToParty by BooleanSetting(
        name = "Announce to Party",
        default = false,
        desc = "Announce crypt reminder to party chat."
    )

    private val displayTime by NumberSetting(
        name = "Display Time",
        default = 3,
        min = 1,
        max = 8,
        increment = 0.5,
        desc = "How long to display the reminder",
        unit = "seconds"
    )

    private val hud by HUD(name, desc = "Displays crypt reminder on HUD.", false) {
        if (it) {
            return@HUD textDim("We need 5 more crypts!", 0, 0, color)
        }
        if (!drawHUD) return@HUD 0 to 0
        textDim("We need ${5 - DungeonUtils.cryptCount} more crypts!", 0, 0, color)
    }

    private val color by ColorSetting(
        name = "HUD Color",
        default = Colors.MINECRAFT_RED,
        desc = "Color of the crypt reminder on the HUD."
    )

    private val soundDropDown by DropdownSetting("Sound")
    private val soundSettings = createSoundSettings("Notification Sound Setting", "entity.experience_orb.pickup") { soundDropDown }

    // 状態管理
    private var isSearch = false
    private var isReminded = false
    private var hideTitle = false
    private var drawHUD = false
    private var displayCounter = 0

    init {
        // Mortのセリフでダンジョン開始を検知
        on<ChatPacketEvent> {
            if (!DungeonUtils.inDungeons) return@on
            val msg = value.noControlCodes
            if (msg.contains("Mort:") && msg.contains("I found this map")) {
                isSearch = true
                isReminded = false
                hideTitle = false
                drawHUD = false
                displayCounter = 0
                return@on
            }
        }

        on<TickEvent.End> {
            if (!isSearch) return@on

            // 表示カウンターの管理（秒単位に変換：displayTime秒 = displayTime * 20 ticks）
            if (drawHUD) {
                displayCounter++
                if (displayCounter >= displayTime * 20) {
                    drawHUD = false
                    displayCounter = 0
                }
            }

            // クリプト数をスコアボードから取得
            val cryptsFound = DungeonUtils.cryptCount

            // 5つ集まったら終了
            if (cryptsFound >= 5) {
                resetReminder()
                return@on
            }

            // 経過時間を取得（DungeonUtils.dungeonTimeは"MM:SS"形式の文字列）
            val timeSeconds = parseDungeonTime(DungeonUtils.dungeonTime)
            if (timeSeconds < reminderTime) return@on
            if (isReminded) return@on

            // 必要なクリプト数を計算
            val needCrypts = 5 - cryptsFound

            // パーティチャットにアナウンス
            playSoundSettings(soundSettings())
            if (announceToParty) {
                sendCommand("pc We need $needCrypts more crypts!")
            }

            // HUD表示
            drawHUD = true
            displayCounter = 0

            // チャットメッセージ
            addonMessage("§fWe need §b$needCrypts§f more crypts!")

            isReminded = true
        }

        // ワールドアンロード時にリセット
        on<LevelEvent.Unload> {
            isSearch = false
            hideTitle = false
            isReminded = false
            drawHUD = false
            displayCounter = 0
        }
    }

    private fun resetReminder() {
        isSearch = false
        hideTitle = false
        drawHUD = false
        displayCounter = 0
    }

    /**
     * DungeonUtils.dungeonTimeの文字列（"MM:SS"形式）を秒数に変換
     */
    private fun parseDungeonTime(timeString: String): Int {
        val clean = timeString.noControlCodes

        val minMatch = Regex("(\\d+)m").find(clean)
        val secMatch = Regex("(\\d+)s").find(clean)

        val minutes = minMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val seconds = secMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

        return minutes * 60 + seconds
    }
}
