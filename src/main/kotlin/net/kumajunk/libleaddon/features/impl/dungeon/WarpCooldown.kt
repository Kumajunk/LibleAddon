package net.kumajunk.libleaddon.features.impl.dungeon

import com.odtheking.odin.clickgui.settings.impl.StringSetting
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.LevelEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.events.core.onReceive
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.Colors
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.render.textDim
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket

/**
 * ダンジョンワープ後の30秒クールダウンを表示するモジュール
 * プレイヤーがダンジョンにワープした際にクールダウンタイマーを開始し、
 * 残り時間をHUDに表示する
 */
object WarpCooldown : Module(
    name = "Warp Cooldown(LA)",
    description = "Displays the warp cooldown timer after entering a dungeon."
) {
    // ====== 設定値 ======
    /**
     * テキストのカラーコード（0-9, a-f）
     * 例: "f" = 白, "b" = 水色, "a" = 緑
     */
    private val textColorCode by StringSetting("Text Color Code", "b", desc = "Color code for 'Warp Cooldown:' text (0-9, a-f)")
    private val numberColorCode by StringSetting("Number Color Code", "f", desc = "Color code for the countdown number (0-9, a-f)")

    // ====== HUD ======
    private val timerHud by HUD("Warp Cooldown", desc = "Displays warp cooldown timer.", toggleable = false) { example ->
        val textColor = "§${textColorCode.take(1)}"
        val numColor = "§${numberColorCode.take(1)}"
        if (example) {
            // プレビュー表示
            textDim("${textColor}Warp Cooldown: ${numColor}30.00s", 0, 0, Colors.WHITE)
        } else if (!isCountdownActive) {
            0 to 0
        } else {
            // 実際のタイマー表示
            val remaining = getSecondsLeft()
            val text = "${textColor}Warp Cooldown: ${numColor}${String.format("%.2f", remaining)}s"
            textDim(text, 0, 0, Colors.WHITE)
        }
    }

    // ====== クールダウン定数 ======
    private const val COOLDOWN_SECONDS = 30.0

    // ====== 状態管理 ======
    /**
     * 最後にワープした時刻（ミリ秒）
     */
    private var lastWarpTime: Long = 0

    /**
     * クールダウンがアクティブかどうか
     */
    private var isCountdownActive = false

    // ====== チャット検知パターン ======
    /**
     * ダンジョン入室メッセージの正規表現
     */
    private val dungeonEntryRegex = Regex("^-*\\n\\[[^]]+] (\\w+) entered (?:MM )?\\w+ Catacombs, Floor (\\w+)!\\n-*$")

    init {
        // ワールドアンロード時にリセット
        on<LevelEvent.Unload> { reset() }

        // チャットメッセージでダンジョン入室を検知
        onReceive<ClientboundSystemChatPacket> {
            val msg = content.string.noControlCodes

            // ダンジョン入室メッセージをチェック
            if (dungeonEntryRegex.matches(msg)) {
                lastWarpTime = System.currentTimeMillis()
                isCountdownActive = true
            }
        }

        // タイマー更新（毎tick）
        on<TickEvent.End> {
            if (!isCountdownActive) return@on

            // 残り時間をチェック
            val remaining = getSecondsLeft()
            if (remaining <= 0) {
                isCountdownActive = false
            }
        }
    }

    /**
     * 残り秒数を計算
     */
    private fun getSecondsLeft(): Double {
        val elapsed = (System.currentTimeMillis() - lastWarpTime) / 1000.0
        return COOLDOWN_SECONDS - elapsed
    }

    /**
     * 状態リセット
     */
    private fun reset() {
        lastWarpTime = 0
        isCountdownActive = false
    }
}
