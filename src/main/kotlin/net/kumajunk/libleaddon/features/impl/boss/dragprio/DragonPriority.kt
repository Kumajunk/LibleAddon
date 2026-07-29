package net.kumajunk.libleaddon.features.impl.boss.dragprio

import com.odtheking.odin.utils.equalsOneOf
import com.odtheking.odin.utils.modMessage
import com.odtheking.odin.utils.skyblock.dungeon.Blessing
import com.odtheking.odin.utils.skyblock.dungeon.DungeonClass
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import net.kumajunk.libleaddon.features.impl.boss.DragPrio.dragonPriorityToggle
import net.kumajunk.libleaddon.features.impl.boss.DragPrio.easyPower
import net.kumajunk.libleaddon.features.impl.boss.DragPrio.normalPower
import net.kumajunk.libleaddon.features.impl.boss.DragPrio.paulBuff
import net.kumajunk.libleaddon.features.impl.boss.DragPrio.soloDebuff
import net.kumajunk.libleaddon.features.impl.boss.DragPrio.soloDebuffOnAll

/*
 * code from odinFabric (https://github.com/odtheking/OdinFabric)
 */
object DragonPriority {

    private val defaultOrder = listOf(WitherDragonsEnum.Red, WitherDragonsEnum.Orange, WitherDragonsEnum.Blue, WitherDragonsEnum.Purple, WitherDragonsEnum.Green)
    private val dragonList = listOf(WitherDragonsEnum.Orange, WitherDragonsEnum.Green, WitherDragonsEnum.Red, WitherDragonsEnum.Blue, WitherDragonsEnum.Purple)

    fun findPriority(spawningDragons: MutableList<WitherDragonsEnum>): WitherDragonsEnum =
        if (!dragonPriorityToggle) spawningDragons.minBy { defaultOrder.indexOf(it) }
        else sortPriority(spawningDragons)

    private fun sortPriority(spawningDragons: MutableList<WitherDragonsEnum>): WitherDragonsEnum {
        val totalPower = Blessing.POWER.current * (if (paulBuff) 1.25 else 1.0) + (if (Blessing.TIME.current > 0) 2.5 else 0.0)
        val playerClass = DungeonUtils.currentDungeonPlayer.clazz.apply { if (this == DungeonClass.EMPTY) modMessage("§cFailed to get dungeon class.") }

        val priorityList =
            if (totalPower >= normalPower || (spawningDragons.any { it == WitherDragonsEnum.Purple } && totalPower >= easyPower))
                if (playerClass.equalsOneOf(DungeonClass.BERSERK, DungeonClass.MAGE)) dragonList else dragonList.reversed()
            else defaultOrder

        spawningDragons.sortBy { priorityList.indexOf(it) }

        if (totalPower >= easyPower) {
            if (soloDebuff == 1 && playerClass == DungeonClass.TANK && (spawningDragons.any { it == WitherDragonsEnum.Purple } || soloDebuffOnAll))
                spawningDragons.sortByDescending { priorityList.indexOf(it) }
            else if (playerClass == DungeonClass.HEALER && (spawningDragons.any { it == WitherDragonsEnum.Purple } || soloDebuffOnAll))
                spawningDragons.sortByDescending { priorityList.indexOf(it) }
        }

        return spawningDragons[0]
    }
}

