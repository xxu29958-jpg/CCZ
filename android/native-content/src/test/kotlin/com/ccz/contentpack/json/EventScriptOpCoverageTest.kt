package com.ccz.contentpack.json

import com.ccz.contentpack.ContentValidator
import com.ccz.core.event.BattleOp
import com.ccz.core.event.ScenarioOp
import com.ccz.core.event.TriggerCondition
import com.ccz.core.event.WinLoseCondition
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Exhaustive decode->map coverage: every BattleOp / TriggerCondition / WinLoseCondition variant is
 * exercised through the polymorphic decode boundary, with structural-equality assertions so a
 * field-swap or shape mismatch in any [EventMappers] when-branch is caught. Also covers faction
 * edge cases (invalid -> fail closed, omitted -> null) for the trigger path.
 */
class EventScriptOpCoverageTest {
    @Test
    fun allBattleOpsDecodeFaithfully() {
        val content = ContentJsonLoader.load(eventPack(ALL_BATTLE_OPS))
        assertTrue(ContentValidator.validate(content).isEmpty(), "refs must resolve")
        assertEquals(
            listOf(
                BattleOp.Script(ScenarioOp.FadeIn),
                BattleOp.SpawnUnit("zhaoyun", Pos(1, 2), Faction.ENEMY),
                BattleOp.RemoveUnit("zhaoyun"),
                BattleOp.MoveUnit("zhaoyun", Pos(3, 4)),
                BattleOp.SetHp("zhaoyun", 7),
                BattleOp.SetStatus("zhaoyun", "poison"),
                BattleOp.GiveItem("zhaoyun", "potion"),
                BattleOp.ForceWin,
                BattleOp.ForceLose,
            ),
            content.events.sScripts.single().pre,
        )
    }

    @Test
    fun allTriggerConditionsDecodeFaithfully() {
        val content = ContentJsonLoader.load(eventPack(ALL_TRIGGERS))
        assertTrue(ContentValidator.validate(content).isEmpty(), "refs must resolve")
        val mid = content.events.sScripts.single().mid
        assertTrue(mid.all { it.once }, "once defaults true when omitted")
        assertEquals(
            listOf(
                TriggerCondition.TurnStart(turn = 2, faction = Faction.ENEMY),
                TriggerCondition.UnitDead("zhaoyun"),
                TriggerCondition.UnitReach("zhaoyun", Pos(5, 6)),
                TriggerCondition.HpBelow("zhaoyun", 30),
                TriggerCondition.EnemyCountBelow(3),
                TriggerCondition.VarEquals("flag", 9),
            ),
            mid.map { it.whenCondition },
        )
    }

    @Test
    fun allWinLoseConditionsDecodeFaithfully() {
        val content = ContentJsonLoader.load(eventPack(ALL_WIN_LOSE))
        assertTrue(ContentValidator.validate(content).isEmpty(), "refs must resolve")
        val s = content.events.sScripts.single()
        assertEquals(
            listOf(
                WinLoseCondition.AnnihilateEnemies,
                WinLoseCondition.ReachTile("zhaoyun", Pos(7, 8)),
                WinLoseCondition.SurviveTurns(12),
                WinLoseCondition.DefeatUnit("zhaoyun"),
            ),
            s.win,
        )
        assertEquals(
            listOf(WinLoseCondition.UnitDead("zhaoyun"), WinLoseCondition.ProtectAlive("zhaoyun")),
            s.lose,
        )
    }

    @Test
    fun invalidFactionInTriggerConditionFailsClosed() {
        val events = """
            { "s_scripts": [ { "id": "s1", "mid": [
              { "id": "t", "when_condition": { "type": "turn_start", "turn": 1, "faction": "NEUTRAL" }, "actions": [] } ] } ] }
        """.trimIndent()
        assertFailsWith<ContentDecodeException> { ContentJsonLoader.load(eventPack(events)) }
    }

    @Test
    fun omittedFactionDecodesAsNull() {
        val events = """
            { "s_scripts": [ { "id": "s1",
              "pre": [ { "type": "spawn_unit", "unit": "zhaoyun", "at": { "x": 0, "y": 0 } } ],
              "mid": [ { "id": "t", "when_condition": { "type": "turn_start", "turn": 1 }, "actions": [] } ] } ] }
        """.trimIndent()
        val s = ContentJsonLoader.load(eventPack(events)).events.sScripts.single()
        assertEquals(BattleOp.SpawnUnit("zhaoyun", Pos(0, 0), null), s.pre.single())
        assertEquals(TriggerCondition.TurnStart(turn = 1, faction = null), s.mid.single().whenCondition)
    }

    private companion object {
        val ALL_BATTLE_OPS = """
            { "s_scripts": [ { "id": "s1", "pre": [
              { "type": "script", "op": { "type": "fade_in" } },
              { "type": "spawn_unit", "unit": "zhaoyun", "at": { "x": 1, "y": 2 }, "faction": "ENEMY" },
              { "type": "remove_unit", "unit": "zhaoyun" },
              { "type": "move_unit", "unit": "zhaoyun", "to": { "x": 3, "y": 4 } },
              { "type": "set_hp", "unit": "zhaoyun", "hp": 7 },
              { "type": "set_status", "unit": "zhaoyun", "status": "poison" },
              { "type": "give_item", "to": "zhaoyun", "item": "potion" },
              { "type": "force_win" },
              { "type": "force_lose" }
            ] } ] }
        """.trimIndent()

        val ALL_TRIGGERS = """
            { "s_scripts": [ { "id": "s1", "mid": [
              { "id": "c1", "when_condition": { "type": "turn_start", "turn": 2, "faction": "ENEMY" }, "actions": [] },
              { "id": "c2", "when_condition": { "type": "unit_dead", "unit": "zhaoyun" }, "actions": [] },
              { "id": "c3", "when_condition": { "type": "unit_reach", "unit": "zhaoyun", "pos": { "x": 5, "y": 6 } }, "actions": [] },
              { "id": "c4", "when_condition": { "type": "hp_below", "unit": "zhaoyun", "pct": 30 }, "actions": [] },
              { "id": "c5", "when_condition": { "type": "enemy_count_below", "count": 3 }, "actions": [] },
              { "id": "c6", "when_condition": { "type": "var_equals", "name": "flag", "value": 9 }, "actions": [] }
            ] } ] }
        """.trimIndent()

        val ALL_WIN_LOSE = """
            { "s_scripts": [ { "id": "s1",
              "win": [
                { "type": "annihilate_enemies" },
                { "type": "reach_tile", "unit": "zhaoyun", "pos": { "x": 7, "y": 8 } },
                { "type": "survive_turns", "turns": 12 },
                { "type": "defeat_unit", "unit": "zhaoyun" }
              ],
              "lose": [
                { "type": "unit_dead", "unit": "zhaoyun" },
                { "type": "protect_alive", "unit": "zhaoyun" }
              ] } ] }
        """.trimIndent()
    }
}
