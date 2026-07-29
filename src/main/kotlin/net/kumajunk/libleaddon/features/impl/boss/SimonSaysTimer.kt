package net.kumajunk.libleaddon.features.impl.boss

import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.clickgui.settings.impl.NumberSetting
import com.odtheking.odin.events.ChatPacketEvent
import com.odtheking.odin.events.LevelEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.sendCommand
import com.odtheking.odin.utils.skyblock.dungeon.DungeonClass
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import net.kumajunk.libleaddon.utils.addonMessage

/**
 * Floor7のSimon Says完了時間を計測・表示するモジュール
 */
object SimonSaysTimer : Module(
    name = "Simon Says Timer(LA)",
    description = "Displays the time taken to complete Simon Says in Floor 7."
) {
    // クラスリスト (0: Healer, 1: Berserk, 2: Archer, 3: Mage, 4: Tank)
    private val classList = listOf(
        DungeonClass.HEALER,
        DungeonClass.BERSERK,
        DungeonClass.ARCHER,
        DungeonClass.MAGE,
        DungeonClass.TANK
    )

    // 設定項目
    private val detectClassIndex by NumberSetting(
        name = "Detect Class",
        default = 0,
        min = 0,
        max = 4,
        increment = 1,
        desc = "0: Healer, 1: Berserk, 2: Archer, 3: Mage, 4: Tank"
    )
    private val announceToParty by BooleanSetting(
        name = "Announce to Party",
        default = false,
        desc = "Announce Simon Says time to party chat."
    )

    // 状態管理
    private var simonSaysStartTime: Long? = null

    // チャットメッセージパターン
    private val goldorStartPattern = "[BOSS] Goldor: Who dares trespass into my domain?"
    private val deviceCompletePattern = Regex("""(\w{1,16}) (activated|completed) a (lever|device|terminal)! \((\d)/([78])\)""")

    init {
        // Goldorの開始セリフでタイマー開始
        on<ChatPacketEvent> {
            val msg = value.noControlCodes

            // Simon Saysの開始
            if (msg.contains(goldorStartPattern)) {
                simonSaysStartTime = System.currentTimeMillis()
                return@on
            }

            // デバイス完了検出
            val matchResult = deviceCompletePattern.find(msg) ?: return@on
            val (playerName, _, deviceType) = matchResult.destructured

            // deviceが完了した場合のみ処理
            if (deviceType != "device") return@on

            // タイマーが開始されていない場合はスキップ
            val startTime = simonSaysStartTime ?: return@on

            // プレイヤーのクラスを取得
            val playerClass = DungeonUtils.dungeonTeammates.find { it.name == playerName }?.clazz

            // 設定されたクラスと一致するかチェック
            val targetClass = classList[detectClassIndex]
            if (playerClass != targetClass) return@on

            // 時間計算
            val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
            val timeText = String.format("%.2f", elapsed)

            // チャットに表示
            addonMessage("§fSimon Says took §b${timeText}s")

            // パーティーにアナウンス
            if (announceToParty) {
                sendCommand("pc Simon Says took ${timeText}s")
            }

            // タイマーをリセット
            simonSaysStartTime = null
        }

        // ワールドアンロード時にリセット
        on<LevelEvent.Unload> {
            simonSaysStartTime = null
        }
    }
}
