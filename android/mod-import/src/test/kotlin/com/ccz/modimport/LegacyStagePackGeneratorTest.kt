package com.ccz.modimport

import com.ccz.contentpack.ContentValidator
import com.ccz.contentpack.assembly.CampaignAssembler
import com.ccz.contentpack.json.ContentJsonLoader
import com.ccz.core.event.WinLoseCondition
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LegacyStagePackGeneratorTest {
    @Test
    fun generatesValidatedAssembledStagePackForReadyRow() {
        val json = LegacyStagePackGenerator.generate(
            extractedRoot = legacyRoot(
                script = EexFixtures.eexBlob(
                    enemyRec(listOf(EexFixtures.DispatchUnit(slot = 0, hid = 101, x = 0, y = 0, level = 2))),
                    friendRec(listOf(EexFixtures.DispatchUnit(slot = 0, hid = 201, x = 4, y = 0, level = 3))),
                ),
            ),
            stageId = 2,
        )

        val content = ContentJsonLoader.load(json)
        assertEquals(emptyList(), ContentValidator.validate(content))
        assertEquals("trssgshz_legacy_stage_2", content.manifest.contentId)
        assertEquals("legacy_stage_2", content.manifest.entry)
        assertEquals("legacy_stage_2_map", content.tables.maps.single().id)

        val setup = CampaignAssembler.assemble(content, "legacy_stage_2", "legacy_stage_2_map")
        val units = setup.initialState.units
        assertEquals(5, units.size, "three default player units + imported enemy/friend")
        assertEquals(Faction.PLAYER, units.getValue("hero_1").faction)
        assertEquals(Faction.PLAYER, units.getValue("hero_2").faction)
        assertEquals(Faction.PLAYER, units.getValue("hero_3").faction)
        assertEquals(Faction.ENEMY, units.getValue("hero_101").faction)
        assertEquals(Faction.ALLY, units.getValue("hero_201").faction)
        assertTrue(setup.context.loadouts.values.all { it == listOf("skill_1") }, "everyone gets the generated basic attack")
        assertEquals(listOf(WinLoseCondition.AnnihilateEnemies), setup.script.win)
        assertEquals(listOf(WinLoseCondition.ProtectAlive("hero_1")), setup.script.lose)
    }

    @Test
    fun appliesScriptRefCollisionProposalAsDeferredMetadata() {
        val json = LegacyStagePackGenerator.generate(
            extractedRoot = legacyRoot(
                script = EexFixtures.eexBlob(
                    enemyRec(
                        listOf(
                            EexFixtures.DispatchUnit(slot = 0, hid = 101, x = 1, y = 1, level = 2),
                            EexFixtures.DispatchUnit(slot = 1, hid = 102, x = 1, y = 1, level = 3),
                        ),
                    ),
                    actorVisibleRec(actor = 102),
                ),
            ),
            stageId = 2,
        )

        val content = ContentJsonLoader.load(json)
        assertEquals(emptyList(), ContentValidator.validate(content))
        val setup = CampaignAssembler.assemble(content, "legacy_stage_2", "legacy_stage_2_map")
        assertTrue("hero_102" !in setup.initialState.units, "script-referenced collision unit is not opening deployment")
        assertTrue("hero_102" in setup.scriptContext.reserves, "deferred collision unit stays in reserves")
        val deferred = setup.deferredDeployments.single()
        assertEquals("hero_102", deferred.unit)
        assertEquals(Pos(1, 1), deferred.at)
        assertEquals(Faction.ENEMY, deferred.faction)
        assertEquals("legacy_actor_state_refs", deferred.source)
        assertEquals(listOf("skill_1"), setup.context.loadouts.getValue("hero_102"))
    }

    @Test
    fun rejectsCollisionStagesUntilProposalIsApplied() {
        val error = assertFailsWith<IllegalArgumentException> {
            LegacyStagePackGenerator.generate(
                extractedRoot = legacyRoot(
                    script = EexFixtures.eexBlob(
                        enemyRec(
                            listOf(
                                EexFixtures.DispatchUnit(slot = 0, hid = 101, x = 1, y = 1, level = 2),
                                EexFixtures.DispatchUnit(slot = 1, hid = 102, x = 1, y = 1, level = 2),
                            ),
                        ),
                    ),
                ),
                stageId = 2,
            )
        }

        assertTrue(error.message.orEmpty().contains("unresolved deployment collision"))
    }

    private fun legacyRoot(script: ByteArray): String {
        val dir = createTempDirectory("legacy-stage-pack-")
        val json = dir.resolve("json").createDirectories()
        val scenes = dir.resolve("Scenes").createDirectories()
        val terrain = dir.resolve("terrainJson").createDirectories()
        json.resolve("dic_hero.json").writeText(heroRows())
        json.resolve("dic_job.json").writeText("""[{ "jobid": 1, "name": "Soldier", "move": 3 }]""")
        json.resolve("map_terrain.json").writeText("""[{ "mapid": 0, "name": "Plain" }]""")
        terrain.resolve("terrainMap_2.json").writeText(
            """{"map_width":5,"map_height":4,"map_value":[[0,0,0,0,0],[0,0,0,0,0],[0,0,0,0,0],[0,0,0,0,0]]}""",
        )
        scenes.resolve("S_01.eex_new").writeBytes(script)
        return dir.toString()
    }

    private fun heroRows(): String =
        """[
            { "hid": 1, "name": "Liu Bei", "jobid": 1, "atk": 120, "def": 40, "ints": 30, "burst": 20, "level": 1, "hp": 200 },
            { "hid": 2, "name": "Guan Yu", "jobid": 1, "atk": 140, "def": 60, "ints": 30, "burst": 30, "level": 1, "hp": 220 },
            { "hid": 3, "name": "Zhang Fei", "jobid": 1, "atk": 135, "def": 55, "ints": 20, "burst": 25, "level": 1, "hp": 210 },
            { "hid": 101, "name": "Enemy A", "jobid": 1, "atk": 50, "def": 20, "ints": 10, "burst": 5, "level": 1, "hp": 100 },
            { "hid": 102, "name": "Enemy B", "jobid": 1, "atk": 45, "def": 18, "ints": 10, "burst": 5, "level": 1, "hp": 100 },
            { "hid": 201, "name": "Ally A", "jobid": 1, "atk": 60, "def": 30, "ints": 10, "burst": 10, "level": 1, "hp": 120 }
        ]""".trimIndent()

    private fun enemyRec(units: List<EexFixtures.DispatchUnit>): ByteArray =
        EexFixtures.dispatchRec(
            cmd = 0x47,
            layout = EexFixtures.DispatchLayout(slots = 80, stride = 0x38, xOff = 0xe, yOff = 0x14),
            units = units,
        )

    private fun friendRec(units: List<EexFixtures.DispatchUnit>): ByteArray =
        EexFixtures.dispatchRec(
            cmd = 0x46,
            layout = EexFixtures.DispatchLayout(slots = 20, stride = 0x34, xOff = 0xa, yOff = 0x10),
            units = units,
        )

    private fun actorVisibleRec(actor: Int): ByteArray =
        ByteArray(0x10).also {
            putS16(it, 0x00, 0x4c)
            putS16(it, 0x02, 0x40)
            putS16(it, 0x06, 0x02)
            putS16(it, 0x08, actor)
            putS16(it, 0x0a, 0x04)
        }

    private fun putS16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xff).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xff).toByte()
    }
}
