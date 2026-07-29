package net.kumajunk.libleaddon.features.impl.boss

import com.odtheking.odin.clickgui.settings.impl.ColorSetting
import com.odtheking.odin.events.ChatPacketEvent
import com.odtheking.odin.events.LevelEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.Color
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.render.textDim

/**
 * Floor7 P2フェーズ中にPurple Pad（紫パッド）のタイマーを表示するモジュール
 * Stormのセリフ検出から96tick（4.8秒）をカウントダウン
 */
object PurplePad : Module(
    name = "Purple Pad Timer(LA)",
    description = "Displays a countdown timer for Purple Pad in Floor 7 Phase 2."
) {
    // 設定項目
    private val timerColor by ColorSetting(
        name = "Timer Color",
        default = Color(170, 0, 170),  // 紫色
        allowAlpha = false,
        desc = "Color of the Purple Pad countdown timer."
    )

    // タイマー管理
    private var purplePadEndTime = 0L
    private var isTimerActive = false

    // Stormのセリフパターン (Purple Pad攻撃検出)
    private val purplePadPattern = Regex("""^\[BOSS] Storm: (ENERGY HEED MY CALL|THUNDER LET ME BE YOUR CATALYST)!$""")

    // HUD設定
    private val timerHud by HUD("Purple Pad Timer", desc = "Displays Purple Pad countdown timer.", toggleable = false) { example ->
        if (example) {
            // プレビュー表示
            textDim("4.80", 0, 0, timerColor)
        } else if (!isTimerActive) {
            0 to 0
        } else {
            val remainingMs = purplePadEndTime - System.currentTimeMillis()
            if (remainingMs <= 0) {
                isTimerActive = false
                0 to 0
            } else {
                // 残り時間を秒単位で表示（小数点2桁）
                val remainingSeconds = remainingMs / 1000.0
                val timerText = String.format("%.2f", remainingSeconds)
                textDim(timerText, 0, 0, timerColor)
            }
        }
    }

    init {
        // Purple Padパターン検出でタイマー開始
        on<ChatPacketEvent> {
            val msg = value.noControlCodes
            if (purplePadPattern.matches(msg)) {
                // 96tick = 4.8秒 (4800ms) のカウントダウン開始
                purplePadEndTime = System.currentTimeMillis() + 4800L
                isTimerActive = true
            }
        }

        // ワールドアンロード時にリセット
        on<LevelEvent.Unload> {
            purplePadEndTime = 0L
            isTimerActive = false
        }
    }
}
