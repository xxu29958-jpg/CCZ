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
import com.ccz.core.model.Pos
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
    fun rScriptPortraitCanReferenceNonCombatPortraitSubject() {
        val content = contentWith(
            events = EventTables(
                rScripts = listOf(RScript(id = "r1", ops = listOf(ScenarioOp.Portrait("cao_cao")))),
                portraitSubjects = listOf(PortraitSubjectDef("cao_cao", "Cao Cao")),
            ),
            items = emptyList(),
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
    fun unknownPortraitSubjectFailClosed() {
        val content = rScriptContent(ScenarioOp.Portrait("ghost"))

        assertTrue(ContentValidator.validate(content).any { it.message.contains("unknown portrait subject: ghost") })
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

    @Test
    fun hpBelowPctOutOfRangeFailClosed() {
        val high = sScriptWith(mid = listOf(trig(TriggerCondition.HpBelow("zhaoyun", pct = 200))))
        val low = sScriptWith(mid = listOf(trig(TriggerCondition.HpBelow("zhaoyun", pct = -1))))

        assertTrue(ContentValidator.validate(high).any { it.message.contains("hp_below pct out of range: 200") })
        assertTrue(ContentValidator.validate(low).any { it.message.contains("hp_below pct out of range: -1") })
    }

    @Test
    fun hpBelowPctBoundariesValidate() {
        val zero = sScriptWith(mid = listOf(trig(TriggerCondition.HpBelow("zhaoyun", pct = 0))))
        val full = sScriptWith(mid = listOf(trig(TriggerCondition.HpBelow("zhaoyun", pct = 100))))

        assertEquals(emptyList(), ContentValidator.validate(zero))
        assertEquals(emptyList(), ContentValidator.validate(full))
    }

    @Test
    fun surviveTurnsBelowOneFailClosed() {
        val zero = sScriptWith(win = listOf(WinLoseCondition.SurviveTurns(0)))
        val negative = sScriptWith(win = listOf(WinLoseCondition.SurviveTurns(-3)))

        assertTrue(ContentValidator.validate(zero).any { it.message.contains("survive_turns must be >= 1: 0") })
        assertTrue(ContentValidator.validate(negative).any { it.message.contains("survive_turns must be >= 1: -3") })
    }

    @Test
    fun surviveTurnsAtLeastOneValidates() {
        assertEquals(emptyList(), ContentValidator.validate(sScriptWith(win = listOf(WinLoseCondition.SurviveTurns(1)))))
    }

    @Test
    fun negativeSpawnAndMoveCoordinatesFailClosed() {
        val spawn = sScriptWith(pre = listOf(BattleOp.SpawnUnit("zhaoyun", at = Pos(-1, 0))))
        val move = sScriptWith(pre = listOf(BattleOp.MoveUnit("zhaoyun", to = Pos(0, -2))))

        assertTrue(ContentValidator.validate(spawn).any { it.message.contains("negative coordinate: (-1, 0)") })
        assertTrue(ContentValidator.validate(move).any { it.message.contains("negative coordinate: (0, -2)") })
    }

    @Test
    fun negativeTriggerAndWinLoseCoordinatesFailClosed() {
        val reach = sScriptWith(mid = listOf(trig(TriggerCondition.UnitReach("zhaoyun", pos = Pos(-1, -1)))))
        val tile = sScriptWith(win = listOf(WinLoseCondition.ReachTile("zhaoyun", pos = Pos(-5, 0))))

        assertTrue(ContentValidator.validate(reach).any { it.message.contains("negative coordinate: (-1, -1)") })
        assertTrue(ContentValidator.validate(tile).any { it.message.contains("negative coordinate: (-5, 0)") })
    }

    @Test
    fun nonNegativeCoordinatesValidate() {
        assertEquals(emptyList(), ContentValidator.validate(sScriptWith(pre = listOf(BattleOp.SpawnUnit("zhaoyun", at = Pos(0, 0))))))
    }

    @Test
    fun deferredDeploymentReferencesValidate() {
        val content = sScriptWith(deferredDeployments = listOf(DeferredDeploymentDef("s1", "zhaoyun", Pos(2, 1), Faction.ENEMY)))

        assertEquals(emptyList(), ContentValidator.validate(content))
    }

    @Test
    fun invalidDeferredDeploymentFailsClosed() {
        val content = sScriptWith(
            deferredDeployments = listOf(DeferredDeploymentDef("s1", "ghost", Pos(-1, 0), source = "")),
        )

        val issues = ContentValidator.validate(content)

        assertTrue(issues.any { it.message.contains("unknown unit: ghost") })
        assertTrue(issues.any { it.message.contains("negative coordinate: (-1, 0)") })
        assertTrue(issues.any { it.message.contains("source is blank") })
    }

    @Test
    fun deferredDeploymentCannotAlsoSpawnInPre() {
        val content = sScriptWith(
            pre = listOf(BattleOp.SpawnUnit("zhaoyun", at = Pos(0, 0))),
            deferredDeployments = listOf(DeferredDeploymentDef("s1", "zhaoyun", Pos(2, 1))),
        )

        assertTrue(ContentValidator.validate(content).any { it.message.contains("deferred unit is already spawned in pre") })
    }

    @Test
    fun embeddedPortraitUnknownSubjectFailClosed() {
        val content = sScriptWith(pre = listOf(BattleOp.Script(ScenarioOp.Portrait("ghost"))))

        assertTrue(ContentValidator.validate(content).any { it.message.contains("unknown portrait subject: ghost") })
    }

    @Test
    fun embeddedBranchInSScriptFailClosed() {
        val content = sScriptWith(
            pre = listOf(BattleOp.Script(ScenarioOp.Branch(variable = "flag", equals = 1, target = "x"))),
        )

        assertTrue(ContentValidator.validate(content).any { it.message.contains("branch unsupported in s-script op") })
    }

    @Test
    fun embeddedChoiceInSScriptFailClosed() {
        val content = sScriptWith(
            pre = listOf(BattleOp.Script(ScenarioOp.Choice(prompt = "?", options = listOf(ChoiceOption(text = "a", goto = "x"))))),
        )

        assertTrue(ContentValidator.validate(content).any { it.message.contains("choice unsupported in s-script op") })
    }

    @Test
    fun embeddedPresentationAndKnownPortraitValidate() {
        val content = sScriptWith(
            pre = listOf(
                BattleOp.Script(ScenarioOp.SetVar("flag", 1)),
                BattleOp.Script(ScenarioOp.Portrait("zhaoyun")),
                BattleOp.Script(ScenarioOp.FadeIn),
            ),
        )

        assertEquals(emptyList(), ContentValidator.validate(content))
    }

    @Test
    fun embeddedPortraitSubjectValidate() {
        val content = sScriptWith(
            pre = listOf(BattleOp.Script(ScenarioOp.Portrait("cao_cao"))),
            portraitSubjects = listOf(PortraitSubjectDef("cao_cao", "Cao Cao")),
        )

        assertEquals(emptyList(), ContentValidator.validate(content))
    }

    @Test
    fun duplicateTriggerIdFailClosed() {
        val content = sScriptWith(
            mid = listOf(
                trig(TriggerCondition.TurnStart(1), id = "dup"),
                trig(TriggerCondition.TurnStart(2), id = "dup"),
            ),
        )

        assertTrue(ContentValidator.validate(content).any { it.message.contains("duplicate trigger id: dup") })
    }

    @Test
    fun uniqueTriggerIdsValidate() {
        val content = sScriptWith(
            mid = listOf(
                trig(TriggerCondition.TurnStart(1), id = "t1"),
                trig(TriggerCondition.TurnStart(2), id = "t2"),
            ),
        )

        assertEquals(emptyList(), ContentValidator.validate(content))
    }

    private fun trig(cond: TriggerCondition, id: String = "t1"): BattleTrigger =
        BattleTrigger(id = id, whenCondition = cond, actions = emptyList())

    private fun sScriptWith(
        win: List<WinLoseCondition> = emptyList(),
        pre: List<BattleOp> = emptyList(),
        mid: List<BattleTrigger> = emptyList(),
        deferredDeployments: List<DeferredDeploymentDef> = emptyList(),
        portraitSubjects: List<PortraitSubjectDef> = emptyList(),
    ): NativeContent =
        contentWith(
            events = EventTables(
                sScripts = listOf(
                    SScript(
                        id = "s1",
                        win = win,
                        lose = emptyList(),
                        pre = pre,
                        mid = mid,
                        post = emptyList(),
                    ),
                ),
                portraitSubjects = portraitSubjects,
                deferredDeployments = deferredDeployments,
            ),
            items = emptyList(),
        )

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

    private fun contentWith(
        events: EventTables,
        items: List<String>,
    ): NativeContent {
        val entry = events.sScripts.firstOrNull()?.id ?: events.rScripts.firstOrNull()?.id ?: "s1"
        return NativeContent(
            manifest = ContentManifest(
                nativeFormatVersion = ContentValidator.SUPPORTED_NATIVE_FORMAT_VERSION,
                contentId = "sample",
                contentVersion = "1.0.0",
                source = SourceInfo(mod = "sample_mod"),
                entry = entry,
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
}
