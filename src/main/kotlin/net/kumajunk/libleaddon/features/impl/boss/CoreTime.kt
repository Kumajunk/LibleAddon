package net.kumajunk.libleaddon.features.impl.boss

import com.odtheking.odin.events.ChatPacketEvent
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.LevelEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.sendCommand
import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import net.kumajunk.libleaddon.utils.addonMessage

/**
 * Floor 7のCoreにプレイヤーが到達するまでの時間を計測・表示するモジュール
 */
object CoreTime : Module(
    name = "Core Time(LA)",
    description = "Displays the time taken to reach the core in Floor 7."
) {
    // 設定項目
    private val showInChat by BooleanSetting("Show in Chat", true, "Show core times in chat.")
    private val announceToParty by BooleanSetting("Announce to Party", false, "Announce core times to party chat.")

    // Core範囲の定数 (元JSの box 座標)
    private val CORE_BOX_MIN = Triple(39.0, 55.0, 54.0)
    private val CORE_BOX_MAX = Triple(71.0, 155.5, 118.0)

    // 状態管理
    private var coreOpenTime = 0L
    private val playersInCore = mutableMapOf<String, Long>()
    private var messageAnnounced = false
    private var isCoreOpened = false

    // チャットメッセージパターン
    private val coreOpeningPattern = "The Core entrance is opening!"
    private val goldorFinishPattern = Regex("^\\[BOSS] Goldor: You have done it, you destroyed the factory.*$")

    init {
        // ワールドアンロード時にリセット
        on<LevelEvent.Unload> { reset() }

        // Core開放メッセージ検出
        on<ChatPacketEvent> {
            if (value.contains(coreOpeningPattern)) {
                reset()
                coreOpenTime = System.currentTimeMillis()
                isCoreOpened = true
            }

            // Goldor撃破メッセージ検出
            if (goldorFinishPattern.matches(value)) {
                announce()
                isCoreOpened = false
            }
        }

        // プレイヤー位置の定期チェック
        on<TickEvent.End> {
            if (!isCoreOpened || coreOpenTime == 0L) return@on

            val world = mc.level ?: return@on

            // ワールド内の全プレイヤーをチェック
            world.players().forEach { player ->
                val playerName = player.gameProfile.name ?: return@forEach

                // 既に記録済みのプレイヤーはスキップ
                if (playersInCore.containsKey(playerName)) return@forEach

                // プレイヤーがCore範囲内にいるか確認
                if (isInsideBox(player.x, player.y, player.z)) {
                    playersInCore[playerName] = System.currentTimeMillis()
                }
            }
        }
    }

    /**
     * プレイヤーがCore範囲内にいるか判定
     */
    private fun isInsideBox(x: Double, y: Double, z: Double): Boolean {
        return x >= CORE_BOX_MIN.first && x <= CORE_BOX_MAX.first &&
               y >= CORE_BOX_MIN.second && y <= CORE_BOX_MAX.second &&
               z >= CORE_BOX_MIN.third && z <= CORE_BOX_MAX.third
    }

    /**
     * 状態をリセット
     */
    private fun reset() {
        coreOpenTime = 0L
        playersInCore.clear()
        messageAnnounced = false
        isCoreOpened = false
    }

    /**
     * 結果をアナウンス
     */
    private fun announce() {
        if (messageAnnounced || playersInCore.isEmpty()) return

        if (showInChat) showTimesInChat()
        if (announceToParty) {
            val message = formatPartyMessage()
            if (message.isNotEmpty()) {
                sendCommand("pc $message")
            }
        }

        messageAnnounced = true

        // 両方無効の場合はリセット
        if (!showInChat && !announceToParty) reset()
    }

    /**
     * 各プレイヤーのCore到達時間をチャットに表示
     */
    private fun showTimesInChat() {
        if (playersInCore.isEmpty()) return

        getPlayerCoreTimes().forEach { (playerName, timeToEnter) ->
            addonMessage("§b$playerName §fhas entered core in §b${timeToEnter}§fs")
        }
    }

    /**
     * パーティチャット用のメッセージを組み立て
     */
    private fun formatPartyMessage(): String {
        if (playersInCore.isEmpty()) return ""

        val messageParts = mutableListOf("Time Into Core |")

        getPlayerCoreTimes().forEach { (playerName, timeToEnter) ->
            // 0.01秒以下は表示しない
            if (timeToEnter.toDouble() > 0.01) {
                messageParts.add("$playerName took ${timeToEnter}s")
            }
        }

        return messageParts.joinToString(" | ")
    }

    /**
     * プレイヤーのCore到達時間リストを取得（時間順でソート）
     */
    private fun getPlayerCoreTimes(): List<Pair<String, String>> {
        return playersInCore.map { (playerName, entranceTime) ->
            val timeToEnter = ((entranceTime - coreOpenTime) / 1000.0)
            playerName to String.format("%.3f", timeToEnter)
        }.sortedBy { it.second.toDoubleOrNull() ?: 0.0 }
    }
}