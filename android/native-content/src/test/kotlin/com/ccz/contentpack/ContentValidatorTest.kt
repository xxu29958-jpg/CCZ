package com.ccz.contentpack

import com.ccz.core.event.RScript
import com.ccz.core.event.SScript
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
            tables = defaultTables().copy(units = listOf(unitDef(classId = "missing_class", skills = listOf("missing_skill")))),
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

        val issues = ContentValidator.validate(validContent(tables = defaultTables().copy(maps = listOf(badMap))))

        assertTrue(issues.any { it.path == "maps[0].tiles" })
        assertTrue(issues.any { it.path == "maps[0].tiles[0]" })
        assertTrue(issues.any { it.path == "maps[0].spawn_points.player[0]" })
    }

    @Test
    fun unknownEquipClassFailClosed() {
        val content = validContent(tables = defaultTables().copy(items = listOf(itemDef(equipClass = "ghost_class"))))

        assertTrue(ContentValidator.validate(content).any { it.path == "items[0].equip_class" })
    }

    @Test
    fun nullAndKnownEquipClassValidate() {
        val content = validContent(
            tables = defaultTables().copy(items = listOf(itemDef(id = "i1", equipClass = null), itemDef(id = "i2", equipClass = "cavalry"))),
        )

        assertTrue(ContentValidator.validate(content).none { it.path.endsWith(".equip_class") })
    }

    @Test
    fun unknownTerrainAffinityKeyFailClosed() {
        val content = validContent(tables = defaultTables().copy(classes = listOf(classDef(terrainAffinity = mapOf("lava" to 1)))))

        assertTrue(ContentValidator.validate(content).any { it.path == "classes[0].terrain_affinity" })
    }

    @Test
    fun knownTerrainAffinityKeyValidates() {
        val content = validContent(tables = defaultTables().copy(classes = listOf(classDef(terrainAffinity = mapOf("plain" to 2)))))

        assertEquals(emptyList(), ContentValidator.validate(content))
    }

    @Test
    fun duplicateSScriptIdFailClosed() {
        val content = validContent(events = EventTables(sScripts = listOf(emptySScript("dup"), emptySScript("dup"))))

        assertTrue(
            ContentValidator.validate(content)
                .any { it.path == "events.sScripts[1].id" && it.message.contains("duplicate id: dup") },
        )
    }

    @Test
    fun duplicateRScriptIdFailClosed() {
        val content = validContent(events = EventTables(rScripts = listOf(RScript("dup", emptyList()), RScript("dup", emptyList()))))

        assertTrue(
            ContentValidator.validate(content)
                .any { it.path == "events.rScripts[1].id" && it.message.contains("duplicate id: dup") },
        )
    }

    @Test
    fun blankScriptIdFailClosed() {
        val content = validContent(events = EventTables(sScripts = listOf(emptySScript(""))))

        assertTrue(ContentValidator.validate(content).any { it.path == "events.sScripts[0].id" && it.message.contains("id is blank") })
    }

    @Test
    fun unknownManifestEntryFailClosed() {
        val content = validContent(
            entry = "missing",
            events = EventTables(sScripts = listOf(emptySScript("entry"))),
        )

        assertTrue(
            ContentValidator.validate(content)
                .any { it.path == "manifest.entry" && it.message.contains("unknown entry script: missing") },
        )
    }

    @Test
    fun blankManifestEntryFailClosed() {
        val content = validContent(
            entry = "",
            events = EventTables(sScripts = listOf(emptySScript("entry"))),
        )

        assertTrue(
            ContentValidator.validate(content)
                .any { it.path == "manifest.entry" && it.message.contains("entry is blank") },
        )
    }

    @Test
    fun manifestEntryMayPointToRScript() {
        val content = validContent(entry = "intro", events = EventTables(rScripts = listOf(RScript("intro", emptyList()))))

        assertEquals(emptyList(), ContentValidator.validate(content))
    }

    @Test
    fun duplicatePortraitSubjectIdFailClosed() {
        val subjects = listOf(PortraitSubjectDef("cao_cao", "Cao Cao"), PortraitSubjectDef("cao_cao", "Cao Cao 2"))
        val content = validContent(events = EventTables(portraitSubjects = subjects))

        assertTrue(
            ContentValidator.validate(content)
                .any { it.path == "events.portrait_subjects[1].id" && it.message.contains("duplicate id: cao_cao") },
        )
    }

    @Test
    fun portraitSubjectIdCannotCollideWithUnitId() {
        val content = validContent(
            events = EventTables(portraitSubjects = listOf(PortraitSubjectDef("zhaoyun", "Zhao Yun"))),
        )

        assertTrue(
            ContentValidator.validate(content)
                .any {
                    it.path == "events.portrait_subjects[0].id" &&
                        it.message.contains("collides with unit id: zhaoyun")
                },
        )
    }

    @Test
    fun terrainMoveCostBelowOneFailClosed() {
        val zero = validContent(tables = defaultTables().copy(terrain = listOf(TerrainDef("plain", "Plain", moveCost = 0))))
        val negative = validContent(tables = defaultTables().copy(terrain = listOf(TerrainDef("plain", "Plain", moveCost = -2))))

        assertTrue(ContentValidator.validate(zero).any { it.path == "terrain[0].move_cost" })
        assertTrue(ContentValidator.validate(negative).any { it.path == "terrain[0].move_cost" })
    }

    @Test
    fun skillRangeInvertedOrNegativeFailClosed() {
        val inverted = validContent(tables = defaultTables().copy(skills = listOf(skillDef(min = 5, max = 1))))
        val negative = validContent(tables = defaultTables().copy(skills = listOf(skillDef(min = -1, max = 2))))

        assertTrue(ContentValidator.validate(inverted).any { it.path == "skills[0].range" })
        assertTrue(ContentValidator.validate(negative).any { it.path == "skills[0].range" })
    }

    @Test
    fun zeroMinSkillRangeValidates() {
        // min == 0 is a valid degenerate band (e.g. self/aura targeting) and must not be rejected.
        val content = validContent(tables = defaultTables().copy(skills = listOf(skillDef(min = 0, max = 2))))
        assertEquals(emptyList(), ContentValidator.validate(content))
    }

    // Override any table via defaultTables().copy(...) to keep the parameter list small
    // (CCZ rule: bundle test knobs into the ContentTables value object, not loose params).
    private fun validContent(
        tables: ContentTables = defaultTables(),
        events: EventTables? = null,
        entry: String = "entry",
    ): NativeContent =
        NativeContent(
            manifest = ContentManifest(
                nativeFormatVersion = ContentValidator.SUPPORTED_NATIVE_FORMAT_VERSION,
                contentId = "sample",
                contentVersion = "1.0.0",
                source = SourceInfo(mod = "sample_mod"),
                entry = entry,
            ),
            tables = tables,
            events = events ?: EventTables(sScripts = listOf(emptySScript(entry))),
        )

    private fun defaultTables(): ContentTables =
        ContentTables(
            classes = listOf(classDef()),
            units = listOf(unitDef()),
            terrain = listOf(TerrainDef("plain", "Plain", moveCost = 1)),
            skills = listOf(skillDef()),
            items = emptyList(),
            maps = listOf(mapDef()),
        )

    private fun emptySScript(id: String): SScript =
        SScript(id = id, win = emptyList(), lose = emptyList(), pre = emptyList(), mid = emptyList(), post = emptyList())

    private fun classDef(terrainAffinity: Map<String, Int> = emptyMap()): ClassDef =
        ClassDef(
            id = "cavalry",
            name = "Cavalry",
            movement = ClassMovement(moveType = "horse", move = 6),
            combat = ClassCombat(skills = listOf("atk"), terrainAffinity = terrainAffinity),
        )

    private fun itemDef(id: String = "sword", equipClass: String? = null): ItemDef =
        ItemDef(id = id, name = id, type = "weapon", equipClass = equipClass)

    private fun unitDef(
        classId: String = "cavalry",
        skills: List<String> = listOf("atk"),
    ): UnitDef =
        UnitDef(
            identity = UnitIdentity("zhaoyun", "Zhao Yun", classId, Faction.PLAYER),
            profile = UnitProfile(level = 1, hpMax = 200, stats = CombatStats(180, 120, 60, 90)),
            loadout = UnitLoadout(skills = skills),
        )

    private fun skillDef(min: Int = 1, max: Int = 1): SkillDef =
        SkillDef(
            id = "atk",
            name = "Attack",
            kind = DamageKind.PHYSICAL,
            powerCoeff = 100,
            use = SkillUse(range = RangeDef(min = min, max = max), area = "single", targeting = "enemy"),
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
