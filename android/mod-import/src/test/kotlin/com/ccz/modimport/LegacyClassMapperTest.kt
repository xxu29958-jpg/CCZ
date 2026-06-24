package com.ccz.modimport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LegacyClassMapperTest {
    @Test
    fun foldsDicJobWalkIntoPerClassTerrainCost() {
        val job = """[{"jobid":1,"name":"骑兵","move":5}]"""
        // terrain 1 = cost 1 (omitted, = tile base), terrain 2 = cost 2, terrain 3 = 255 impassable -> 0
        val walk = """[{"id":1,"1":1,"2":2,"3":255}]"""

        val movement = LegacyClassMapper.mapClasses(job, walk).single().movement

        assertEquals(mapOf("terrain_2" to 2, "terrain_3" to 0), movement.terrainCost)
    }

    @Test
    fun terrainCostEmptyWhenNoJobWalkSupplied() {
        val classes = LegacyClassMapper.mapClasses("""[{"jobid":1,"name":"步兵","move":3}]""")
        assertTrue(classes.single().movement.terrainCost.isEmpty())
    }

    @Test
    fun mapsDicJobRowsToPackClassesIgnoringRichFields() {
        // real dic_job rows carry spe/atk/def/.../hp_up/atkid/seid/info — all ignored for this slice
        val json = """
            [
              {"jobid":1,"name":"群雄","spe":6,"atk":3,"hp_up":5,"move":2,"atkid":1,"seid":"267&","info":"x"},
              {"jobid":2,"name":"英雄","move":1}
            ]
        """.trimIndent()

        val classes = LegacyClassMapper.mapClasses(json)

        assertEquals(2, classes.size)
        assertEquals(PackClass("job_1", "群雄", PackMovement(LegacyClassMapper.DEFAULT_MOVE_TYPE, 2)), classes[0])
        assertEquals(PackClass("job_2", "英雄", PackMovement(LegacyClassMapper.DEFAULT_MOVE_TYPE, 1)), classes[1])
    }

    @Test
    fun toleratesUtf8Bom() {
        val classes = LegacyClassMapper.mapClasses("﻿[{\"jobid\":7,\"name\":\"弓兵\",\"move\":1}]")
        assertEquals("job_7", classes.single().id)
    }

    @Test
    fun emitsSnakeCaseClassesJson() {
        val out = LegacyClassMapper.toClassesJson(
            listOf(PackClass("job_1", "群雄", PackMovement("ground", 2))),
        )
        assertTrue(out.contains("\"move_type\""), "wire key must be snake_case move_type: $out")
        assertTrue(out.contains("\"id\": \"job_1\""), out)
        assertTrue(out.contains("群雄"), "Chinese names must survive serialization: $out")
    }

    @Test
    fun rejectsDuplicateJobIdsFailClosed() {
        val json = "[{\"jobid\":1,\"name\":\"a\",\"move\":1},{\"jobid\":1,\"name\":\"b\",\"move\":1}]"
        assertFailsWith<IllegalArgumentException> { LegacyClassMapper.mapClasses(json) }
    }

    @Test
    fun rejectsBlankNameAndNegativeMove() {
        assertFailsWith<IllegalArgumentException> {
            LegacyClassMapper.mapClasses("[{\"jobid\":1,\"name\":\" \",\"move\":1}]")
        }
        assertFailsWith<IllegalArgumentException> {
            LegacyClassMapper.mapClasses("[{\"jobid\":1,\"name\":\"x\",\"move\":-1}]")
        }
    }
}
