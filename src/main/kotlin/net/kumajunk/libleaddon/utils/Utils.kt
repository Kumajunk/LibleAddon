package net.kumajunk.libleaddon.utils

import com.odtheking.odin.OdinMod.mc
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

fun addonMessage(message: Any?, chatStyle: Style? = null) {
    val prefix = "§b[§fLA§b] §r"
    val text = Component.literal("$prefix$message")
    chatStyle?.let { text.setStyle(chatStyle) }
    mc.execute { mc.gui.chat.addClientSystemMessage(text) }
}

/**
 * Component型を直接受け付けるaddonMessageのオーバーロード
 * ホバーイベント等のスタイルを保持したまま表示可能
 */
fun addonMessage(component: Component) {
    val prefix = "§b[§fLA§b] §r"
    val text = Component.literal(prefix).append(component)
    mc.schedule { mc.gui.chat.addClientSystemMessage(text) }
}

/**
 * プレフィックスなしでメッセージを送信
 * @param message 送信するメッセージ
 */
fun sendMessage(message: Any) {
    val text = Component.literal("$message")
    mc.execute { mc.gui.chat.addClientSystemMessage(text) }
}

/**
 * プレフィックスなしでComponentを送信
 * ホバーイベント等のスタイルを保持したまま表示可能
 */
fun sendMessage(component: Component) {
    mc.execute { mc.gui.chat.addClientSystemMessage(component) }
}