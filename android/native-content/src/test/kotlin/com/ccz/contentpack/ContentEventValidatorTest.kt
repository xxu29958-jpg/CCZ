package com.ccz.contentpack

import com.ccz.core.event.BattleOp
import com.ccz.core.event.BattleTrigger
import com.ccz.core.event.ChoiceOption
import com.ccz.core.event.RScript
import com.ccz.core.event.SScript
import com.ccz.core.event.ScenarioOp
import com.ccz.core.event.TriggerCondition
import com.ccz.core.event.WinLoseCondition
import com.ccz.core.model.CombatStats
import com.ccz.core.model.Faction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentEventValidatorTest {
    @Test
    fun eventReferencesToKnownUnitsAndItemsValidate() {
        val content = contentWith(
            events = EventTables(sScripts = listOf(scriptReferencing("zhaoyun", "potion"))),
            items = listOf("potion"),
        )

        assertEquals(emptyList(), ContentValidator.validate(content))
    }

    @Test
    fun unknownUnitAndItemReferencesFailClosed() {
        val content = contentWith(
            events = EventTables(sScripts = listOf(scriptReferencing("ghost", "elixir"))),
            items = listOf("potion"),
        )

        val issues = ContentValidator.validate(content)

        assertTrue(issues.any { it.message.contains("unknown unit: ghost") })
        assertTrue(issues.any { it.message.contains("unknown item: elixir") })
    }

    @Test
    fun validRScriptReferencesValidate() {
        val content = rScriptContent(
            ScenarioOp.Portrait("zhaoyun"),
            ScenarioOp.Branch(variable = "flag", equals = 1, target = "end"),
            ScenarioOp.Choice(prompt = "?", options = listOf(ChoiceOption(text = "go", goto = "end"))),
            ScenarioOp.Label("end"),
        )

        assertEquals(emptyList(), ContentValidator.validate(content))
    }

    @Test
    fun unknownBranchTargetFailClosed() {
        val content = rScriptContent(
            ScenarioOp.Branch(variable = "flag", equals = 1, target = "missing"),
            ScenarioOp.Label("end"),
        )

        assertTrue(ContentValidator.validate(content).any { it.message.contains("unknown label: missing") })
    }

    @Test
    fun unknownChoiceGotoFailClosed() {
        val content = rScriptContent(
            ScenarioOp.Choice(prompt = "?", options = listOf(ChoiceOption(text = "x", goto = "nowhere"))),
            ScenarioOp.Label("end"),
        )

        assertTrue(ContentValidator.validate(content).any { it.message.contains("unknown label: nowhere") })
    }

    @Test
    fun unknownPortraitUnitFailClosed() {
        val content = rScriptContent(ScenarioOp.Portrait("ghost"))

        assertTrue(ContentValidator.validate(content).any { it.message.contains("unknown unit: ghost") })
    }

    @Test
    fun duplicateLabelFailClosed() {
        val content = rScriptContent(ScenarioOp.Label("dup"), ScenarioOp.Label("dup"))

        assertTrue(ContentValidator.validate(content).any { it.message.contains("duplicate label: dup") })
    }

    @Test
    fun choiceGotoNullContinuesInOrderAndValidates() {
        val content = rScriptContent(
            ScenarioOp.Choice(prompt = "?", options = listOf(ChoiceOption(text = "stay", goto = null))),
        )

        assertEquals(emptyList(), ContentValidator.validate(content))
    }

    private fun rScriptContent(vararg ops: ScenarioOp): NativeContent =
        contentWith(events = EventTables(rScripts = listOf(RScript(id = "r1", ops = ops.toList()))), items = emptyList())

    private fun scriptReferencing(unitId: String, itemId: String): SScript =
        SScript(
            id = "s1",
            win = listOf(WinLoseCondition.DefeatUnit(unitId)),
            lose = listOf(WinLoseCondition.UnitDead(unitId)),
            pre = listOf(BattleOp.GiveItem(to = unitId, item = itemId)),
            mid = listOf(
                BattleTrigger(
                    id = "t1",
                    whenCondition = TriggerCondition.UnitDead(unitId),
                    actions = listOf(BattleOp.RemoveUnit(unitId)),
                ),
            ),
            post = emptyList(),
        )

    private fun contentWith(events: EventTables, items: List<String>): NativeContent =
        NativeContent(
            manifest = ContentManifest(
                nativeFormatVersion = ContentValidator.SUPPORTED_NATIVE_FORMAT_VERSION,
                contentId = "sample",
                contentVersion = "1.0.0",
                source = SourceInfo(mod = "sample_mod"),
                entry = "s1",
            ),
            tables = ContentTables(
                classes = listOf(ClassDef("cavalry", "Cavalry", ClassMovement("horse", 6))),
                units = listOf(
                    UnitDef(
                        identity = UnitIdentity("zhaoyun", "Zhao Yun", "cavalry", Faction.PLAYER),
                        profile = UnitProfile(level = 1, hpMax = 200, stats = CombatStats(180, 120, 60, 90)),
                    ),
                ),
                terrain = listOf(TerrainDef("plain", "Plain", moveCost = 1)),
                skills = emptyList(),
                items = items.map { ItemDef(id = it, name = it, type = "consumable") },
                maps = emptyList(),
            ),
            events = events,
        )
}
