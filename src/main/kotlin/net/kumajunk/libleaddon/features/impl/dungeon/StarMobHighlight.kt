package net.kumajunk.libleaddon.features.impl.dungeon

import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.clickgui.settings.impl.ColorSetting
import com.odtheking.odin.events.PacketEvent
import com.odtheking.odin.events.RenderEvent
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.LevelEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.Color
import com.odtheking.odin.utils.Color.Companion.withAlpha
import com.odtheking.odin.utils.render.drawFilledBox
import com.odtheking.odin.utils.render.drawWireFrameBox
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.entity.player.Player

object StarMobHighlight : Module(
    name = "StarMob Highlight(LA)",
    description = "Highlights starred mobs in dungeons."
) {
    // 検知済みのアーマースタンドを記録（重複検知防止）
    private val checkedArmorStands = HashSet<Int>()

    private val tickScannedArmorStands = HashSet<Int>()

    // スター付きモブのエンティティID一覧
    private val starMobs = HashSet<Int>()

    private val filledOutline = +BooleanSetting(
        name = "Filled Outline",
        default = false,
        desc = "Whether to draw filled outline or not"
    )

    private val espBats = +BooleanSetting(
        name = "Highlight Bats",
        default = true,
        desc = "Highlights bats in dungeons"
    )
    private val espFels = +BooleanSetting(
        name = "Highlight Fels",
        default = true,
        desc = "Highlights Fels (Dinnerbone) in dungeons"
    )
    private val customMiniBossColors = +BooleanSetting(
        name = "Custom Miniboss Colors",
        default = true,
        desc = "Use custom colors for minibosses"
    )

    private val starMobColor = +ColorSetting(
        name = "Star Mob Color",
        default = Color(255, 85, 85), // Red
        allowAlpha = false,
        desc = "Highlight color for starred mobs"
    )
    private val batColor = +ColorSetting(
        name = "Bat Color",
        default = Color(85, 255, 85), // Green
        allowAlpha = false,
        desc = "Highlight color for bats"
    )
    private val felColor = +ColorSetting(
        name = "Fel Color",
        default = Color(255, 85, 255), // Pink
        allowAlpha = false,
        desc = "Highlight color for Fels"
    )

    private val shadowAssassinColor = +ColorSetting(
        name = "Shadow Assassin Color",
        default = Color(85, 85, 255), // Blue
        allowAlpha = false,
        desc = "Highlight color for Shadow Assassins"
    )
    private val angryArchaeologistColor = +ColorSetting(
        name = "Angry Archaeologist Color",
        default = Color(255, 170, 0), // Orange
        allowAlpha = false,
        desc = "Highlight color for Angry Archaeologists"
    )
    private val unstableDragonColor = +ColorSetting(
        name = "Unstable Dragon Color",
        default = Color(255, 255, 0), // Yellow
        allowAlpha = false,
        desc = "Highlight color for Unstable Dragons"
    )
    private val youngDragonColor = +ColorSetting(
        name = "Young Dragon Color",
        default = Color(255, 255, 0), // Yellow
        allowAlpha = false,
        desc = "Highlight color for Young Dragons"
    )
    private val superiorDragonColor = +ColorSetting(
        name = "Superior Dragon Color",
        default = Color(255, 255, 0), // Yellow
        allowAlpha = false,
        desc = "Highlight color for Superior Dragons"
    )
    private val holyDragonColor = +ColorSetting(
        name = "Holy Dragon Color",
        default = Color(255, 255, 0), // Yellow
        allowAlpha = false,
        desc = "Highlight color for Holy Dragons"
    )
    private val frozenAdventurerColor = +ColorSetting(
        name = "Frozen Adventure Color",
        default = Color(0, 255, 255), // Cyan
        allowAlpha = false,
        desc = "Highlight color for Frozen Adventures"
    )

    init {
        // ワールドアンロード時にデータをクリア
        on<LevelEvent.Unload> {
            checkedArmorStands.clear()
            tickScannedArmorStands.clear()
            starMobs.clear()
        }

        // パケット受信時にスター付きモブを検知
        on<PacketEvent.Receive> {
            if (!DungeonUtils.inClear) return@on

            val p = packet
            if (p is ClientboundSetEntityDataPacket) {
                val world = mc.level ?: return@on
                val entity = world.getEntity(p.id) ?: return@on

                if (entity is ArmorStand && entity.hasCustomName()) {
                    val customName = entity.customName?.string ?: return@on

                    // スターマーク（§6✯）を含む名前を持つアーマースタンドを検出
                    if (customName.contains("§6✯") || customName.contains("✯")) {
                        checkStarMob(entity)
                    }
                }
            }

            if (p is ClientboundAddEntityPacket) {

                if (p.type != EntityType.PLAYER) return@on

                val info = mc.connection?.getPlayerInfo(p.uuid) ?: return@on
                val name = info.profile.name

                if (name !in listOf(
                        "Shadow Assassin",
                        "Lost Adventurer",
                        "Diamond Guy",
                        "King Midas"
                    )) return@on

                val entity = mc.level?.getEntity(p.id) ?: return@on
                starMobs.add(entity.id)
            }
        }

        // レンダリングイベントでESPを描画
        on<RenderEvent.Extract> {
            if (!DungeonUtils.inClear) return@on

            val world = mc.level ?: return@on

            for (entity in world.entitiesForRendering()) {

                val color = when {
                    entity.id in starMobs -> getColor(entity) ?: starMobColor.value
                    else -> getColor(entity)
                } ?: continue

                drawNormalHighlight(entity, color, filledOutline.value)
            }
        }

        on<TickEvent.End> {
            if (!DungeonUtils.inClear) return@on

            val world = mc.level ?: return@on

            for (entity in world.entitiesForRendering()) {
                if (entity !is ArmorStand) continue
                if (!entity.hasCustomName()) continue
                if (entity.id in tickScannedArmorStands) continue

                val name = entity.customName?.string ?: continue
                if (!name.contains("§6✯") && !name.contains("✯")) continue

                tickScannedArmorStands.add(entity.id)
                checkStarMob(entity)
            }
        }
    }

    /**
     * アーマースタンドからスター付きモブの本体を特定してリストに追加する。
     */
    private fun checkStarMob(armorStand: ArmorStand) {
        if (armorStand.id in checkedArmorStands) return
        checkedArmorStands.add(armorStand.id)

        val world = armorStand.level()
        val name = armorStand.customName?.string?.uppercase() ?: return

        // ウィザーマンサーは常に -3（間に2つのスカルがある）
        val idOffset = if (name.contains("WITHERMANCER")) 3 else 1

        // エンティティIDから本体を検索
        val mobEntity = world.getEntity(armorStand.id - idOffset)
        if (mobEntity != null && mobEntity !is ArmorStand && mobEntity.id !in starMobs) {
            starMobs.add(mobEntity.id)
            return
        }

        // バウンディングボックス内のエンティティから本体を検索
        val searchBox = armorStand.boundingBox.move(0.0, -1.0, 0.0)
        val possibleEntities = world.getEntities(armorStand, searchBox) { it !is ArmorStand }

        possibleEntities.find { entity ->
            entity.id !in starMobs && when (entity) {
                is Player -> !entity.isInvisible && entity.uuid.version() == 2 && entity != mc.player && !DungeonUtils.dungeonTeammates.any { it.entity == entity }
                is WitherBoss -> false
                else -> true
            }
        }?.let { mob ->
            starMobs.add(mob.id)
        }
    }

    private fun getColor(entity: Entity): Color? {
        return when (entity) {

            is Bat ->
                if (espBats.value && !entity.isInvisible && !entity.isPassenger)
                    batColor.value else null

            is EnderMan ->
                if (entity.id in starMobs && espFels.value && entity.name.string == "Dinnerbone")
                    felColor.value else null

            is Player -> {

                val name = entity.name.string

                when {
                    name.contains("Shadow Assassin") -> {
                        if (customMiniBossColors.value)
                            shadowAssassinColor.value
                        else
                            starMobColor.value
                    }

                    DungeonUtils.floor?.floorNumber == 4 && !DungeonUtils.inBoss -> {

                        val boots = entity.getItemBySlot(EquipmentSlot.FEET)
                        if (boots.isEmpty) return null

                        val itemName = boots.hoverName.string

                        when (name) {

                            "Lost Adventurer" -> {
                                if (!customMiniBossColors.value) starMobColor.value
                                else when (itemName) {
                                    "§6Unstable Dragon Boots" -> unstableDragonColor.value
                                    "§6Young Dragon Boots" -> youngDragonColor.value
                                    "§6Superior Dragon Boots" -> superiorDragonColor.value
                                    "§6Holy Dragon Boots" -> holyDragonColor.value
                                    "§6Frozen Blaze Boots" -> frozenAdventurerColor.value
                                    else -> null
                                }
                            }

                            "Diamond Guy" -> {
                                if (customMiniBossColors.value &&
                                    itemName.startsWith("§6Perfect Boots - Tier")
                                ) angryArchaeologistColor.value
                                else starMobColor.value
                            }

                            else -> null
                        }
                    }

                    else -> null
                }
            }

            else -> null
        }
    }

    private fun RenderEvent.Extract.drawNormalHighlight(entity: Entity, color: Color, isFilled: Boolean) {
        val box = entity.boundingBox
        drawWireFrameBox(box, color, 1f, true)
        if (isFilled) {
            color.withAlpha(0.3f, false)
            drawFilledBox(box, color, true)
        }
    }
}