package com.ccz.modimport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LegacyUnitMapperTest {
    @Test
    fun mapsDicHeroRowsToUnitsWithStatBridge() {
        val json = """
            [
              {"hid":1,"name":"刘备","jobid":1,"atk":78,"def":87,"ints":83,"burst":85,"fortune":100,
               "level":1,"hp":168,"mp":30,"skill":0},
              {"hid":2,"name":"关羽","jobid":3,"atk":120,"def":90,"ints":40,"burst":60,"level":5,"hp":300,"skill":11}
            ]
        """.trimIndent()

        val units = LegacyUnitMapper.mapUnits(json)

        assertEquals(2, units.size)
        assertEquals(
            PackUnit(
                identity = PackIdentity("hero_1", "刘备", "job_1", "PLAYER"),
                // strength 78+87+83+85 = 333 lands grade 2 on the engine's own quality ladder.
                profile = PackProfile(level = 1, hpMax = 168, stats = PackStats(78, 87, 83, 85), grade = 2),
                loadout = PackLoadout(skills = emptyList()),
            ),
            units[0],
        )
        // skill != 0 -> a single skill ref; ints->mat, burst->res bridge
        assertEquals(listOf("skill_11"), units[1].loadout.skills)
        assertEquals(PackStats(120, 90, 40, 60), units[1].profile.stats)
        assertEquals("job_3", units[1].identity.classId)
        assertEquals(1, units[1].profile.grade, "strength 120+90+40+60 = 310 lands grade 1")
    }

    @Test
    fun gradesHeroesByCombatStrengthAsAnEngineRule() {
        // The grade is THIS engine's own quality tier forged from combat-stat strength (atk+def+ints+burst),
        // NOT a port of the legacy dic_grade table. Six tiers via thresholds [290,330,380,470,1500], where
        // grade = how many thresholds the strength meets, so the lower bound of each tier is inclusive.
        assertEquals(0, gradeOf(50, 50, 40, 40), "strength 180 < 290 -> baseline tier 0")
        assertEquals(0, gradeOf(100, 100, 49, 40), "strength 289 -> still tier 0")
        assertEquals(1, gradeOf(100, 100, 50, 40), "strength 290 -> tier 1 (lower bound inclusive)")
        assertEquals(2, gradeOf(90, 90, 90, 80), "strength 350 -> tier 2")
        assertEquals(3, gradeOf(100, 100, 100, 80), "strength 380 -> tier 3")
        assertEquals(4, gradeOf(120, 120, 120, 110), "strength 470 -> tier 4")
        assertEquals(5, gradeOf(500, 500, 500, 500), "strength 2000 (maxed stats) -> top tier 5")
    }

    private fun gradeOf(atk: Int, def: Int, ints: Int, burst: Int): Int =
        LegacyUnitMapper.mapUnits(
            """[{"hid":1,"name":"x","jobid":1,"hp":100,"atk":$atk,"def":$def,"ints":$ints,"burst":$burst}]""",
        ).single().profile.grade

    @Test
    fun emitsSnakeCaseUnitsJson() {
        val out = LegacyUnitMapper.toUnitsJson(
            listOf(
                PackUnit(
                    PackIdentity("hero_1", "刘备", "job_1", "PLAYER"),
                    PackProfile(1, 168, PackStats(78, 87, 83, 85)),
                ),
            ),
        )
        assertTrue(out.contains("\"class_id\""), out)
        assertTrue(out.contains("\"hp_max\""), out)
        assertTrue(out.contains("刘备"), out)
    }

    @Test
    fun toleratesBomAndUnknownFields() {
        val units = LegacyUnitMapper.mapUnits(
            "﻿[{\"hid\":9,\"name\":\"x\",\"jobid\":1,\"hp\":50,\"weapon_id\":3,\"kill\":\"k\"}]",
        )
        assertEquals("hero_9", units.single().identity.id)
    }

    @Test
    fun rejectsDuplicateHidFailClosed() {
        val json = "[{\"hid\":1,\"name\":\"a\",\"jobid\":1,\"hp\":1},{\"hid\":1,\"name\":\"b\",\"jobid\":1,\"hp\":1}]"
        assertFailsWith<IllegalArgumentException> { LegacyUnitMapper.mapUnits(json) }
    }

    @Test
    fun rejectsNonPositiveHpAndLevel() {
        assertFailsWith<IllegalArgumentException> {
            LegacyUnitMapper.mapUnits("[{\"hid\":1,\"name\":\"x\",\"jobid\":1,\"hp\":0}]")
        }
        assertFailsWith<IllegalArgumentException> {
            LegacyUnitMapper.mapUnits("[{\"hid\":1,\"name\":\"x\",\"jobid\":1,\"hp\":1,\"level\":0}]")
        }
    }
}
