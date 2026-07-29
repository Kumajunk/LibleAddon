package net.kumajunk.libleaddon.features.impl.boss

import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.clickgui.settings.impl.ColorSetting
import com.odtheking.odin.events.ChatPacketEvent
import com.odtheking.odin.events.LevelEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.Color
import com.odtheking.odin.utils.alert
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.render.textDim

/**
 * Floor7 P1フェーズ中にEnergy Crystalを所持している場合、配置を促す通知をHUDに表示するモジュール
 */
object CrystalNotifier : Module(
    name = "Crystal Notifier(LA)",
    description = "Displays a notification when you are holding an Energy Crystal during P1."
) {
    // 設定項目
    private val notifyColor by ColorSetting(
        name = "Notify Color",
        default = Color(255, 85, 85),
        allowAlpha = false,
        desc = "Color of the 'Place Crystal!' notification text."
    )

    private val notifyPlaced by BooleanSetting(
        name = "Notify placed crystal",
        desc = "Sends a notification when the crystal is successfully placed."
    )

    // P1フェーズ状態管理（Maxor開始～Storm開始）
    private var isP1 = false

    private var hasCrystal = false
        set(value) {
            if (field && !value && notifyPlaced) {
                alert("§aPlaced!", true)
            }
            field = value
        }

    // チャットパターン
    private val maxorPattern = Regex("""^\[BOSS] Maxor: (.+)$""")
    private val stormPattern = Regex("""^\[BOSS] Storm: (.+)$""")

    // HUD設定
    private val notifyHud by HUD("Crystal Notifier", desc = "Displays 'Place Crystal!' notification.", toggleable = false) { example ->
        if (example) {
            // プレビュー表示
            textDim("Place Crystal!", 0, 0, notifyColor)
        } else if (!isP1) {
            0 to 0
        } else {
            // P1フェーズ中のみチェック
            val player = mc.player ?: return@HUD 0 to 0
            val inventory = player.inventory

            // スロット8（ホットバー右端）のアイテムをチェック
            val item = inventory.getItem(8)
            val itemName = item.hoverName.string.noControlCodes

            if (itemName.contains("Energy Crystal")) {
                hasCrystal = true
                textDim("Place Crystal!", 0, 0, notifyColor)
            } else {
                hasCrystal = false
                0 to 0
            }
        }
    }

    init {
        // Maxorのセリフ検知でP1開始
        on<ChatPacketEvent> {
            val msg = value.noControlCodes
            if (maxorPattern.matches(msg)) {
                isP1 = true
            }
            // Stormのセリフ検知でP1終了
            if (stormPattern.matches(msg)) {
                isP1 = false
            }
        }

        // ワールドアンロード時にリセット
        on<LevelEvent.Unload> {
            isP1 = false
        }
    }
}