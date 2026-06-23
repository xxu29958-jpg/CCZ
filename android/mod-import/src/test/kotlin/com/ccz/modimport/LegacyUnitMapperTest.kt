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
                profile = PackProfile(level = 1, hpMax = 168, stats = PackStats(78, 87, 83, 85)),
                loadout = PackLoadout(skills = emptyList()),
            ),
            units[0],
        )
        // skill != 0 -> a single skill ref; ints->mat, burst->res bridge
        assertEquals(listOf("skill_11"), units[1].loadout.skills)
        assertEquals(PackStats(120, 90, 40, 60), units[1].profile.stats)
        assertEquals("job_3", units[1].identity.classId)
    }

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
