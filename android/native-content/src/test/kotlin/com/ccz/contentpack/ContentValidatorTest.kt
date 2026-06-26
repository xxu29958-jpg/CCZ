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

    @Test
    fun negativeUnitGradeFailClosed() {
        // grade is a non-negative quality-tier index; a negative tier is malformed content. A too-high
        // tier is NOT an error (the assembler saturates it at the top tier), so only the lower bound gates.
        val negative = validContent(tables = defaultTables().copy(units = listOf(unitDef(profile = profile(grade = -1)))))
        assertTrue(ContentValidator.validate(negative).any { it.path == "units[0].grade" })

        val high = validContent(tables = defaultTables().copy(units = listOf(unitDef(profile = profile(grade = 99)))))
        assertEquals(emptyList(), ContentValidator.validate(high), "a too-high grade saturates, not rejected")
    }

    @Test
    fun unknownTerrainCostKeyFailClosed() {
        // A per-class move-cost override on an unknown terrain id is silently dropped at MoveReachability
        // (lookup is by the tile's id) — reject it at load, mirroring terrain_affinity.
        val bad = validContent(tables = defaultTables().copy(classes = listOf(classDef(terrainCost = mapOf("terrain_void" to 2)))))
        assertTrue(ContentValidator.validate(bad).any { it.path == "classes[0].terrain_cost" })

        val ok = validContent(tables = defaultTables().copy(classes = listOf(classDef(terrainCost = mapOf("plain" to 2)))))
        assertEquals(emptyList(), ContentValidator.validate(ok), "a known terrain cost key validates")
    }

    @Test
    fun nonPositiveUnitLevelFailClosed() {
        // level < 1 budgets to the base panel (growth scales by level-1, coerced to 0), masking a data error.
        val zero = validContent(tables = defaultTables().copy(units = listOf(unitDef(profile = profile(level = 0)))))
        assertTrue(ContentValidator.validate(zero).any { it.path == "units[0].level" })

        val negative = validContent(tables = defaultTables().copy(units = listOf(unitDef(profile = profile(level = -3)))))
        assertTrue(ContentValidator.validate(negative).any { it.path == "units[0].level" })
    }

    @Test
    fun nonPositiveUnitHpMaxFailClosed() {
        // hp_max <= 0 is clamped to 0 by the budget → a unit with hp = hpMax = 0 is dead-on-arrival
        // (alive = hp > 0 is false), so SpawnUnit would deploy a 0/0 ghost: reject at load, fail-closed.
        val zero = validContent(tables = defaultTables().copy(units = listOf(unitDef(profile = profile(hpMax = 0)))))
        assertTrue(ContentValidator.validate(zero).any { it.path == "units[0].hp_max" })

        val negative = validContent(tables = defaultTables().copy(units = listOf(unitDef(profile = profile(hpMax = -10)))))
        assertTrue(ContentValidator.validate(negative).any { it.path == "units[0].hp_max" })

        val one = validContent(tables = defaultTables().copy(units = listOf(unitDef(profile = profile(hpMax = 1)))))
        assertEquals(emptyList(), ContentValidator.validate(one), "hp_max == 1 is the valid floor")
    }

    @Test
    fun negativeUnitStatFailClosed() {
        // A negative stat is silently clamped to 0 by the budget (masking a data error); 0 itself is
        // legitimate (a unit with no magic), so only negatives gate — defense-in-depth alongside hp_max.
        // All four stat branches are covered so a typo in any one is caught.
        val negAtk = validContent(tables = defaultTables().copy(units = listOf(unitDef(profile = profile(stats = CombatStats(-1, 120, 60, 90))))))
        assertTrue(ContentValidator.validate(negAtk).any { it.path == "units[0].stats.atk" })
        val negDef = validContent(tables = defaultTables().copy(units = listOf(unitDef(profile = profile(stats = CombatStats(180, -1, 60, 90))))))
        assertTrue(ContentValidator.validate(negDef).any { it.path == "units[0].stats.def" })
        val negMat = validContent(tables = defaultTables().copy(units = listOf(unitDef(profile = profile(stats = CombatStats(180, 120, -1, 90))))))
        assertTrue(ContentValidator.validate(negMat).any { it.path == "units[0].stats.mat" })
        val negRes = validContent(tables = defaultTables().copy(units = listOf(unitDef(profile = profile(stats = CombatStats(180, 120, 60, -1))))))
        assertTrue(ContentValidator.validate(negRes).any { it.path == "units[0].stats.res" })

        val zeroMagic = validContent(tables = defaultTables().copy(units = listOf(unitDef(profile = profile(stats = CombatStats(180, 120, 0, 0))))))
        assertEquals(emptyList(), ContentValidator.validate(zeroMagic), "a 0 stat is legitimate, not rejected")
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

    private fun classDef(
        terrainAffinity: Map<String, Int> = emptyMap(),
        terrainCost: Map<String, Int> = emptyMap(),
    ): ClassDef =
        ClassDef(
            id = "cavalry",
            name = "Cavalry",
            movement = ClassMovement(moveType = "horse", move = 6, terrainCost = terrainCost),
            combat = ClassCombat(skills = listOf("atk"), terrainAffinity = terrainAffinity),
        )

    private fun itemDef(id: String = "sword", equipClass: String? = null): ItemDef =
        ItemDef(id = id, name = id, type = "weapon", equipClass = equipClass)

    private fun unitDef(
        classId: String = "cavalry",
        skills: List<String> = listOf("atk"),
        // Profile knobs are bundled into a value object (CCZ rule: not loose params) — build via profile(...).
        profile: UnitProfile = profile(),
    ): UnitDef =
        UnitDef(
            identity = UnitIdentity("zhaoyun", "Zhao Yun", classId, Faction.PLAYER),
            profile = profile,
            loadout = UnitLoadout(skills = skills),
        )

    private fun profile(
        level: Int = 1,
        hpMax: Int = 200,
        stats: CombatStats = CombatStats(180, 120, 60, 90),
        grade: Int = 0,
    ): UnitProfile = UnitProfile(level = level, hpMax = hpMax, stats = stats, grade = grade)

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
