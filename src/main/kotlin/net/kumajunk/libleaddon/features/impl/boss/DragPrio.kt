package net.kumajunk.libleaddon.features.impl.boss

import com.odtheking.odin.clickgui.settings.Setting.Companion.withDependency
import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.clickgui.settings.impl.DropdownSetting
import com.odtheking.odin.clickgui.settings.impl.NumberSetting
import com.odtheking.odin.clickgui.settings.impl.SelectorSetting
import com.odtheking.odin.events.BlockUpdateEvent
import com.odtheking.odin.events.ChatPacketEvent
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.LevelEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.events.core.onReceive
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.Colors
import com.odtheking.odin.utils.render.textDim
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import com.odtheking.odin.utils.skyblock.dungeon.M7Phases
import com.odtheking.odin.utils.toFixed
import net.kumajunk.libleaddon.features.impl.boss.dragprio.DragonCheck
import net.kumajunk.libleaddon.features.impl.boss.dragprio.WitherDragonState
import net.kumajunk.libleaddon.features.impl.boss.dragprio.WitherDragonsEnum
import net.kumajunk.libleaddon.features.impl.boss.dragprio.handleSpawnPacket
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket

/*
 * code from odinFabric (https://github.com/odtheking/OdinFabric)
 */
object DragPrio : Module(
    name = "DragPrio(LA)",
    description = "Displays the priority of dragons in Master Mode Floor 7."
){
    private val hud by HUD("Dragon Timer HUD", "Displays the dragon timer in the HUD.") { example ->
        if (example) textDim("§54.5", 0, 0, Colors.WHITE)
        else {
            priorityDragon?.let { dragon ->
                if (dragon.timeToSpawn <= 0) return@HUD 0 to 0
                textDim("§${dragon.colorCode}${getDragonTimer(dragon.timeToSpawn)}", 0, 0, Colors.WHITE)
            } ?: (0 to 0)
        }
    }
    val dragonTitle by BooleanSetting("Dragon Title", true, desc = "Displays a title for spawning dragons.")


    private val dragonPriorityDropDown by DropdownSetting("Dragon Priority Dropdown")
    val dragonPriorityToggle by BooleanSetting("Dragon Priority", true, desc = "Displays the priority of dragons spawning.").withDependency { dragonPriorityDropDown }
    val normalPower by NumberSetting("Normal Power", 0, 0, 32, desc = "Power needed to split.").withDependency { dragonPriorityToggle && dragonPriorityDropDown }
    val easyPower by NumberSetting("Easy Power", 0, 0, 32, desc = "Power needed when its Purple and another dragon.").withDependency { dragonPriorityToggle && dragonPriorityDropDown }
    val soloDebuff by SelectorSetting("Purple Solo Debuff", "Tank", arrayListOf("Tank", "Healer"), desc = "The class that solo debuffs purple, the other class helps b/m.").withDependency { dragonPriorityToggle && dragonPriorityDropDown }
    val soloDebuffOnAll by BooleanSetting("Solo Debuff on All Splits", false, desc = "Same as Purple Solo Debuff but for all dragons (A will only have 1 debuff).").withDependency { dragonPriorityToggle && dragonPriorityDropDown }
    val paulBuff by BooleanSetting("Paul Buff", false, desc = "Multiplies the power in your run by 1.25.").withDependency { dragonPriorityToggle && dragonPriorityDropDown }

    val witherKingRegex = Regex("^\\[BOSS] Wither King: (Oh, this one hurts!|I have more of those\\.|My soul is disposable\\.)$")
    var priorityDragon: WitherDragonsEnum? = null
    var currentTick = 0L

    init {
        onReceive<ClientboundLevelParticlesPacket> {
            if (DungeonUtils.getF7Phase() == M7Phases.P5) handleSpawnPacket(this)
        }

        onReceive<ClientboundSetEquipmentPacket> {
            if (DungeonUtils.getF7Phase() == M7Phases.P5) DragonCheck.dragonSprayed(this)
        }

        onReceive<ClientboundAddEntityPacket> {
            if (DungeonUtils.getF7Phase() == M7Phases.P5) DragonCheck.dragonSpawn(this)
        }

        onReceive<ClientboundSetEntityDataPacket> {
            if (DungeonUtils.getF7Phase() == M7Phases.P5) DragonCheck.dragonUpdate(this)
        }

        on<BlockUpdateEvent> {
            if (DungeonUtils.getF7Phase() == M7Phases.P5 && updated.isAir)
                WitherDragonsEnum.entries.find { it.statuePos == pos }?.setDead(false)
        }

        on<ChatPacketEvent> {
            if (DungeonUtils.getF7Phase() != M7Phases.P5 || !witherKingRegex.matches(value)) return@on
            (DragonCheck.lastDragonDeath ?: WitherDragonsEnum.entries.find { it.state != WitherDragonState.DEAD })
                ?.apply {
                    if (state != WitherDragonState.DEAD) setDead(false)
                    DragonCheck.lastDragonDeath = null
                }
        }

        on<TickEvent.Server> {
            WitherDragonsEnum.entries.forEach {
                if (it.timeToSpawn > 0) it.timeToSpawn--
                else if (it.state == WitherDragonState.SPAWNING) it.setAlive(null)
            }
            currentTick++
        }

        on<LevelEvent.Load> {
            DragonCheck.dragonHealthMap.clear()
            WitherDragonsEnum.reset()
        }
    }

    private fun getDragonTimer(spawnTime: Int): String = (spawnTime / 20f).toFixed(2)
}