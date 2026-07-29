package net.kumajunk.libleaddon.features.impl.dungeon

import com.odtheking.odin.clickgui.settings.impl.ColorSetting
import com.odtheking.odin.clickgui.settings.impl.NumberSetting
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.LevelEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.events.core.onReceive
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.Color
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.render.textDim
import net.kumajunk.libleaddon.utils.addonMessage
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket

/**
 * マスク(Bonzo, Spirit, Phoenix)のクールダウンタイマーを管理するモジュール
 * 発動を検知してチャットに通知し、残りクールダウン時間をHUDに表示する
 */
object MaskTimer : Module(
    name = "Mask Timer(LA)",
    description = "Tracks mask and phoenix cooldown timers in dungeons."
) {
    // ====== 設定値 ======
    /**
     * カタコンベレベル（クールダウン短縮計算用）
     * Bonzo Mask: クールダウン = 360秒 - (cataLevel * 3.6秒)
     */
    private val cataLevel by NumberSetting("Cata Level", 25, 1, 50, 1, desc = "Your catacombs level for cooldown calculation", unit = "Lvl")

    // HUD設定
    private val timerColor by ColorSetting("Timer Color", Color(85, 255, 255), false, desc = "Color of the timer text on HUD.")

    // ====== HUD ======
    private val timerHud by HUD("Mask Timer", desc = "Displays active mask cooldown timers.", toggleable = false) { example ->
        if (example) {
            // プレビュー表示
            textDim("§9Bonzo Mask§f: §b360.00s", 0, 0, timerColor)
        } else if (activeTimers.isEmpty()) {
            0 to 0
        } else {
            // 実際のタイマー表示
            var yOffset = 0
            for ((key, remaining) in activeTimers) {
                val mask = masks[key] ?: continue
                val seconds = remaining / 20.0
                val text = "${mask.name}§f: §b${String.format("%.2f", seconds)}s"
                textDim(text, 0, yOffset, timerColor)
                yOffset += 10
            }
            // 最大幅を計算（プレビューと同じ幅を使用）
            val maxWidth = mc.font.width("§9Bonzo Mask§f: §b360.00s")
            maxWidth to yOffset
        }
    }

    // ====== マスク定義 ======
    /**
     * マスク情報を保持するデータクラス
     * @param name 表示名（カラーコード付き）
     * @param baseDuration 基本クールダウン時間（tick単位、20tick = 1秒）
     * @param procText 発動時の表示テキスト
     * @param useCataReduction cataレベルによるクールダウン短縮を適用するか
     */
    private data class MaskInfo(
        val name: String,
        val baseDuration: Int,   // tick単位
        val procText: String,
        val useCataReduction: Boolean = false
    )

    private val masks = mapOf(
        "bonzo" to MaskInfo(
            name = "§9Bonzo Mask§r",
            baseDuration = 7200, // 360秒 = 7200tick
            procText = "§9Bonzo Mask Procced!",
            useCataReduction = true
        ),
        "spirit" to MaskInfo(
            name = "§fSpirit Mask§r",
            baseDuration = 600, // 30秒 = 600tick
            procText = "§fSpirit Mask Procced!"
        ),
        "phoenix" to MaskInfo(
            name = "§cPhoenix§r",
            baseDuration = 1200, // 60秒 = 1200tick
            procText = "§cPhoenix Procced!"
        )
    )

    // ====== チャット検知パターン ======
    private val bonzoRegex = Regex("""Your (?:⚚ )?Bonzo's Mask saved your life!""")
    private const val SPIRIT_CRITERIA = "Second Wind Activated! Your Spirit Mask saved your life!"
    private const val PHOENIX_CRITERIA = "Your Phoenix Pet saved you from certain death!"

    // ====== タイマー状態 ======
    /**
     * アクティブなタイマー（マスクキー → 残りtick数）
     */
    private val activeTimers = mutableMapOf<String, Int>()

    init {
        // ワールドロード時にリセット
        on<LevelEvent.Load> { reset() }

        // チャットメッセージで発動を検知
        onReceive<ClientboundSystemChatPacket> {
            val msg = content.string.noControlCodes

            // Bonzo Mask検知
            if (bonzoRegex.containsMatchIn(msg)) {
                triggerMask("bonzo")
            }
            // Spirit Mask検知
            else if (msg.contains(SPIRIT_CRITERIA)) {
                triggerMask("spirit")
            }
            // Phoenix Pet検知
            else if (msg.contains(PHOENIX_CRITERIA)) {
                triggerMask("phoenix")
            }
        }

        // タイマー更新（毎tick）
        on<TickEvent.End> {
            if (activeTimers.isEmpty()) return@on

            // 終了したタイマーを収集
            val expiredKeys = mutableListOf<String>()

            // タイマーをデクリメント
            for ((key, remaining) in activeTimers) {
                val newRemaining = remaining - 1
                if (newRemaining <= 0) {
                    expiredKeys.add(key)
                } else {
                    activeTimers[key] = newRemaining
                }
            }

            // 終了したタイマーを処理
            for (key in expiredKeys) {
                activeTimers.remove(key)
                masks[key]?.let { mask ->
                    addonMessage("${mask.name}§a is ready!")
                }
            }
        }
    }

    /**
     * マスク発動時の処理
     * @param key マスクのキー（bonzo, spirit, phoenix）
     */
    private fun triggerMask(key: String) {
        val mask = masks[key] ?: return

        // クールダウン計算
        val duration = if (mask.useCataReduction) {
            // Bonzo: 7200tick - (cataLevel * 72tick)
            mask.baseDuration - (cataLevel * 72)
        } else {
            mask.baseDuration
        }

        // タイマーを設定（既存のタイマーは上書き）
        activeTimers[key] = duration

        // チャット通知
        val durationSeconds = duration / 20.0
        addonMessage("${mask.procText} §7(§b${String.format("%.1f", durationSeconds)}s cooldown§7)")
    }

    /**
     * 状態リセット
     */
    private fun reset() {
        activeTimers.clear()
    }
}