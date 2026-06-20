package com.ccz.contentpack

import com.ccz.core.event.BattleOp
import com.ccz.core.event.BattleTrigger
import com.ccz.core.event.SScript
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
