package net.kumajunk.libleaddon.features.impl.skyblock

import com.odtheking.odin.OdinMod.scope
import com.odtheking.odin.events.ChatPacketEvent
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.events.core.onReceive
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.Colors
import com.odtheking.odin.utils.network.hypixelapi.RequestUtils
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.render.textDim
import kotlinx.coroutines.launch
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket

object Soulflow : Module(
    name = "Soulflow(LA)",
    description = "Display Soulflow counts"
) {
    private val hud by HUD("Soulflow", desc = "Display Soulflow counts.", toggleable = false) { example ->
        if (example) {
            textDim("§3⸎ 160", 0, 0, Colors.WHITE)
        } else if (soulflowCounts > 0L) {
            textDim("§3⸎ $soulflowCounts", 0, 0, Colors.WHITE)
        } else
            0 to 0
    }

    var soulflowCounts: Long = 0L
    var lastEtherWarp: Long = 0L
    var lastGyro: Long = 0L
    var lastOverflowMana = 0
    private val overflowManaRegex = Regex("([0-9,]+)ʬ")
    
    // Auto-update settings
    private var tickCounter = 0
    private const val UPDATE_INTERVAL_TICKS = 36000 // 30 minutes (30 * 60 * 20 ticks)
    private var isUpdating = false

    init {
        on<ChatPacketEvent> {
            val msg = value.noControlCodes
            val regex = Regex("have a total of ([0-9,]+)⸎")
            val match = regex.find(msg)

            if (match != null) {
                val soulflowValue = match.groupValues[1].replace(",", "")
                soulflowCounts = soulflowValue.toLong()
            }
        }

        onReceive<ClientboundSystemChatPacket> { event ->
            val packet = event.packet as? ClientboundSystemChatPacket ?: return@onReceive
            if (!packet.overlay) return@onReceive

            val content = packet.content().string.noControlCodes

            if (content.contains("Ether Transmission")) {
                if (lastEtherWarp + 2500L > System.currentTimeMillis()) return@onReceive
                soulflowCounts--
                lastEtherWarp = System.currentTimeMillis()
            }
            if (content.contains("Gravity Storm")) {
                if (lastGyro + 4000L > System.currentTimeMillis()) return@onReceive
                soulflowCounts -= 10
                lastGyro = System.currentTimeMillis()
            }
            val match = overflowManaRegex.find(content)
            if (match != null) {
                val value = match.groupValues[1].replace(",", "").toInt()
                if (lastOverflowMana < value) {
                    soulflowCounts -= 15L
                }
                lastOverflowMana = value
            }
        }
        
        // Auto-update soulflow every 15 minutes
        on<TickEvent.End> {
            if (!enabled) return@on
            
            tickCounter++
            
            // Update every 15 minutes
            if (tickCounter >= UPDATE_INTERVAL_TICKS) {
                tickCounter = 0
                updateSoulflowFromAPI()
            }
        }
    }
    
    /**
     * Fetch soulflow count from Hypixel API
     */
    private fun updateSoulflowFromAPI() {
        if (isUpdating) return
        
        val name = mc.user.name.takeIf { !it.matches(Regex("Player\\d{2,3}")) } ?: return
        
        isUpdating = true
        scope.launch {
            try {
                RequestUtils.getProfile(name)
                    .fold(
                        onSuccess = { playerInfo ->
                            playerInfo.memberData?.let { memberData ->
                                soulflowCounts = memberData.miscItemData.soulflow
                                println("[LA] Updated soulflow count: $soulflowCounts")
                            }
                        },
                        onFailure = { 
                            println("[LA] Failed to update soulflow: ${it.message}")
                        }
                    )
            } finally {
                isUpdating = false
            }
        }
    }
}
