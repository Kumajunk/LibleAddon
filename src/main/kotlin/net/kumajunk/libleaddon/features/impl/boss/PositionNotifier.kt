package net.kumajunk.libleaddon.features.impl.boss

import com.odtheking.odin.events.ChatPacketEvent
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.LevelEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.sendCommand
import com.odtheking.odin.utils.skyblock.dungeon.DungeonClass
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import com.odtheking.odin.utils.skyblock.dungeon.M7Phases
import net.kumajunk.libleaddon.features.impl.boss.PositionNotifier.CheckBox.isInsideBox

/**
 * Floor7の各フェーズで特定の位置に到達した際にパーティーチャットでアナウンスするモジュール
 */
object  PositionNotifier : Module(
    name = "Position Notifier(LA)",
    description = "Announces when you reach specific positions in Floor 7."
) {
    /**
     * 位置座標範囲の定義（nullの場合はY座標のみでチェック）
     */
    data class PositionBox(val minX: Double, val minY: Double, val minZ: Double,
                           val maxX: Double, val maxY: Double, val maxZ: Double)

    /**
     * 位置データの定義
     * @param id 位置ID
     * @param box 座標範囲（nullの場合はcheckPositionで独自判定）
     * @param messageText パーティーチャットに送信するメッセージ
     * @param checkCondition フェーズとクラス条件をチェック
     * @param checkPosition 位置判定関数（デフォルトはbox範囲チェック）
     */
    data class PositionData(
        val id: String,
        val box: PositionBox?,
        val messageText: String,
        val checkCondition: (M7Phases, DungeonClass?, Int) -> Boolean,
        val checkPosition: (Double, Double, Double, PositionBox?) -> Boolean = { x, y, z, b ->
            b?.let { isInsideBox(x, y, z, it) } ?: false
        }
    )

    // 現在のStage（Storm Phase以降のターミナル進行状況）
    // Stage 0: Storm未撃破, Stage 1: Storm撃破後～, Stage 2: 7/7 or 8/8到達ごとに+1
    private var currentStage = 0

    // 位置リスト
    private val positions: List<PositionData> = listOf(
        // Phase 1 (Maxor) → P2到達通知
        PositionData(
            id = "AtP2",
            box = null,
            messageText = "At P2!",
            // Phase 1 & Healer以外
            checkCondition = { phase, clazz, _ ->
                phase == M7Phases.P1 && clazz != DungeonClass.HEALER
            },
            // Y座標が164〜205の範囲
            checkPosition = { _, y, _, _ -> y < 205 && y > 164 }
        ),

        // Phase 2, 3共通 - SS (Safe Spot for SS route)
        PositionData(
            id = "AtSS",
            box = PositionBox(107.0, 119.5, 91.0, 111.0, 126.0, 97.0),
            messageText = "At SS!",
            checkCondition = { phase, _, _ ->
                phase == M7Phases.P2 || phase == M7Phases.P3
            }
        ),

        // Stage 1,2 - Pre Enter 2
        PositionData(
            id = "AtEE2",
            box = PositionBox(53.0, 108.0, 129.0, 60.0, 115.0, 133.0),
            messageText = "At Pre Enter 2!",
            checkCondition = { _, _, stage -> stage in 1..2 }
        ),

        // Stage 1,2 - High Pre Enter 2
        PositionData(
            id = "AtHighEE2",
            box = PositionBox(58.0, 131.5, 138.0, 62.0, 137.0, 143.0),
            messageText = "At High Pre Enter 2!",
            checkCondition = { _, _, stage -> stage in 1..2 }
        ),

        // Stage 1,2 - SafeSpot 2
        PositionData(
            id = "AtSafeSpot2",
            box = PositionBox(47.0, 109.0, 121.0, 49.0, 111.0, 123.0),
            messageText = "At SafeSpot 2!",
            checkCondition = { _, _, stage -> stage in 1..2 }
        ),

        // Stage 2,3 - Pre Enter 3
        PositionData(
            id = "AtEE3",
            box = PositionBox(0.0, 108.0, 98.0, 4.0, 115.0, 107.0),
            messageText = "At Pre Enter 3!",
            checkCondition = { _, _, stage -> stage in 2..3 }
        ),

        // Stage 2,3 - SafeSpot 3
        PositionData(
            id = "AtSafeSpot3",
            box = PositionBox(18.0, 121.5, 91.0, 19.0, 124.0, 96.0),
            messageText = "At SafeSpot 3!",
            checkCondition = { _, _, stage -> stage in 2..3 }
        ),

        // Stage 3,4 - Lower Pre Enter 4
        PositionData(
            id = "AtLowerEE4",
            box = PositionBox(38.0, 108.5, 34.0, 42.0, 111.0, 36.0),
            messageText = "At Lower Pre Enter 4!",
            checkCondition = { _, _, stage -> stage in 3..4 }
        ),

        // Stage 2,3,4 - Core到達
        PositionData(
            id = "AtCore",
            box = PositionBox(52.0, 113.0, 49.0, 56.0, 117.0, 53.0),
            messageText = "At Core!",
            checkCondition = { _, _, stage -> stage in 2..4 }
        ),

        // Stage 4 - Inside Goldor Tunnel
        PositionData(
            id = "InsideGoldorTunnel",
            box = PositionBox(41.0, 110.0, 59.0, 68.0, 150.0, 117.0),
            messageText = "Inside Goldor Tunnel!",
            checkCondition = { _, _, stage -> stage == 4 }
        ),

        // Phase 4 - Mid到達
        PositionData(
            id = "AtMid",
            box = PositionBox(47.0, 64.0, 69.0, 61.0, 75.0, 83.0),
            messageText = "At Mid!",
            checkCondition = { phase, _, _ -> phase == M7Phases.P4 }
        ),

        // Phase 2,3 - Pre4 Entry（Healer以外）
        PositionData(
            id = "AtPre4Entry",
            box = PositionBox(91.0, 129.0, 44.0, 93.0, 133.0, 46.0),
            messageText = "At Pre4 Entry!",
            checkCondition = { phase, clazz, _ ->
                (phase == M7Phases.P2 || phase == M7Phases.P3) && clazz != DungeonClass.HEALER
            }
        ),

        // Phase 4 → P5到達通知（Healerのみ）
        PositionData(
            id = "AtP5",
            box = null,
            messageText = "At P5!",
            // Phase 4 & Healerのみ
            checkCondition = { phase, clazz, _ ->
                phase == M7Phases.P4 && clazz == DungeonClass.HEALER
            },
            // Y座標が4〜50の範囲
            checkPosition = { _, y, _, _ -> y < 50 && y > 4 }
        )
    )

    // 既に通知済みの位置を追跡
    private val notifiedPositions = mutableSetOf<String>()

    // 検索状態
    private var isSearching = false

    // プレイヤーのクラス
    private var playerClass: DungeonClass? = null

    // チャットメッセージパターン
    private val stormDefeatedPattern = "[BOSS] Storm: I should have known that I stood no chance."
    private val terminalCompletePattern = Regex("""\(([78])/\1\)""")

    init {
        // Storm撃破とターミナル完了を検知
        on<ChatPacketEvent> {
            val msg = value.noControlCodes

            // Storm撃破でStage 1に
            if (msg.contains(stormDefeatedPattern)) {
                currentStage = 1
            }

            // 7/7 or 8/8 到達でStage加算
            if (!msg.contains(":") && terminalCompletePattern.containsMatchIn(msg)) {
                currentStage += 1
            }
        }

        // Tick毎にプレイヤー位置をチェック
        on<TickEvent.End> {
            val currentPhase = DungeonUtils.getF7Phase()

            // ダンジョン外またはフェーズ不明なら無効
            if (!DungeonUtils.inDungeons || currentPhase == M7Phases.Unknown) {
                if (isSearching) {
                    reset()
                }
                return@on
            }

            // プレイヤークラスを取得
            if (playerClass == null) {
                playerClass = DungeonUtils.dungeonTeammates.find {
                    it.name == mc.player?.gameProfile?.name
                }?.clazz
            }

            if (playerClass == null) return@on

            isSearching = true

            val player = mc.player ?: return@on

            // 各位置をチェック
            positions.forEach { position ->
                // まだ通知していない & 条件を満たす & 範囲内にいる場合
                if (!notifiedPositions.contains(position.id) &&
                    position.checkCondition(currentPhase, playerClass, currentStage) &&
                    position.checkPosition(player.x, player.y, player.z, position.box)) {
                    notifiedPositions.add(position.id)
                    sendCommand("pc ${position.messageText}")
                }
            }
        }

        // ワールドアンロード時にリセット
        on<LevelEvent.Unload> {
            reset()
        }
    }

    object CheckBox{
        /**
         * プレイヤーが指定範囲内にいるか判定
         */
        fun isInsideBox(x: Double, y: Double, z: Double, box: PositionBox): Boolean {
            return x >= box.minX && x <= box.maxX &&
                   y >= box.minY && y <= box.maxY &&
                   z >= box.minZ && z <= box.maxZ
        }
    }

    /**
     * 状態をリセット
     */
    private fun reset() {
        notifiedPositions.clear()
        isSearching = false
        playerClass = null
        currentStage = 0
    }
}
