package com.ccz.contentpack.assembly

import com.ccz.contentpack.UnitDef
import com.ccz.contentpack.UnitIdentity
import com.ccz.contentpack.UnitProfile
import com.ccz.core.battle.BattleState
import com.ccz.core.battle.Event
import com.ccz.core.battle.TriggerRunner
import com.ccz.core.event.BattleOp
import com.ccz.core.event.SScript
import com.ccz.core.model.AccuracyRates
import com.ccz.core.model.BurstRates
import com.ccz.core.model.CombatRates
import com.ccz.core.model.CombatStats
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BattleAssemblerTest {
    // Non-default panel/rates so a "rates = CombatRates()" mutation in the mapper would be caught
    // (UnitProfile.rates also defaults to CombatRates(), which would otherwise hide a dropped field).
    private val sampleStats = CombatStats(atk = 180, def = 120, mat = 60, res = 90)
    private val sampleRates = CombatRates(accuracy = AccuracyRates(hit = 90, evade = 5), burst = BurstRates(crit = 7))

    private fun unitDef(
        id: String,
        faction: Faction = Faction.ENEMY,
        hpMax: Int = 200,
    ): UnitDef = UnitDef(
        identity = UnitIdentity(id = id, name = "Name-$id", classId = "cavalry", faction = faction),
        profile = UnitProfile(level = 3, hpMax = hpMax, stats = sampleStats, rates = sampleRates),
    )

    @Test
    fun reservesMapUnitDefByIdAtFullHpWithPanel() {
        val reserves = BattleAssembler.reserves(listOf(unitDef("zhaoyun", Faction.PLAYER, hpMax = 200)))

        val combatant = reserves.getValue("zhaoyun")
        assertEquals("zhaoyun", combatant.id)
        assertEquals("Name-zhaoyun", combatant.name)
        assertEquals("cavalry", combatant.classId)
        assertEquals(Faction.PLAYER, combatant.faction)
        assertEquals(200, combatant.hpMax)
        assertEquals(200, combatant.hp) // reserves enter at full hp
        assertEquals(sampleStats, combatant.stats)
        assertEquals(sampleRates, combatant.rates) // non-default rates pinned (kills pass-through mutation)
        assertEquals(Pos(-1, -1), combatant.pos) // off-map sentinel before any spawn places it
    }

    @Test
    fun emptyUnitsYieldEmptyReserves() {
        assertTrue(BattleAssembler.reserves(emptyList()).isEmpty())
    }

    @Test
    fun reservesAssembleMultipleUnitsByDistinctKeys() {
        val reserves = BattleAssembler.reserves(listOf(unitDef("a"), unitDef("b", Faction.PLAYER)))

        assertEquals(setOf("a", "b"), reserves.keys)
        assertEquals(Faction.ENEMY, reserves.getValue("a").faction)
        assertEquals(Faction.PLAYER, reserves.getValue("b").faction)
    }

    @Test
    fun assembledReservePlacedBySpawnOpThroughTriggerRunner() {
        val ctx = BattleAssembler.scriptContext(listOf(unitDef("rein", Faction.ENEMY, hpMax = 150)))
        val script = SScript(
            id = "s",
            win = emptyList(),
            lose = emptyList(),
            pre = listOf(BattleOp.SpawnUnit("rein", Pos(4, 4))),
            mid = emptyList(),
            post = emptyList(),
        )
        val state = BattleState(units = emptyMap(), turn = 1, active = Faction.PLAYER, rngState = 0)

        val result = TriggerRunner.applyPre(state, script, ctx)

        assertEquals(listOf(Event.UnitSpawned("rein")), result.events)
        val spawned = result.state.units.getValue("rein")
        assertEquals(Pos(4, 4), spawned.pos) // sentinel pos overwritten by op.at
        assertEquals(Faction.ENEMY, spawned.faction) // faction carried from UnitDef
        assertEquals(150, spawned.hp) // full hp from assembled reserve
        assertEquals(sampleStats, spawned.stats) // combat panel survives assembly + spawn
        assertEquals(sampleRates, spawned.rates) // rates survive assembly + spawn
    }
}
