package com.ccz.contentpack.json

import com.ccz.contentpack.ContentValidator
import com.ccz.core.event.BattleOp
import com.ccz.core.event.ChoiceOption
import com.ccz.core.event.DialogueLine
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
 * Decode boundary for native event scripts: ops are decoded polymorphically by the
 * "type" class-discriminator (the op-string whitelist) and unknown ops / enum strings
 * fail closed. These tests assert faithful shape mapping (guarding against field-swap)
 * and the fail-closed contract; reference integrity is covered by ContentValidator.
 */
class EventScriptJsonTest {
    @Test
    fun rScriptDecodesFaithfully() {
        val content = ContentJsonLoader.load(eventPack(R_EVENTS))
        assertTrue(ContentValidator.validate(content).isEmpty())

        val r = content.events.rScripts.single()
        assertEquals("r1", r.id)
        val ops = r.ops
        assertEquals(11, ops.size)
        assertEquals(ScenarioOp.Dialogue(DialogueLine(speaker = "zhaoyun", text = "charge")), ops[0])
        assertEquals(ScenarioOp.Portrait(unit = "zhaoyun", emotion = "angry"), ops[1])
        assertEquals(
            ScenarioOp.Choice("pick", listOf(ChoiceOption(text = "yes", goto = "lbl", setVars = mapOf("morale" to 1)))),
            ops[2],
        )
        assertEquals(ScenarioOp.SetVar("morale", 2), ops[3])
        assertEquals(ScenarioOp.Branch(variable = "morale", equals = 2, target = "lbl"), ops[4])
        assertEquals(ScenarioOp.Label("lbl"), ops[5])
        assertEquals(ScenarioOp.Wait(3), ops[6])
        assertEquals(ScenarioOp.SceneTransition("scn2"), ops[7])
        assertEquals(ScenarioOp.PlayBgm("bgm1"), ops[8])
        assertEquals(ScenarioOp.FadeIn, ops[9])
        assertEquals(ScenarioOp.FadeOut, ops[10])
    }

    @Test
    fun sScriptDecodesFaithfully() {
        val content = ContentJsonLoader.load(eventPack(S_EVENTS))
        assertTrue(ContentValidator.validate(content).isEmpty())

        val s = content.events.sScripts.single()
        assertEquals("s1", s.id)
        assertEquals(listOf(WinLoseCondition.AnnihilateEnemies, WinLoseCondition.ReachTile("zhaoyun", Pos(3, 4))), s.win)
        assertEquals(listOf(WinLoseCondition.UnitDead("zhaoyun")), s.lose)
        assertEquals(listOf(BattleOp.SpawnUnit("zhaoyun", Pos(1, 2), Faction.ENEMY)), s.pre)
        assertEquals(listOf(BattleOp.SetHp("zhaoyun", 10)), s.post)

        val trigger = s.mid.single()
        assertEquals("t1", trigger.id)
        assertEquals(false, trigger.once)
        assertEquals(TriggerCondition.TurnStart(turn = 5, faction = Faction.PLAYER), trigger.whenCondition)
        assertEquals(
            listOf(BattleOp.Script(ScenarioOp.Dialogue(DialogueLine(text = "ambush"))), BattleOp.ForceWin),
            trigger.actions,
        )
    }

    @Test
    fun unknownOpStringFailsClosed() {
        val events = """{ "r_scripts": [ { "id": "r1", "ops": [ { "type": "teleport_everyone" } ] } ] }"""
        assertFailsWith<ContentDecodeException> { ContentJsonLoader.load(eventPack(events)) }
    }

    @Test
    fun unknownFactionInBattleOpFailsClosed() {
        val events = """
            { "s_scripts": [ { "id": "s1",
              "pre": [ { "type": "spawn_unit", "unit": "zhaoyun", "at": { "x": 0, "y": 0 }, "faction": "NEUTRAL" } ] } ] }
        """.trimIndent()
        assertFailsWith<ContentDecodeException> { ContentJsonLoader.load(eventPack(events)) }
    }

    @Test
    fun missingOpDiscriminatorFailsClosed() {
        val events = """{ "r_scripts": [ { "id": "r1", "ops": [ { "line": { "text": "x" } } ] } ] }"""
        assertFailsWith<ContentDecodeException> { ContentJsonLoader.load(eventPack(events)) }
    }

    @Test
    fun unknownKeyInsideOpFailsClosed() {
        val events = """{ "r_scripts": [ { "id": "r1", "ops": [ { "type": "wait", "ticks": 3, "bogus": 1 } ] } ] }"""
        assertFailsWith<ContentDecodeException> { ContentJsonLoader.load(eventPack(events)) }
    }

    @Test
    fun unknownUnitReferenceInEventFailsValidation() {
        val events = """
            { "s_scripts": [ { "id": "s1",
              "pre": [ { "type": "spawn_unit", "unit": "ghost", "at": { "x": 0, "y": 0 }, "faction": "ENEMY" } ] } ] }
        """.trimIndent()
        val content = ContentJsonLoader.load(eventPack(events)) // decode succeeds: shape + enums are valid
        val issues = ContentValidator.validate(content)
        assertTrue(issues.any { it.message.contains("unknown unit: ghost") }, "got: $issues")
    }

    private companion object {
        val R_EVENTS = """
            {
              "r_scripts": [
                {
                  "id": "r1",
                  "ops": [
                    { "type": "dialogue", "line": { "speaker": "zhaoyun", "text": "charge" } },
                    { "type": "portrait", "unit": "zhaoyun", "emotion": "angry" },
                    { "type": "choice", "prompt": "pick",
                      "options": [ { "text": "yes", "goto": "lbl", "set_vars": { "morale": 1 } } ] },
                    { "type": "set_var", "name": "morale", "value": 2 },
                    { "type": "branch", "variable": "morale", "equals": 2, "target": "lbl" },
                    { "type": "label", "name": "lbl" },
                    { "type": "wait", "ticks": 3 },
                    { "type": "scene_transition", "target": "scn2" },
                    { "type": "play_bgm", "id": "bgm1" },
                    { "type": "fade_in" },
                    { "type": "fade_out" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val S_EVENTS = """
            {
              "s_scripts": [
                {
                  "id": "s1",
                  "win": [ { "type": "annihilate_enemies" },
                           { "type": "reach_tile", "unit": "zhaoyun", "pos": { "x": 3, "y": 4 } } ],
                  "lose": [ { "type": "unit_dead", "unit": "zhaoyun" } ],
                  "pre": [ { "type": "spawn_unit", "unit": "zhaoyun", "at": { "x": 1, "y": 2 }, "faction": "ENEMY" } ],
                  "mid": [
                    {
                      "id": "t1",
                      "when_condition": { "type": "turn_start", "turn": 5, "faction": "PLAYER" },
                      "once": false,
                      "actions": [
                        { "type": "script", "op": { "type": "dialogue", "line": { "text": "ambush" } } },
                        { "type": "force_win" }
                      ]
                    }
                  ],
                  "post": [ { "type": "set_hp", "unit": "zhaoyun", "hp": 10 } ]
                }
              ]
            }
        """.trimIndent()
    }
}
