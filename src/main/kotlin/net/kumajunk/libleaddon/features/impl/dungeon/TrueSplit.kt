package net.kumajunk.libleaddon.features.impl.dungeon

import com.odtheking.odin.OdinMod.scope
import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.clickgui.settings.impl.NumberSetting
import com.odtheking.odin.events.ChatPacketEvent
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.LevelEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.events.core.onReceive
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.PersonalBest
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.render.textDim
import com.odtheking.odin.utils.skyblock.dungeon.DungeonListener
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import com.odtheking.odin.utils.toFixed
import kotlinx.coroutines.launch
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style

object TrueSplit : Module(
    name = "True Splits(LA)",
    description = "Advanced split timers for all dungeon floors."
) {
    private val pb = PersonalBest(this, "SplitsPB")

    private val isF7OrM7: Boolean
        get() = DungeonListener.floor?.floorNumber == 7

    private val inDungeons: Boolean
        get() = DungeonUtils.inDungeons

    private val mainHud by HUD("Main Splits", "Displays the main split timers.") { example ->
        if (!example && !inDungeons) return@HUD 0 to 0

        val floorNum = DungeonListener.floor?.floorNumber ?: 7
        val floorToUse = if (example) 7 else floorNum
        val activeLabels = getStageLabelsForFloor(floorToUse)

        val lines = if (example) {
            listOf(
                createHudText(0, "Blood Open", 1500L, 20L, 7),
                createHudText(1, "Watcher", 5000L, 100L, 7),
                createHudText(2, "Portal", 5000L, 100L, 7),
                createHudText(3, "Maxor", 5000L, 100L, 7),
                createHudText(4, "Storm", 5000L, 100L, 7),
                createHudText(5, "Terminals", 5000L, 100L, 7),
                createHudText(6, "Goldor", 5000L, 100L, 7),
                createHudText(7, "Necron", 5000L, 100L, 7),
                createHudText(8, "Dragons", 5000L, 100L, 7)
            )
        } else {
            buildList {
                val runSnapshot = currentRun.toList()

                runSnapshot.forEachIndexed { index, split ->
                    add(createHudText(index, split.name, split.time, split.tick, floorToUse))
                }

                if (currentStage > 0 && currentStage <= activeLabels.size) {
                    val idx = currentStage - 1
                    val label = activeLabels[idx].first
                    add(
                        createHudText(
                            idx,
                            label,
                            System.currentTimeMillis() - startTimestamp,
                            tickCounter - startTick,
                            floorToUse
                        )
                    )
                }
            }
        }

        if (lines.isEmpty()) return@HUD 0 to 0

        var yOffset = 0
        var maxWidth = 0

        lines.forEach { line ->
            if (line.isNotEmpty()) {
                val dim = textDim(line, 0, yOffset)
                maxWidth = maxOf(maxWidth, dim.first)
                yOffset += dim.second
            }
        }

        maxWidth to yOffset
    }

    private val breakdownHud by HUD("Breakdown Splits", "Displays the detailed breakdown splits.") { example ->
        if (!breakdownEnabled && !example) return@HUD 0 to 0
        if (!example && !isF7OrM7) return@HUD 0 to 0

        val lines = if (example) {
            listOf(
                "§7§n§lMAXOR BREAKDOWN",
                "§bCrystals > §a1.50s",
                "§bMaxor Kill > §a5.00s"
            )
        } else {
            val snapshot = currentBreakdownLines.toList()

            buildList {
                var idx = currentBreakdownScrollIndex
                repeat(scrollLength) {
                    if (idx < snapshot.size) {
                        add(snapshot[idx])
                        idx++
                    }
                }

                if ((currentStage in 4..8 || (currentStage == 9 && DungeonListener.floor?.isMM == true)) && lastBreakdownTime > 0) {
                    val diff = System.currentTimeMillis() - lastBreakdownTime
                    val label = getCurrentBreakdownLabel()
                    add("§b$label §6> §a${(diff / 1000f).toFixed(2)}s")
                }
            }
        }

        if (lines.isEmpty()) return@HUD 0 to 0

        var yOffset = 0
        var maxWidth = 0

        lines.forEach { line ->
            val dim = textDim(line, 0, yOffset)
            maxWidth = maxOf(maxWidth, dim.first)
            yOffset += dim.second
        }

        maxWidth to yOffset
    }

    private val breakdownEnabled: Boolean by BooleanSetting(
        "Breakdown",
        true,
        desc = "Toggle the breakdown HUD. (Floor 7 Only)"
    )
    private val scrollLength: Int by NumberSetting(
        "Breakdown Scroll Length",
        7,
        1,
        20,
        1,
        desc = "Number of lines to show in the breakdown HUD."
    )
    private val scrollDelay: Int by NumberSetting(
        "Breakdown Scroll Delay",
        5,
        0,
        20,
        1,
        unit = "seconds",
        desc = "Delay in seconds before scrolling to the next breakdown section."
    )

    private val bossEntry: Boolean by BooleanSetting(
        "Boss Entry Split",
        false,
        desc = "Show the Boss Entry split (Start -> Boss Portal)."
    )

    data class SplitEntry(val name: String, val time: Long, val tick: Long)

    private var currentRun = mutableListOf<SplitEntry>()
    private var currentBreakdownLines = mutableListOf<String>()

    // Internal state
    private var currentStage = 0
    private var startTimestamp = 0L
    private var dungeonStartTime = 0L
    private var startTick = 0L
    private var tickCounter = 0L
    private var currentBreakdownScrollIndex = 0
    private var lastScrollTime = 0L

    // Breakdown State
    private var lastBreakdownTime = 0L
    private var lastBreakdownTick = 0L
    private var passedMaxorCrystals = 0
    private var lastGoldorTerm = 0
    private var gateDestroyed = false
    private var termStage = 0
    private var relicCount = 0
    private var dragonCount = 0
    private var stormEnraged = false

    val regex = Regex("""\s*Team Score:.*""")

    init {
        on<LevelEvent.Load> {
            reset()
        }

        on<TickEvent.Server> {
            if (!inDungeons) return@on
            tickCounter++

            // Scroll logic (F7)
            if (isF7OrM7 && currentBreakdownLines.isNotEmpty() && currentBreakdownLines.size > scrollLength) {
                val now = System.currentTimeMillis()
                if (now - lastScrollTime > scrollDelay * 1000L) {
                    currentBreakdownScrollIndex = (currentBreakdownLines.size - scrollLength).coerceAtLeast(0)
                    lastScrollTime = now
                }
            }
        }

        on<ChatPacketEvent> {
            if (!inDungeons) return@on
            val msg = value.noControlCodes
            handleChat(msg)
        }

        onReceive<ClientboundSoundPacket> {
            if (!isF7OrM7 || currentStage != 9 || !breakdownEnabled) return@onReceive
            if (sound.value().location.path == "mob.wither.death" || sound.value().location.path == "entity.wither.death") {
                relicCount++
                if (relicCount == 5) {
                    addBreakdownEntry("Relics")
                }
            }
        }
    }

    private fun reset() {
        currentRun.clear()
        currentBreakdownLines.clear()
        currentStage = 0
        tickCounter = 0
        currentBreakdownScrollIndex = 0
        resetBreakdownState()
    }

    private fun resetBreakdownState() {
        lastBreakdownTime = System.currentTimeMillis()
        lastBreakdownTick = tickCounter
        passedMaxorCrystals = 0
        lastGoldorTerm = 0
        gateDestroyed = false
        termStage = 0
        relicCount = 0
        dragonCount = 0
        stormEnraged = false
    }

    private fun handleChat(msg: String) {
        val floorNum = DungeonListener.floor?.floorNumber ?: 7

        // Start check
        if (msg == "[NPC] Mort: Here, I found this map when I first entered the dungeon.") {
            reset()
            startTimestamp = System.currentTimeMillis()
            dungeonStartTime = startTimestamp
            startTick = tickCounter
            currentStage = 1 // Start waiting for Blood Open (Stage 1)
            return
        }
        if (regex.containsMatchIn(msg)) {
            if (isF7OrM7) {
                scope.launch {
                    Thread.sleep(500)
                    printBreakdown()
                }
            }
        }

        // F7 Breakdown Logic
        if (isF7OrM7) {
            handleBreakdown(msg)
        }

        // Main Splits Logic
        if (floorNum == 7) {
            val currentCriteria = MAINSPLITSTRINGS_F7.getOrNull(currentStage)
            if (currentStage == 1) {
                if (BLOODSTARTMESSAGES.any { msg == it }) advanceStage("Blood Open", floorNum)
            } else if (currentStage == 6 && msg == "The Core entrance is opening!") {
                if (termStage == 3) addBreakdownEntry("Section 4")
                advanceStage("Terminals", floorNum)
            } else if (currentCriteria != null && msg == currentCriteria) {
                val stageName = STAGE_LABELS_F7.getOrNull(currentStage - 1)?.first ?: "Unknown"
                advanceStage(stageName, floorNum)
                if (currentStage == 4 && bossEntry) recordBossEntry()
            }
        } else if (floorNum == 6) { // F6 Logic
            if (currentStage == 1) {
                if (BLOODSTARTMESSAGES.any { msg == it }) advanceStage("Blood Open", floorNum)
            } else if (currentStage == 2) {
                if (msg == "[BOSS] The Watcher: You have proven yourself. You may pass.") advanceStage("Watcher", floorNum)
            } else if (currentStage == 3) {
                if (msg == "[BOSS] Sadan: So you made it all the way here... Now you wish to defy me? Sadan?!") {
                    advanceStage("Portal", floorNum)
                }
            } else if (currentStage == 4) {
                if (msg == "[BOSS] Sadan: ENOUGH!") advanceStage("Terracottas", floorNum)
            } else if (currentStage == 5) {
                if (msg == "[BOSS] Sadan: You did it. I understand now, you have earned my respect.") {
                    advanceStage("Giants", floorNum)
                }
            } else if (currentStage == 6) {
                if (msg == "                             > EXTRA STATS <") advanceStage("Boss Kill", floorNum)
            }
        } else { // F1-F5 Logic
            if (currentStage == 1) {
                if (BLOODSTARTMESSAGES.any { msg == it }) advanceStage("Blood Open", floorNum)
            } else if (currentStage == 2) {
                if (msg == "[BOSS] The Watcher: You have proven yourself. You may pass.") advanceStage("Watcher", floorNum)
            } else if (currentStage == 3) {
                if (msg.startsWith("[BOSS] ") && !msg.startsWith("[BOSS] The Watcher:")) {
                    advanceStage("Portal", floorNum)
                }
            } else if (currentStage == 4) {
                if (msg == "                             > EXTRA STATS <") advanceStage("Boss Kill", floorNum)
            }
        }
    }

    private fun advanceStage(stageName: String, floorNum: Int) {
        val now = System.currentTimeMillis()
        val currentTick = tickCounter

        val timeDiff = now - startTimestamp
        val tickDiff = currentTick - startTick

        // Check PB only for Floor 7
        if (floorNum == 7) {
            if (stageName != "Dragons" || (DungeonListener.floor?.isMM == true)) {
                pb.time(stageName, timeDiff / 1000f, "s", "§b$stageName §6> §a", true)
            }
        }

        currentRun.add(SplitEntry(stageName, timeDiff, tickDiff))

        // Prepare for next stage
        startTimestamp = now
        startTick = currentTick

        currentStage++

        // Trigger breakdown header for new stage if F7
        if (floorNum == 7) {
            addBreakdownHeader(currentStage)
        }
    }

    private fun recordBossEntry() {
        val now = System.currentTimeMillis()
        val entryTime = now - dungeonStartTime
        val entryTick = tickCounter

        pb.time("Boss Entry", entryTime / 1000f, "s", "§bBoss Entry §6> §a", true)
        currentRun.add(SplitEntry("Boss Entry", entryTime, entryTick))
    }

    private fun addBreakdownHeader(stage: Int) {
        when (stage) {
            4 -> currentBreakdownLines.add("§7§n§lMAXOR BREAKDOWN")
            5 -> currentBreakdownLines.add("§7§n§lSTORM BREAKDOWN")
            6 -> currentBreakdownLines.add("§7§n§lGOLDOR BREAKDOWN")
            8 -> currentBreakdownLines.add("§7§n§lNECRON BREAKDOWN")
            9 -> if (DungeonListener.floor?.isMM == true)
                currentBreakdownLines.add("§7§n§lDRAGONS BREAKDOWN")
        }
        lastBreakdownTime = System.currentTimeMillis()
        lastBreakdownTick = tickCounter
        currentBreakdownScrollIndex = (currentBreakdownLines.size - scrollLength).coerceAtLeast(0)
    }

    private fun handleBreakdown(msg: String) {
        // Maxor
        if (msg == "The Energy Laser is charging up!") {
            passedMaxorCrystals++
            if (passedMaxorCrystals == 2) {
                addBreakdownEntry("Crystals")
            }
        } else if (msg == MAINSPLITSTRINGS_F7[4]) {
            if (passedMaxorCrystals >= 2) {
                addBreakdownEntry("Maxor Kill")
            }
        }

        // Storm
        if (msg == "⚠ Storm is enraged! ⚠") {
            stormEnraged = true
            addBreakdownEntry("First Pillar Kill")
        } else if (msg == MAINSPLITSTRINGS_F7[5]) {
            addBreakdownEntry("Storm Kill")
        }

        // Goldor
        if (msg == "The gate has been destroyed!") {
            gateDestroyed = true
            checkForwardTermStage()
        }
        val termMatch = Regex(".+ (?:activated|completed) a .+! \\((\\d+)/(\\d+)\\)").find(msg)

        if (termMatch != null) {
            val (currentStr, totalStr) = termMatch.destructured
            val current = currentStr.toInt()
            val total = totalStr.toInt()

            if (current > lastGoldorTerm && current == total) {
                lastGoldorTerm = current
                checkForwardTermStage()
            }
        }

        // Necron
        if (msg == MAINSPLITSTRINGS_F7[7]) { // Necron Start
            addBreakdownEntry("Goldor Kill")
        } else if (msg == MAINSPLITSTRINGS_F7[8]) { // Necron Kill
            addBreakdownEntry("Necron Kill")
        }

        // Dragons
        if (DRAGONKILLSTRINGS.any { msg == it }) {
            dragonCount++
            if (dragonCount > 5) return
            if (dragonCount > 2) {
                addBreakdownEntry(if (dragonCount == 3) "Third Dragon Kill" else if (dragonCount == 4) "Fourth Dragon Kill" else "Fifth Dragon Kill")
            }
        }
    }

    private fun checkForwardTermStage() {
        if (gateDestroyed && (lastGoldorTerm == 7 || lastGoldorTerm == 8)) {
            gateDestroyed = false
            lastGoldorTerm = 0
            termStage++

            val label = when (termStage) {
                1 -> "Section 1"
                2 -> "Section 2"
                3 -> "Section 3"
                else -> "Unknown Section"
            }
            addBreakdownEntry(label)
        }
    }

    private fun printBreakdown() {
        if (currentBreakdownLines.isEmpty()) return

        val message = Component.empty()
        message.append(Component.literal("§b-----------------------------------------------------§r\n"))
        message.append(Component.literal("§6§lTrue Splits Breakdown§r\n"))

        val sections = mutableMapOf<String, MutableList<String>>()
        var currentSection = "Global"

        currentBreakdownLines.forEach { line ->
            if (line.startsWith("§7§n§l")) {
                currentSection = line
                sections[currentSection] = mutableListOf()
            } else {
                sections.getOrPut(currentSection) { mutableListOf() }.add(line)
            }
        }

        sections.forEach { (header, lines) ->
            if (header == "Global" && lines.isEmpty()) return@forEach

            val cleanHeader = header.replace("§7§n§l", "").replace(" BREAKDOWN", "")
            val hoverContent = Component.empty()
            hoverContent.append(Component.literal("$header\n"))
            lines.forEach { l -> hoverContent.append(Component.literal("$l\n")) }

            val button = Component.literal(" §b[View §r$cleanHeader§b]§r ")
            button.style = Style.EMPTY.withHoverEvent(HoverEvent.ShowText(hoverContent))

            message.append(button)
        }
        message.append(Component.literal("\n§b-----------------------------------------------------§r"))

        net.kumajunk.libleaddon.utils.sendMessage(message)
    }

    private fun addBreakdownEntry(label: String) {
        val now = System.currentTimeMillis()
        val diff = now - lastBreakdownTime
        currentBreakdownLines.add("§b$label > §a${(diff / 1000f).toFixed(2)}s")
        lastBreakdownTime = now
        lastBreakdownTick = tickCounter
        currentBreakdownScrollIndex = (currentBreakdownLines.size - scrollLength).coerceAtLeast(0)
    }

    private fun getCurrentBreakdownLabel(): String {
        return when (currentStage) {
            4 -> if (passedMaxorCrystals < 2) "Crystals" else "Maxor Kill"
            5 -> if (!stormEnraged) "First Pillar" else "Storm Kill"
            6 -> when (termStage) {
                0 -> "Section 1"
                1 -> "Section 2"
                2 -> "Section 3"
                3 -> "Section 4"
                else -> "Terminals"
            }
            7 -> "Goldor Kill"
            8 -> "Necron Kill"
            9 -> "Dragons"
            else -> "Split"
        }
    }

    private fun getStageLabelsForFloor(floor: Int): List<Pair<String, String>> {
        return when (floor) {
            7 -> STAGE_LABELS_F7
            6 -> STAGE_LABELS_F6
            else -> STAGE_LABELS_GENERAL
        }
    }

    private fun createHudText(stage: Int, label: String, realTime: Long, tickTime: Long, floorNum: Int): String {
        // Hide Dragons split if not in Master Mode F7
        if (label == "Dragons") {
            val floor = DungeonListener.floor
            if (floor == null || floorNum != 7 || !floor.isMM) return ""
        }

        val activeLabels = getStageLabelsForFloor(floorNum)
        val colorConstraint = activeLabels.find { it.first == label }
        val baseColor = colorConstraint?.second ?: if (label == "Boss Entry") "§b" else "§f"
        val activeMarker = if (stage == currentStage - 1) "§n" else ""

        val timeColor = "§a"

        return "§b$activeMarker$baseColor$label§b §6> $timeColor${(realTime / 1000f).toFixed(2)}s $timeColor(${
            (tickTime / 20f).toFixed(
                2
            )
        }s)"
    }

    // Constants
    private val BLOODSTARTMESSAGES = listOf(
        "[BOSS] The Watcher: Congratulations, you made it through the Entrance.",
        "[BOSS] The Watcher: Ah, you've finally arrived.",
        "[BOSS] The Watcher: Ah, we meet again...",
        "[BOSS] The Watcher: So you made it this far... interesting.",
        "[BOSS] The Watcher: You've managed to scratch and claw your way here, eh?",
        "[BOSS] The Watcher: I'm starting to get tired of seeing you around here...",
        "[BOSS] The Watcher: Oh.. hello?",
        "[BOSS] The Watcher: Things feel a little more roomy now, eh?"
    )

    private val MAINSPLITSTRINGS_F7 = listOf(
        "[NPC] Mort: Here, I found this map when I first entered the dungeon.", // 0 (Start)
        "BLOOD_START_PLACEHOLDER", // 1 (Blood Open)
        "[BOSS] The Watcher: You have proven yourself. You may pass.", // 2 (Watcher)
        "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!", // 3 (Portal Entry / Maxor Start)
        "[BOSS] Storm: Pathetic Maxor, just like expected.", // 4 (Storm Start)
        "[BOSS] Goldor: Who dares trespass into my domain?", // 5 (Goldor Start)
        "The Core entrance is opening!", // 6 (Terminals Done) 
        "[BOSS] Necron: You went further than any human before, congratulations.", // 7 (Necron Start)
        "[BOSS] Necron: All this, for nothing...", // 8 (Necron Kill)
        "                             > EXTRA STATS <" // 9 (End)
    )

    val DRAGONKILLSTRINGS = listOf(
        "[BOSS] Wither King: Oh, this one hurts!",
        "[BOSS] Wither King: I have more of those.",
        "[BOSS] Wither King: My soul is disposable."
    )

    private val STAGE_LABELS_F7 = listOf(
        "Blood Open" to "§2",
        "Watcher" to "§b",
        "Portal" to "§d",
        "Maxor" to "§5",
        "Storm" to "§3",
        "Terminals" to "§6",
        "Goldor" to "§6",
        "Necron" to "§c",
        "Dragons" to "§4"
    )

    private val STAGE_LABELS_F6 = listOf(
        "Blood Open" to "§2",
        "Watcher" to "§b",
        "Portal" to "§d",
        "Terracottas" to "§c",
        "Giants" to "§4",
        "Boss Kill" to "§5"
    )

    private val STAGE_LABELS_GENERAL = listOf(
        "Blood Open" to "§2",
        "Watcher" to "§b",
        "Portal" to "§d",
        "Boss Kill" to "§c"
    )
}
