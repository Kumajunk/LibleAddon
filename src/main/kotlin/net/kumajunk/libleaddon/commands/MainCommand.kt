package net.kumajunk.libleaddon.commands

import com.github.stivais.commodore.Commodore
import com.github.stivais.commodore.utils.GreedyString
import com.odtheking.odin.OdinMod.mc
import com.odtheking.odin.OdinMod.scope
import com.odtheking.odin.utils.calculateDungeonLevel
import com.odtheking.odin.utils.formatNumber
import com.odtheking.odin.utils.formatTime
import com.odtheking.odin.utils.handlers.schedule
import com.odtheking.odin.utils.network.hypixelapi.HypixelData
import com.odtheking.odin.utils.network.hypixelapi.RequestUtils
import com.odtheking.odin.utils.toFixed
import kotlinx.coroutines.launch
import net.kumajunk.libleaddon.clickgui.AddonGUI
import net.kumajunk.libleaddon.clickgui.OdinGUI
import net.kumajunk.libleaddon.utils.addonMessage
import net.kumajunk.libleaddon.utils.sendMessage
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

/**
 * Command for opening the Addon settings GUI (LA features only).
 * Accessible via /la or /libleaddon
 * 
 * Subcommands:
 * - /la od or /la odin: Opens the Odin GUI (Odin features only, excludes LA)
 */
val addonCommand = Commodore("libleaddon", "la") {
    runs {
        schedule(0) { mc.setScreen(AddonGUI) }
    }
    
    // Subcommand to open Odin GUI (Odin features only)
    literal("od", "odin") {
        runs {
            schedule(0) { mc.setScreen(OdinGUI) }
        }
    }
}

/**
 * /lapv コマンド - プレイヤーのダンジョン統計を表示
 * 
 * 使用方法:
 * - /lapv : 自分のダンジョン統計を表示
 * - /lapv <プレイヤー名> : 指定したプレイヤーのダンジョン統計を表示
 */
val profileViewerCommand: Commodore = Commodore("lapv") {

    // No arguments: show your own stats
    runs {
        val name = mc.user.name
        addonMessage("§aFetching dungeon stats for §6$name§a...")
        scope.launch {
            fetchCataStats(RequestUtils.getProfile(name))
        }
    }

    // Player name specified: show stats of the specified player
    runs { mcid: GreedyString ->
        val name = mcid.string
        addonMessage("§aFetching dungeon stats for §6$name§a...")
        scope.launch {
            fetchCataStats(RequestUtils.getProfile(name))
        }
    }
}

/**
 * ダンジョン統計を取得して表示する
 * @param result プレイヤー情報の取得結果
 */
fun fetchCataStats(result: Result<HypixelData.PlayerInfo>, isPF: Boolean = false) {
    result.fold(
        onSuccess = { playerInfo ->
            playerInfo.memberData?.let { displayCataStats(playerInfo.name, it, isPF) }
                ?: addonMessage("§c§6${playerInfo.name}§cのプロファイルが見つかりませんでした！")
        },
        onFailure = { addonMessage("§c統計の取得に失敗しました: ${it.message}") }
    )
}

/**
 * ダンジョン統計をチャットに表示
 * @param name プレイヤー名
 * @param member メンバーデータ
 */
