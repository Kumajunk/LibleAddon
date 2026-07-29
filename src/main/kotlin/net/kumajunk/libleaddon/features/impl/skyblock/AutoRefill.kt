package net.kumajunk.libleaddon.features.impl.skyblock

import com.odtheking.odin.clickgui.settings.Setting.Companion.withDependency
import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.clickgui.settings.impl.NumberSetting
import com.odtheking.odin.events.ChatPacketEvent
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.LevelEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.itemId
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.sendCommand
import com.odtheking.odin.utils.skyblock.Island
import com.odtheking.odin.utils.skyblock.LocationUtils
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import net.kumajunk.libleaddon.utils.addonMessage

object AutoRefill : Module(
    name = "Auto Refill(LA)",
    description = "Automatically refill consumable items",
) {
    private var isZeroRefill = +BooleanSetting(
        name = "Allow Zero Refill",
        default = false,
        desc = "Whether to replenish items when they reach 0"
    )

    private var isPearlRefill = +BooleanSetting(
        name = "Pearl Refill",
        default = true,
        desc = "Automatically refills ender pearls"
    )

    private var pearlThreshold = +NumberSetting(
        name = "Pearl Threshold",
        default = 8,
        min = 1,
        max = 16,
        desc = "The amount of pearls you need to have to trigger a refill"
    ).withDependency { isPearlRefill.value }

    private var isSuperboomRefill = +BooleanSetting(
        name = "Superboom Refill",
        default = false,
        desc = "Automatically refills superboom TNT"
    )

    private var superboomThreshold = +NumberSetting(
        name = "Superboom Threshold",
        default = 32,
        min = 1,
        max = 64,
        desc = "The amount of superboom TNT you need to have to trigger a refill"
    ).withDependency { isSuperboomRefill.value }

    private var tick = 0
    private var isDead = false

    private var pearlLastRefillTime = 0L
    private var superboomLastRefillTime = 0L

    private const val REFILL_COOLDOWN = 5000L

    init {
        on<TickEvent.End> {
            tick++

            if (tick % 10 != 0) return@on
            if (!LocationUtils.isInSkyblock || LocationUtils.currentArea == Island.Rift) return@on
            if (mc.screen != null) return@on

            checkHaunt()

            if (isDead) return@on

            if (isPearlRefill.value) {
                val pearlCount = mc.player?.inventory?.find {
                    it.itemId == "ENDER_PEARL"
                }?.count ?: 0

                if (pearlCount != 0 || isZeroRefill.value) {
                    val now = System.currentTimeMillis()

                    if (
                        pearlCount < pearlThreshold.value &&
                        now - pearlLastRefillTime >= REFILL_COOLDOWN
                    ) {
                        sendCommand("gfs ender_pearl ${16 - pearlCount}")
                        addonMessage("§aAuto Refilled Ender Pearls!")

                        pearlLastRefillTime = now
                        return@on
                    }
                }
            }

            if (isSuperboomRefill.value) {
                val superboomCount = mc.player?.inventory?.find {
                    it.itemId == "SUPERBOOM_TNT"
                }?.count ?: 0

                if (superboomCount != 0 || isZeroRefill.value) {
                    val now = System.currentTimeMillis()

                    if (
                        superboomCount < superboomThreshold.value &&
                        now - superboomLastRefillTime >= REFILL_COOLDOWN
                    ) {
                        sendCommand("gfs superboom_tnt ${64 - superboomCount}")
                        addonMessage("§aAuto Refilled Superboom TNT!")

                        superboomLastRefillTime = now
                        return@on
                    }
                }
            }

            tick = 0
        }

        on<LevelEvent.Load> {
            isDead = false
        }

        on<ChatPacketEvent> {
            if (DungeonUtils.inDungeons) {
                val msg = value.noControlCodes

                if (
                    msg.contains("☠") &&
                    msg.contains("became a ghost.") &&
                    !msg.contains(":")
                ) {
                    val split = msg.split(" ")

                    if (split.getOrNull(2) == "You") {
                        isDead = true
                    }
                }
            }
        }
    }

    private fun checkHaunt() {
        val player = mc.player ?: return
        val stack = player.inventory.getItem(0)

        if (stack.isEmpty) return

        val name = stack.displayName.string.noControlCodes
        isDead = name.contains("Haunt")
    }
}