package net.kumajunk.libleaddon

import com.odtheking.odin.OdinMod.mc
import com.odtheking.odin.OdinMod.scope
import com.odtheking.odin.config.ModuleConfig
import com.odtheking.odin.events.core.EventBus
import com.odtheking.odin.features.ModuleManager
import com.odtheking.odin.utils.network.hypixelapi.RequestUtils
import com.odtheking.odin.utils.network.hypixelapi.RequestUtils.getUuid
import com.odtheking.odin.features.Module
import kotlinx.coroutines.launch
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.kumajunk.libleaddon.commands.addonCommand
import net.kumajunk.libleaddon.commands.profileViewerCommand
import net.kumajunk.libleaddon.features.impl.boss.*
import net.kumajunk.libleaddon.features.impl.dungeon.*
import net.kumajunk.libleaddon.features.impl.dungeon.map.DungeonMap
import net.kumajunk.libleaddon.features.impl.render.HideArmor
import net.kumajunk.libleaddon.features.impl.render.NoFire
import net.kumajunk.libleaddon.features.impl.render.NoHurtCam
import net.kumajunk.libleaddon.features.impl.render.RemoveGlow
import net.kumajunk.libleaddon.features.impl.skyblock.AutoRefill
import net.kumajunk.libleaddon.features.impl.skyblock.Soulflow
import java.util.*

object LibleAddon : ClientModInitializer {

    var playerUUID: UUID? = null
    var playerName: String? = null
    
    /**
     * List of all modules registered by this addon.
     * Used by AddonGUI to filter and display only addon modules.
     */
    val addonModules = mutableSetOf<Module>()

    override fun onInitializeClient() {
        // Register commands by adding to the array
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            arrayOf(addonCommand, profileViewerCommand).forEach { commodore -> commodore.register(dispatcher) }
        }

        // Register objects to event bus by adding to the list
        listOf(this).forEach { EventBus.subscribe(it) }

        // Register modules by adding to the list
        val modules = arrayOf(
            // dungeon
            AutoPotionBag,
            BloodRushSplit,
            CalcLagLoss,
            ClassDupeNotifier,
            CryptReminder,
            DungeonMap,
            LeapAnnounce,
            MaskTimer,
            ScoreMilestone,
            ShieldCooldown,
            StarMobHighlight,
            StatsViewer,
            WarpCooldown,
            WarpSuccessNotifier,
            TrueSplit,

            // boss
            CoreTime,
            CrushTimer,
            CrystalNotifier,
            DiamanteNotifier,
            DragPrio,
            HidePlayerOnLeap,
            I4Helper,
            I4Timer,
            PositionNotifier,
            Predev,
            PreEnterNotifier,
            PurplePad,
            SimonSaysTimer,
            StormKillTime,

            // render
            HideArmor,
            NoFire,
            NoHurtCam,
            RemoveGlow,

            // skyblock
            AutoRefill,
            Soulflow
        )
        
        // Store addon modules for filtering in AddonGUI
        addonModules.addAll(modules)
        
        // Register modules with ModuleManager
        ModuleManager.registerModules(ModuleConfig("LibleAddon.json"), *modules)

        val name = mc.user.name.takeIf { !it.matches(Regex("Player\\d{2,3}")) } ?: return
        scope.launch {
            getUuid(name)
                .onSuccess { uuid ->
                    playerUUID = uuid.id.toUUID()
                    playerName = name
                }
                .onFailure {
                    println("Failed to get UUID for $name: ${it.message}")
                }
        }
        scope.launch {
            RequestUtils.getProfile(name)
                .fold(
                    onSuccess = { playerInfo ->
                        playerInfo.memberData?.let { memberData ->
                            Soulflow.soulflowCounts = memberData.miscItemData.soulflow
                        }
                            ?: println("Failed to get soulflow data for $name")
                    },
                    onFailure = { println("Failed to get profile for $name: ${it.message}") }
                )
        }
        println("LibleAddon initialized!")
    }

    fun String.toUUID(): UUID {
        val dashed = if (this.contains("-")) {
            this
        } else {
            // 8-4-4-4-12 の位置にハイフンを挿入
            this.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})".toRegex(),
                "$1-$2-$3-$4-$5"
            )
        }
        return UUID.fromString(dashed)
    }
}