private fun displayCataStats(name: String, member: HypixelData.MemberData, isPF: Boolean = false) {
    val dungeons = member.dungeons
    val cata = dungeons.dungeonTypes.catacombs
    val mm = dungeons.dungeonTypes.mastermode
    
    // カタコンブレベルを計算
    val cataLevel = calculateDungeonLevel(cata.experience)
    
    // クラスレベルを計算
    val archerLevel = calculateDungeonLevel(dungeons.classes["archer"]?.experience ?: 0.0)
    val berserkLevel = calculateDungeonLevel(dungeons.classes["berserk"]?.experience ?: 0.0)
    val mageLevel = calculateDungeonLevel(dungeons.classes["mage"]?.experience ?: 0.0)
    val tankLevel = calculateDungeonLevel(dungeons.classes["tank"]?.experience ?: 0.0)
    val healerLevel = calculateDungeonLevel(dungeons.classes["healer"]?.experience ?: 0.0)
    
    // クラス平均を計算
    val classAverage = (archerLevel + berserkLevel + mageLevel + tankLevel + healerLevel) / 5.0
    
    // 選択中のクラス
    val selectedClass = dungeons.selectedClass?.replaceFirstChar { it.uppercase() } ?: "Unknown"
    
    // シークレット情報
    val secrets = dungeons.secrets
    val secretAvg = dungeons.avrSecrets
    val totalRuns = dungeons.totalRuns
    
    // Magical Power
    val magicalPower = member.assumedMagicalPower
    val tunings = member.tunings

    // 区切り線
    val divider = "§f§m-----------------------------------------------------§r"
    
    // ヘッダー表示
    sendMessage(divider)
    sendMessage("§b[§fLA§b] §r§fViewing §b$name§f's Dungeon Stats!")
    sendMessage(" ")
    
    // Magical Power（ホバーでTunings表示）
    val mpMessage = Component.literal("§fMagical Power: §b${formatNumber(magicalPower.toString())}")
        .withStyle { it.withHoverEvent(HoverEvent.ShowText(
            Component.literal("§bTunings").apply {
                tunings.forEach { tuning ->
                    append(Component.literal("\n§7- §e$tuning"))
                }
            }
        )) }
    sendMessage(mpMessage)
    
    // シークレット情報（ホバーで詳細表示）
    val secretsMessage = Component.literal("§fSecrets: §b${formatNumber(secrets.toString())} §7(${secretAvg.toFixed(1)} S/R)")
        .withStyle { it.withHoverEvent(HoverEvent.ShowText(
            Component.literal("§7Total Secrets: §e${formatNumber(secrets.toString())}\n§7Total Runs: §b$totalRuns\n§7Average: §a${secretAvg.toFixed()}")
        )) }
    sendMessage(secretsMessage)
    
    sendMessage("§fSelected Class: [§b$selectedClass§f]")
    sendMessage(" ")
    
    // カタコンブ情報
    sendMessage("§c§l⚔ §fCatacombs Level: §b${cataLevel.toFixed()}   §b§l⚛ §fClass Average: §b${classAverage.toFixed()}")
    sendMessage("§6Archer (${archerLevel.toInt()}) §cBerserk (${berserkLevel.toInt()}) §bMage (${mageLevel.toInt()}) §aTank (${tankLevel.toInt()}) §dHealer (${healerLevel.toInt()})")
    sendMessage(" ")
    
    // フロア情報（ホバーで詳細タイム表示）
    val floorsMessage = Component.literal("§7Floors: ")
        .append(Component.literal("§6Normal")
            .withStyle { it.withHoverEvent(HoverEvent.ShowText(buildFloorHover(cata, "§6§lNormal Floors", "§eF"))) })
        .append(Component.literal(" §8| "))
        .append(Component.literal("§cMaster")
            .withStyle { it.withHoverEvent(HoverEvent.ShowText(buildFloorHover(mm, "§c§lMaster Floors", "§cM"))) })
    sendMessage(floorsMessage)
    
    sendMessage(" ")
    
    // アーマー情報（アイコン+ホバーでlore表示）
    val armorPieces = getArmorPieces(member)
    if (armorPieces.isNotEmpty()) {
        sendMessage(buildArmorLine(armorPieces))
    }

    val kickMessage = Component.literal("§c§nKick $name from the party")
            .withStyle { it.withHoverEvent(HoverEvent.ShowText(Component.literal("§cClick to kick this player!"))) }
            .withStyle { it.withClickEvent(ClickEvent.RunCommand("/p kick $name")) }

    if (isPF) {
        sendMessage(divider)
        sendMessage(kickMessage)
    }
    // フッター
    sendMessage(divider)
}

/**
 * フロア詳細のホバーテキストを生成
 */
private fun buildFloorHover(dungeonType: HypixelData.DungeonTypeData, title: String, floorPrefix: String) =
    Component.literal(title).apply {
        (1..7).forEach { floor ->
            val key = "$floor"
            val sPlusMs = dungeonType.fastestTimeSPlus[key]?.toLong() ?: 0
            val bestMs = dungeonType.fastestTimes[key]?.toLong() ?: 0
            val comps = dungeonType.tierComps[key]?.toInt() ?: 0
            val timeStr = when {
                sPlusMs > 0 -> "§a${formatTime(sPlusMs, 0)}"
                bestMs > 0 -> "§7${formatTime(bestMs, 0)}"
                else -> "§8None"
            }
            append(Component.literal("\n$floorPrefix$floor: $timeStr §8(§b$comps§8)"))
        }
    }

/**
 * アーマー情報を取得
 */
private data class ArmorPiece(val slot: String, val itemStack: HypixelData.ItemData?)

private fun getArmorPieces(member: HypixelData.MemberData) = member.inventory?.invArmor?.itemStacks
    ?.takeIf { it.size >= 4 }
    ?.let { listOfNotNull(
        it[3]?.let { stack -> ArmorPiece("⛑", stack) },        // ヘルメット
        it[2]?.let { stack -> ArmorPiece("\uD83D\uDEE1", stack) }, // チェストプレート（盾アイコン）
        it[1]?.let { stack -> ArmorPiece("\uD83D\uDC56", stack) }, // レギンス（ズボンアイコン）
        it[0]?.let { stack -> ArmorPiece("\uD83D\uDC62", stack) }  // ブーツ（靴アイコン）
    )} ?: emptyList()

/**
 * アーマー行を構築（アイコン+ホバーでlore表示）
 */
private fun buildArmorLine(armorPieces: List<ArmorPiece>) = Component.literal("§7Armor: ").apply {
    armorPieces.forEachIndexed { index, (slot, itemStack) ->
        val displayName = itemStack?.name ?: "§8Empty"

        append(Component.literal(slot).withStyle { style ->
            itemStack?.let {
                val hover = Component.empty().append(Component.literal("$displayName\n"))
                it.lore.forEach { loreLine -> hover.append(loreLine).append(Component.literal("\n")) }
                style.withHoverEvent(HoverEvent.ShowText(hover))
            } ?: style.withHoverEvent(HoverEvent.ShowText(Component.literal("§8Empty Slot")))
        })

        if (index < armorPieces.lastIndex) append(Component.literal(" §8| "))
    }
}
