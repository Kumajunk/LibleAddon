package net.kumajunk.libleaddon.features.impl.boss

import com.odtheking.odin.events.ChatPacketEvent
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.LevelEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.skyblock.dungeon.DungeonClass
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import net.kumajunk.libleaddon.LibleAddon
import net.kumajunk.libleaddon.utils.addonMessage

/**
 * Floor7のPredev (Healer) タイマー
 */
object Predev : Module(
    name = "Predev Timer(LA)",
    description = "Timer for Predev (Healer) in Floor 7."
) {
    private var isPredev = false
    private var predevSuccess = false
    private var startTime = 0L
    private var leverTime = 0L
    private var arrowTime = 0L

    // minPos: {x: 58, y: 131.5, z: 138}, maxPos: {x: 62, y: 137, z: 143}
    private val LEVER_MIN = Triple(58.0, 131.5, 138.0)
    private val LEVER_MAX = Triple(62.0, 137.0, 143.0)

    // minPos: {x: -2, y: 120, z: 76}, maxPos: {x: 4, y: 125, z: 79}
    private val ARROW_MIN = Triple(-2.0, 120.0, 76.0)
    private val ARROW_MAX = Triple(4.0, 125.0, 79.0)

    init {
        on<ChatPacketEvent> {
            val msg = value.noControlCodes

            // Start Timer
            if (msg.contains("[BOSS] Maxor: WELL")) {
                // Healer以外ならリターン
                val myName = LibleAddon.playerName ?: mc.user.name
                val myClass = DungeonUtils.dungeonTeammates.find { it.name == myName }?.clazz
                if (myClass != DungeonClass.HEALER) return@on

                predevSuccess = false
                isPredev = true
                startTime = System.currentTimeMillis()
                leverTime = 0
                arrowTime = 0
                return@on
            }

            // End Timer
            val match = Regex("You have teleported to (\\w+)!").find(msg)
            if (match != null && isPredev) {
                val predevTimeEnd = System.currentTimeMillis()
                
                if (predevSuccess) {
                    val clearTime = (predevTimeEnd - startTime) / 1000.0
                    val startToLever = (leverTime - startTime) / 1000.0
                    val leverToArrow = (arrowTime - leverTime) / 1000.0

                    addonMessage("§b §rPredev completed in §b${String.format("%.2f", clearTime)}s")
                    addonMessage("§b §rStart to Lever: §b${String.format("%.2f", startToLever)}s§f, Lever to Arrow: §b${String.format("%.2f", leverToArrow)}s")
                } else {
                    val leverSuccess = if (leverTime == 0L) "failed" else "success"
                    val arrowSuccess = if (arrowTime == 0L) "failed" else "success"
                    val leapTime = (predevTimeEnd - startTime) / 1000.0
                    
                    addonMessage("§b §rPredev failed!")
                    addonMessage("§b §rLever: $leverSuccess, Arrow: $arrowSuccess, LeapTime: ${String.format("%.2f", leapTime)}s")
                }
                isPredev = false
            }
        }

        on<TickEvent.End> {
            if (!isPredev || predevSuccess) return@on

            val player = mc.player ?: return@on

            // Check Lever
            if (leverTime == 0L && isInsideBox(player, LEVER_MIN, LEVER_MAX)) {
                leverTime = System.currentTimeMillis()
                checkSuccess()
            }

            // Check Arrow
            if (arrowTime == 0L && isInsideBox(player, ARROW_MIN, ARROW_MAX)) {
                arrowTime = System.currentTimeMillis()
                checkSuccess()
            }
        }

        on<LevelEvent.Unload> {
            predevSuccess = false
            isPredev = false
            startTime = 0
            leverTime = 0
            arrowTime = 0
        }
    }

    private fun checkSuccess() {
        if (leverTime != 0L && arrowTime != 0L) {
            predevSuccess = true
        }
    }

    private fun isInsideBox(player: net.minecraft.world.entity.player.Player, min: Triple<Double, Double, Double>, max: Triple<Double, Double, Double>): Boolean {
        return player.x >= min.first && player.x <= max.first &&
               player.y >= min.second && player.y <= max.second &&
               player.z >= min.third && player.z <= max.third
    }
}
