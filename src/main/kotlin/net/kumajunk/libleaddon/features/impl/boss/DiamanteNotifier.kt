package net.kumajunk.libleaddon.features.impl.boss

import com.odtheking.odin.clickgui.settings.impl.ColorSetting
import com.odtheking.odin.clickgui.settings.impl.NumberSetting
import com.odtheking.odin.events.ChatPacketEvent
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.LevelEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.Color
import com.odtheking.odin.utils.handlers.schedule
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.render.textDim
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.monster.Giant
import java.util.Locale.getDefault

/**
 * Floor7のDiamante Giant検出および通知を行うモジュール
 */
object DiamanteNotifier : Module(
    name = "Diamante Giant Notifier(LA)",
    description = "Highlights and notifies when a Diamante Giant is detected."
) {
    // 設定項目
    private val displayTime by NumberSetting(
        name = "Display Time",
        default = 3.0,
        min = 1.0,
        max = 10.0,
        increment = 0.5,
        desc = "Duration to display the notification."
    )
    private val color by ColorSetting(
        name = "Notification Color",
        default = Color(255, 85, 85), // RED
        desc = "Color of the notification text."
    )

    // HUD
    private val hud by HUD("Diamante Notification", desc = "Displays detection message.", toggleable = false) { example ->
        if (example) {
            textDim("Diamante Giant Detected!", 0, 0, color)
        } else if (currentMessage != null) {
            textDim(currentMessage!!, 0, 0, color)
        } else {
            0 to 0
        }
    }

    // 状態管理
    private var isBloodOpen = false
    private var currentMessage: String? = null
    private var lastCheckTime = 0L

    init {
        // Blood Doorが開いたメッセージを検知
        on<ChatPacketEvent> {
            val msg = value.noControlCodes
            if (msg.trim() == "The BLOOD DOOR has been opened!") {
                isBloodOpen = true
            }
        }

        // ワールドアンロード時にリセット
        on<LevelEvent.Unload> {
            isBloodOpen = false
            currentMessage = null
        }

        // 定期的にGiantをスキャン (2FPS = 500ms間隔)
        on<TickEvent.End> {
            if (!isBloodOpen || mc.level == null) return@on

            if (System.currentTimeMillis() - lastCheckTime < 500) return@on
            lastCheckTime = System.currentTimeMillis()

            val world = mc.level ?: return@on

            val giants = world.entitiesForRendering().filterIsInstance<Giant>()

            if (giants.isEmpty()) return@on

            var interestingFound = false

            for (giant in giants) {
                // 装備情報の取得 (0:MainHand, 1:Boots, 2:Leggings, 3:Chestplate, 4:Helmet)
                val helmetStack = giant.getItemBySlot(EquipmentSlot.HEAD)
                val chestStack  = giant.getItemBySlot(EquipmentSlot.CHEST)
                val legsStack   = giant.getItemBySlot(EquipmentSlot.LEGS)
                val bootsStack  = giant.getItemBySlot(EquipmentSlot.FEET)

                val helmet = helmetStack.hoverName.string
                val chest  = chestStack.hoverName.string
                val legs   = legsStack.hoverName.string
                val boots  = bootsStack.hoverName.string

                val isDiamante = listOf(helmet, chest, legs, boots).any { it.lowercase(getDefault()).contains("diamond") }
                val isBoomer = helmet.lowercase(getDefault()).contains("superboom tnt")

                if (isDiamante && !isBoomer) {
                    showTitle("Diamante Giant Detected!")
                    interestingFound = true
                    break
                } else if (isDiamante) {
                    showTitle("Boomer Diamante Giant Detected!")
                    interestingFound = true
                    break
                } else if (isBoomer) {
                    showTitle("Boomer Giant Detected!")
                    interestingFound = true
                    break
                }
            }
            
            // Giantが見つかった時点で、興味深い個体がいなくてもスキャン終了 (JSの挙動準拠)
            resetNotifier()
        }
    }

    private fun showTitle(text: String) {
        currentMessage = text
        // 指定時間後にメッセージを消去
        schedule((displayTime * 20).toInt()) {
            currentMessage = null
        }
    }

    private fun resetNotifier() {
        isBloodOpen = false
    }
}
