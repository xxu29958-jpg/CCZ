package com.ccz.modimport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LegacySkillMapperTest {
    @Test
    fun mapsDicSkillRowsIgnoringFxFields() {
        val json = """
            [
              {"skid":1,"name":"灼热","type":0,"atkid":14,"mp_consume":6,"hurt_num":80,"skill_type":900,"seid":0},
              {"skid":2,"name":"奇谋","type":1,"mp_consume":15,"hurt_num":100}
            ]
        """.trimIndent()

        val skills = LegacySkillMapper.mapSkills(json)

        assertEquals(2, skills.size)
        assertEquals(
            PackSkill("skill_1", "灼热", "PHYSICAL", 80, PackUse(PackRange(1, 1), "single", "enemy", 6)),
            skills[0],
        )
        assertEquals("STRATEGY", skills[1].kind)
        assertEquals(100, skills[1].powerCoeff)
        assertEquals(15, skills[1].use.mpCost)
    }

    @Test
    fun emitsSnakeCaseSkillsJson() {
        val out = LegacySkillMapper.toSkillsJson(
            listOf(PackSkill("skill_1", "灼热", "PHYSICAL", 80, PackUse(PackRange(1, 1), "single", "enemy", 6))),
        )
        assertTrue(out.contains("\"power_coeff\""), out)
        assertTrue(out.contains("\"mp_cost\""), out)
        assertTrue(out.contains("灼热"), out)
    }

    @Test
    fun toleratesBomAndUnknownFields() {
        val skills = LegacySkillMapper.mapSkills("﻿[{\"skid\":5,\"name\":\"x\",\"hurt_num\":1,\"mark_num\":99,\"info\":\"y\"}]")
        assertEquals("skill_5", skills.single().id)
    }

    @Test
    fun rejectsDuplicateSkidFailClosed() {
        val json = "[{\"skid\":1,\"name\":\"a\"},{\"skid\":1,\"name\":\"b\"}]"
        assertFailsWith<IllegalArgumentException> { LegacySkillMapper.mapSkills(json) }
    }

    @Test
    fun rejectsBlankNameAndNegativeNumbers() {
        assertFailsWith<IllegalArgumentException> { LegacySkillMapper.mapSkills("[{\"skid\":1,\"name\":\" \"}]") }
        assertFailsWith<IllegalArgumentException> {
            LegacySkillMapper.mapSkills("[{\"skid\":1,\"name\":\"x\",\"hurt_num\":-1}]")
        }
    }
}
