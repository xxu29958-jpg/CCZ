package com.ccz.contentpack

import com.ccz.core.model.CombatStats
import com.ccz.core.model.DamageKind
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentValidatorTest {
    @Test
    fun validNativeContentHasNoIssues() {
        assertEquals(emptyList(), ContentValidator.validate(validContent()))
    }

    @Test
    fun unknownReferencesFailClosed() {
        val content = validContent(
            unit = unitDef(classId = "missing_class", skills = listOf("missing_skill")),
        )

        val issues = ContentValidator.validate(content)

        assertTrue(issues.any { it.path == "units[0].class" })
        assertTrue(issues.any { it.path == "units[0].skills" })
    }

    @Test
    fun invalidMapShapeAndSpawnBoundsAreReported() {
        val badMap = MapDef(
            id = "map_1",
            size = MapSize(width = 2, height = 2),
            tileset = "plain",
            tiles = listOf(listOf("plain")),
            spawnPoints = mapOf("player" to listOf(Pos(2, 0))),
        )

        val issues = ContentValidator.validate(validContent(map = badMap))

        assertTrue(issues.any { it.path == "maps[0].tiles" })
        assertTrue(issues.any { it.path == "maps[0].tiles[0]" })
        assertTrue(issues.any { it.path == "maps[0].spawn_points.player[0]" })
    }

    private fun validContent(
        unit: UnitDef = unitDef(),
        map: MapDef = mapDef(),
    ): NativeContent =
        NativeContent(
            manifest = ContentManifest(
                nativeFormatVersion = ContentValidator.SUPPORTED_NATIVE_FORMAT_VERSION,
                contentId = "sample",
                contentVersion = "1.0.0",
                source = SourceInfo(mod = "sample_mod"),
                entry = "map_1",
            ),
            tables = ContentTables(
                classes = listOf(classDef()),
                units = listOf(unit),
                terrain = listOf(TerrainDef("plain", "Plain", moveCost = 1)),
                skills = listOf(skillDef()),
                items = emptyList(),
                maps = listOf(map),
            ),
        )

    private fun classDef(): ClassDef =
        ClassDef(
            id = "cavalry",
            name = "Cavalry",
            movement = ClassMovement(moveType = "horse", move = 6),
            combat = ClassCombat(skills = listOf("atk")),
        )

    private fun unitDef(
        classId: String = "cavalry",
        skills: List<String> = listOf("atk"),
    ): UnitDef =
        UnitDef(
            identity = UnitIdentity("zhaoyun", "Zhao Yun", classId, Faction.PLAYER),
            profile = UnitProfile(level = 1, hpMax = 200, stats = CombatStats(180, 120, 60, 90)),
            loadout = UnitLoadout(skills = skills),
        )

    private fun skillDef(): SkillDef =
        SkillDef(
            id = "atk",
            name = "Attack",
            kind = DamageKind.PHYSICAL,
            powerCoeff = 100,
            use = SkillUse(range = RangeDef(min = 1, max = 1), area = "single", targeting = "enemy"),
        )

    private fun mapDef(): MapDef =
        MapDef(
            id = "map_1",
            size = MapSize(width = 2, height = 2),
            tileset = "plain",
            tiles = listOf(listOf("plain", "plain"), listOf("plain", "plain")),
            spawnPoints = mapOf("player" to listOf(Pos(0, 0))),
        )
}
