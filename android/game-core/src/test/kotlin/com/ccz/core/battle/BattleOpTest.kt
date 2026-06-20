package com.ccz.core.battle

import com.ccz.core.event.BattleOp
import com.ccz.core.event.ScenarioOp
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BattleOpTest {
    private val emptyCtx = ScriptContext()

    @Test
    fun spawnPlacesReserveTemplateWithFactionOverride() {
        val template = combatant("rein", Faction.ENEMY, Pos(0, 0))
        val state = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0)))
        val ctx = ScriptContext(reserves = mapOf("rein" to template))
        val result = BattleOps.applyOp(state, BattleOp.SpawnUnit("rein", Pos(4, 4), Faction.ALLY), ctx)

        assertEquals(listOf(Event.UnitSpawned("rein")), result.events)
        assertEquals(Pos(4, 4), result.state.unit("rein").pos)
        assertEquals(Faction.ALLY, result.state.unit("rein").faction)
    }

    @Test
    fun spawnWithoutTemplateIsNoOp() {
        val state = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0)))
        val result = BattleOps.applyOp(state, BattleOp.SpawnUnit("missing", Pos(1, 1)), emptyCtx)
        assertEquals(state, result.state)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun removeDeletesUnit() {
        val state = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0)), combatant("e", Faction.ENEMY, Pos(1, 0)))
        val result = BattleOps.applyOp(state, BattleOp.RemoveUnit("e"), emptyCtx)
        assertEquals(listOf(Event.UnitRemoved("e")), result.events)
        assertNull(result.state.units["e"])
    }

    @Test
    fun moveRepositionsUnitAndEmitsMoved() {
        val state = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0)))
        val result = BattleOps.applyOp(state, BattleOp.MoveUnit("h", Pos(3, 2)), emptyCtx)
        assertEquals(listOf(Event.Moved("h", Pos(0, 0), Pos(3, 2))), result.events)
        assertEquals(Pos(3, 2), result.state.unit("h").pos)
    }

    @Test
    fun setHpClampsToBoundsAndDiesAtZero() {
        val state = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0), hp = 50))
        val over = BattleOps.applyOp(state, BattleOp.SetHp("h", 9999), emptyCtx)
        assertEquals(100, over.state.unit("h").hp) // hpMax fixture = 100
        assertEquals(listOf(Event.HpSet("h", 100)), over.events)

        val zero = BattleOps.applyOp(state, BattleOp.SetHp("h", -5), emptyCtx)
        assertEquals(0, zero.state.unit("h").hp)
        assertEquals(listOf(Event.HpSet("h", 0), Event.Died("h")), zero.events)
    }

    @Test
    fun setStatusAddsStatus() {
        val state = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0)))
        val result = BattleOps.applyOp(state, BattleOp.SetStatus("h", "poison"), emptyCtx)
        assertEquals(listOf(Event.StatusApplied("h", "poison")), result.events)
        assertTrue("poison" in result.state.unit("h").statuses)
    }

    @Test
    fun giveItemEmitsEventWithoutStateChange() {
        val state = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0)))
        val result = BattleOps.applyOp(state, BattleOp.GiveItem("h", "sword"), emptyCtx)
        assertEquals(listOf(Event.ItemGranted("h", "sword")), result.events)
        assertEquals(state, result.state)
    }

    @Test
    fun forceWinEndsBattleOnceThenStaysSticky() {
        val state = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0)))
        val result = BattleOps.applyOps(state, listOf(BattleOp.ForceWin, BattleOp.ForceLose), emptyCtx)
        assertEquals(BattleOutcome.VICTORY, result.state.outcome)
        assertEquals(listOf(Event.BattleEnded(BattleOutcome.VICTORY)), result.events)
    }

    @Test
    fun forceLoseEndsBattleWithDefeat() {
        val state = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0)))
        val result = BattleOps.applyOp(state, BattleOp.ForceLose, emptyCtx)
        assertEquals(BattleOutcome.DEFEAT, result.state.outcome)
        assertEquals(listOf(Event.BattleEnded(BattleOutcome.DEFEAT)), result.events)
    }

    @Test
    fun opsOnAbsentUnitAreFailSafeNoOps() {
        val state = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0)))
        val ops = listOf(
            BattleOp.RemoveUnit("ghost"),
            BattleOp.MoveUnit("ghost", Pos(1, 1)),
            BattleOp.SetHp("ghost", 10),
            BattleOp.SetStatus("ghost", "poison"),
        )
        ops.forEach { op ->
            val result = BattleOps.applyOp(state, op, emptyCtx)
            assertEquals(state, result.state)
            assertTrue(result.events.isEmpty())
        }
    }

    @Test
    fun scriptSetVarMutatesStateOtherScenarioOpsAreEmittedOnly() {
        val state = stateOf(combatant("h", Faction.PLAYER, Pos(0, 0)))
        val setVar = BattleOps.applyOp(state, BattleOp.Script(ScenarioOp.SetVar("flag", 7)), emptyCtx)
        assertEquals(7, setVar.state.varValue("flag"))
        assertEquals(listOf(Event.VarSet("flag", 7)), setVar.events)

        val dialogue = ScenarioOp.Dialogue(com.ccz.core.event.DialogueLine(text = "hi"))
        val emitted = BattleOps.applyOp(state, BattleOp.Script(dialogue), emptyCtx)
        assertEquals(listOf(Event.Scenario(dialogue)), emitted.events)
        assertFalse(emitted.state.progress.vars.containsKey("flag"))
    }
}
