package com.ccz.modimport

import com.ccz.modimport.EexFixtures.eexBlob
import com.ccz.modimport.EexFixtures.strRec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Drives [LegacyObjectiveImporter] on synthetic EEX scripts: it imports a battle's win/lose objectives from the
 * real script and scopes them to the deployed roster (the 大兴山 reconciliation: the script's protect-邹靖 is
 * dropped because that ally is not in the curated demo roster, leaving exactly the hand-coded objectives).
 */
class LegacyObjectiveImporterTest {
    private val names = mapOf("刘备" to "hero_1", "邹靖" to "hero_182", "张角" to "hero_967")
    private fun nameToId(n: String): String? = names[n]

    /** A 0x19 CommonInfo objective block string. */
    private fun objectiveBlock(win: String, lose: List<String>): ByteArray {
        val text = "胜利条件\n★·$win\n\n失败条件\n" + lose.joinToString("\n") { "☆·$it" }
        return strRec(0x19, text)
    }

    @Test
    fun importsAnnihilateAndProtectScopedToDeployedRoster() {
        // 大兴山 final phase: win 全灭, lose 刘备死亡 + 邹靖死亡. Only 刘备 (hero_1) is deployed in the curated battle.
        val blob = eexBlob(objectiveBlock("全灭敌军。", listOf("刘备死亡。", "邹靖死亡。")))
        val imported = LegacyObjectiveImporter.importObjectives(blob, rosterIds = setOf("hero_1"), nameToId = ::nameToId)
        assertEquals(listOf(PackCondition(PackCondition.ANNIHILATE_ENEMIES)), imported.win)
        assertEquals(
            listOf(PackCondition(PackCondition.PROTECT_ALIVE, unit = "hero_1")),
            imported.lose,
            "protect-刘备 is kept (deployed); protect-邹靖 is dropped (not in this roster)",
        )
        assertEquals(listOf(PackCondition(PackCondition.PROTECT_ALIVE, unit = "hero_182")), imported.outOfRoster)
        assertTrue(imported.unsupported.isEmpty())
    }

    @Test
    fun picksTheAnnihilationPhaseAmongMultipleObjectiveBlocks() {
        // phase 1 is an area objective (unmapped), phase 2 is the annihilation fight — the importer chooses phase 2
        val blob = eexBlob(
            objectiveBlock("到达村庄。", listOf("村庄被占领。")),
            objectiveBlock("全灭敌军。", listOf("刘备死亡。")),
        )
        val imported = LegacyObjectiveImporter.importObjectives(blob, rosterIds = setOf("hero_1"), nameToId = ::nameToId)
        assertEquals(listOf(PackCondition(PackCondition.ANNIHILATE_ENEMIES)), imported.win)
        assertEquals(listOf(PackCondition(PackCondition.PROTECT_ALIVE, unit = "hero_1")), imported.lose)
        // the NON-selected phase-1 area objectives are still surfaced as unsupported (never silently dropped)
        assertEquals(2, imported.unsupported.size, "phase-1 reach-village/village-captured are recorded, not lost")
        assertTrue(imported.unsupported.any { "到达村庄" in it.clause } && imported.unsupported.any { "村庄被占领" in it.clause })
    }

    @Test
    fun keepsWinSideDefeatUnitWhenTheBossIsDeployed() {
        // a win-side "张角死亡" (kill the boss) is a defeat_unit, kept only when 张角 is in the deployed roster
        val withBoss = eexBlob(strRec(0x19, "胜利条件\n★·张角死亡。\n失败条件\n☆·刘备死亡。"))
        val imported = LegacyObjectiveImporter.importObjectives(withBoss, rosterIds = setOf("hero_1", "hero_967"), nameToId = ::nameToId)
        assertEquals(listOf(PackCondition(PackCondition.DEFEAT_UNIT, unit = "hero_967")), imported.win)
        assertEquals(listOf(PackCondition(PackCondition.PROTECT_ALIVE, unit = "hero_1")), imported.lose)
    }

    @Test
    fun failsClosedOnMalformedScript() {
        assertFailsWith<EexFormatException> {
            LegacyObjectiveImporter.importObjectives(ByteArray(16) { 0x7a }, emptySet(), ::nameToId)
        }
    }
}
