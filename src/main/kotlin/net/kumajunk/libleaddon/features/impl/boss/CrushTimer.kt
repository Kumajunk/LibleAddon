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
 * Floor7 P2フェーズ中にStormのCrush攻撃のタイマーを表示するモジュール
 * Crushが発生してから1秒（20tick）後に着地するため、カウントダウンを表示
 */
object CrushTimer : Module(
    name = "Crush Timer(LA)",
    description = "Displays a countdown timer for Storm's Crush attack in Floor 7."
) {
    // 設定項目
    private val timerColor by ColorSetting(
        name = "Timer Color",
        default = Color(255, 85, 85),
        allowAlpha = false,
        desc = "Color of the Crush countdown timer."
    )

    // タイマー管理
    private var crushEndTime = 0L
    private var isTimerActive = false

    // Stormのセリフパターン (Crush攻撃検出)
    private val crushPattern = Regex("""^\[BOSS] Storm: (Ouch, that hurt!|Oof)$""")

    // HUD設定
    private val timerHud by HUD("Crush Timer", desc = "Displays Crush countdown timer.", toggleable = false) { example ->
        if (example) {
            // プレビュー表示
            textDim("1.00", 0, 0, timerColor)
        } else if (!isTimerActive) {
            0 to 0
        } else {
            val remainingMs = crushEndTime - System.currentTimeMillis()
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
        // Crushパターン検出でタイマー開始
        on<ChatPacketEvent> {
            val msg = value.noControlCodes
            if (crushPattern.matches(msg)) {
                // 20tick = 1秒 (1000ms) のカウントダウン開始
                crushEndTime = System.currentTimeMillis() + 1000L
                isTimerActive = true
            }
        }

        // ワールドアンロード時にリセット
        on<LevelEvent.Unload> {
            crushEndTime = 0L
            isTimerActive = false
        }
    }
}
