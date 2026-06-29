package com.ccz.modimport

import com.ccz.modimport.EexFixtures.DispatchLayout
import com.ccz.modimport.EexFixtures.DispatchUnit
import com.ccz.modimport.EexFixtures.dispatchRec
import com.ccz.modimport.EexFixtures.eexBlob
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegacyStageMigrationPlannerTest {
    @Test
    fun plansReadyStageAndBlockedMissingAssets() {
        val report = LegacyStageMigrationPlanner.plan(legacyRoot())

        assertEquals(2, report.totals.stages.stageRows)
        assertEquals("legacy_decoded", report.opcodeProfile)
        assertEquals(1, report.totals.stages.ready)
        assertEquals(1, report.totals.stages.blocked)
        assertEquals(1, report.totals.diagnostics.missingScript)
        assertEquals(1, report.totals.diagnostics.missingMap)
        assertEquals(1, report.totals.deployment.units)

        val ready = report.stages.first { it.stage.gkid == 1 }
        assertEquals("S_00.eex_new", ready.assets.scriptFile)
        assertEquals("terrainMap_1.json", ready.assets.mapFile)
        assertEquals(3, ready.map?.mapWidth)
        assertEquals(3, ready.map?.mapHeight)
        assertEquals(1, ready.deployment.deploymentUnits)
        assertEquals(1, ready.deployment.enemyUnits)
        assertEquals("ready", ready.diagnostics.status)
        assertTrue(ready.diagnostics.reasons.isEmpty())

        val blocked = report.stages.first { it.stage.gkid == 2 }
        assertEquals("S_01.eex_new", blocked.assets.scriptFile)
        assertEquals("terrainMap_2.json", blocked.assets.mapFile)
        assertEquals("blocked", blocked.diagnostics.status)
        assertTrue("missing_script" in blocked.diagnostics.reasons)
        assertTrue("missing_map" in blocked.diagnostics.reasons)
    }

    @Test
    fun emitsStructuredJsonReport() {
        val json = LegacyStageMigrationPlanner.planJson(
            legacyRoot(
                stage1Script = eexBlob(
                    enemyRec(
                        listOf(
                            DispatchUnit(slot = 0, hid = 101, x = 1, y = 1, level = 2),
                            DispatchUnit(slot = 1, hid = 999, x = 1, y = 1, level = 2),
                        ),
                    ),
                    actorVisibleRec(actor = 999),
                ),
                heroRows = """[{ "hid": 101, "name": "Enemy" }, { "hid": 999, "name": "Hidden" }]""",
            ),
        )

        assertTrue("\"binding_policy\"" in json)
        assertTrue("\"stage_rows\"" in json)
        assertTrue("\"script_file\"" in json)
        assertTrue("\"deployment_units\"" in json)
        assertTrue("\"groups_with_script_refs\"" in json)
        assertTrue("\"script_ref_coverage\"" in json)
        assertTrue("\"resolution_proposal\"" in json)
        assertTrue("\"collision_resolution_preview\"" in json)
        assertTrue("\"trial_assembly\"" in json)
        assertTrue("\"groups_with_proposals\"" in json)
        assertTrue("\"stages_ready_if_proposals_applied\"" in json)
        assertTrue("\"groups_one_unreferenced_unit\"" in json)
    }

    @Test
    fun trialAssemblesProposalReadyCollisionAsDeferredMetadata() {
        val report = LegacyStageMigrationPlanner.plan(
            legacyRoot(
                stage1Script = eexBlob(
                    enemyRec(
                        listOf(
                            DispatchUnit(slot = 0, hid = 101, x = 1, y = 1, level = 2),
                            DispatchUnit(slot = 1, hid = 999, x = 1, y = 1, level = 3),
                        ),
                    ),
                    actorVisibleRec(actor = 999),
                ),
                heroRows = richHeroRows(),
                includeAssemblyTables = true,
            ),
        )

        val stage = report.stages.first { it.stage.gkid == 1 }

        assertEquals("blocked", stage.diagnostics.status)
        assertEquals("ready", stage.diagnostics.collisionResolutionPreview?.statusAfterProposals)
        assertEquals("ready", stage.diagnostics.trialAssembly?.status)
        assertEquals(1, stage.diagnostics.trialAssembly?.openingUnits)
        assertEquals(1, stage.diagnostics.trialAssembly?.deferredUnits)
        assertEquals(emptyList(), stage.diagnostics.trialAssembly?.reasons)
    }

    @Test
    fun blocksCollidingDeploymentAndUnknownHeroes() {
        val report = LegacyStageMigrationPlanner.plan(
            legacyRoot(
                stage1Script = eexBlob(
                    enemyRec(
                        listOf(
                            DispatchUnit(slot = 0, hid = 101, x = 1, y = 1, level = 2),
                            DispatchUnit(slot = 1, hid = 999, x = 1, y = 1, level = 2),
                        ),
                    ),
                    actorVisibleRec(actor = 999),
                ),
            ),
        )

        val stage = report.stages.first { it.stage.gkid == 1 }
        assertEquals("blocked", stage.diagnostics.status)
        assertEquals(1, stage.deployment.collisions)
        assertEquals(1, stage.deployment.unknownHids)
        assertEquals(1, report.totals.deployment.collisionGroups.groups)
        assertEquals(2, report.totals.deployment.collisionGroups.units)
        assertEquals(1, report.totals.deployment.collisionGroups.groupsWithScriptRefs)
        assertEquals(1, report.totals.deployment.collisionGroups.scriptRefRows)
        assertEquals(0, report.totals.deployment.collisionGroups.coverage.groupsWithoutRefs)
        assertEquals(0, report.totals.deployment.collisionGroups.coverage.groupsAllUnitsReferenced)
        assertEquals(1, report.totals.deployment.collisionGroups.coverage.groupsOneUnreferencedUnit)
        assertEquals(1, report.totals.deployment.collisionGroups.coverage.groupsWithUnreferencedUnits)
        assertEquals(0, report.totals.deployment.collisionGroups.coverage.groupsMixedRefs)
        assertEquals(1, report.totals.deployment.collisionGroups.resolution.groupsWithProposals)
        assertEquals(1, report.totals.deployment.collisionGroups.resolution.stagesWithProposals)
        assertEquals(1, report.totals.deployment.collisionGroups.resolution.stagesWithOnlyProposedCollisionGroups)
        assertEquals(0, report.totals.deployment.collisionGroups.resolution.stagesReadyIfProposalsApplied)
        assertEquals(1, report.totals.deployment.collisionGroups.resolution.openingUnits)
        assertEquals(1, report.totals.deployment.collisionGroups.resolution.deferredUnits)
        assertTrue(stage.diagnostics.reasons.any { it.startsWith("deployment_collisions:") })
        assertTrue(stage.diagnostics.reasons.any { it.startsWith("unknown_deployment_hids:") })
        val group = stage.diagnostics.collisionGroups.single()
        assertEquals(1, group.x)
        assertEquals(1, group.y)
        assertEquals(listOf(101, 999), group.units.map { it.hid })
        assertEquals(listOf(0, 1), group.units.map { it.slot })
        assertTrue(group.units.all { it.recordOffset.startsWith("0x") })
        assertTrue(group.units.all { it.rawWords.size == 28 })
        assertEquals(listOf(999), group.scriptRefs.map { it.hid })
        assertEquals(listOf("set_actor_visible"), group.scriptRefs.map { it.kind })
        assertEquals(listOf(0, 0), group.scriptRefs.single().values)
        assertEquals("one_unreferenced_unit", group.scriptRefCoverage.bucket)
        assertEquals(listOf(999), group.scriptRefCoverage.referencedHids)
        assertEquals("enemy", group.scriptRefCoverage.singleUnreferencedUnit?.side)
        assertEquals(101, group.scriptRefCoverage.singleUnreferencedUnit?.hid)
        assertEquals(0, group.scriptRefCoverage.singleUnreferencedUnit?.slot)
        assertEquals("opening_unit_with_deferred_actor_state_refs", group.resolutionProposal?.kind)
        assertEquals(101, group.resolutionProposal?.openingUnit?.hid)
        assertEquals(listOf(999), group.resolutionProposal?.deferredUnits?.map { it.hid })
        assertUnknownCollisionPreview(stage)
    }

    private fun assertUnknownCollisionPreview(stage: StageMigrationRow) {
        assertEquals("blocked", stage.diagnostics.collisionResolutionPreview?.statusAfterProposals)
        assertTrue(
            stage.diagnostics.collisionResolutionPreview?.remainingReasons.orEmpty()
                .any { it.startsWith("unknown_deployment_hids:") },
        )
        assertEquals(1, stage.diagnostics.collisionResolutionPreview?.proposedGroups)
        assertEquals(0, stage.diagnostics.collisionResolutionPreview?.unresolvedGroups)
    }

    @Test
    fun detectsCurrentApkProfileAndIgnoresLegacyDispatchCoincidence() {
        val report = LegacyStageMigrationPlanner.plan(
            legacyRoot(
                stage1Script = eexBlob(
                    enemyRec(listOf(DispatchUnit(slot = 0, hid = 999, x = 1, y = 1, level = 2)), cmd = 0x47),
                    enemyRec(listOf(DispatchUnit(slot = 0, hid = 101, x = 2, y = 2, level = 2)), cmd = 0xde),
                ),
            ),
        )

        val stage = report.stages.first { it.stage.gkid == 1 }
        assertEquals("trssgshz_current_apk", report.opcodeProfile)
        assertEquals("ready", stage.diagnostics.status)
        assertEquals(1, stage.deployment.deploymentUnits)
        assertEquals(1, stage.deployment.enemyUnits)
        assertEquals(0, stage.deployment.unknownHids)
        assertEquals(0, stage.deployment.collisions)
        assertTrue(stage.diagnostics.reasons.isEmpty())
    }

    private fun legacyRoot(
        stage1Script: ByteArray = eexBlob(enemyRec(listOf(DispatchUnit(slot = 0, hid = 101, x = 1, y = 1, level = 2)))),
        heroRows: String = """[{ "hid": 101, "name": "Enemy" }]""",
        includeAssemblyTables: Boolean = false,
    ): String {
        val dir = createTempDirectory("legacy-stage-plan-")
        val json = dir.resolve("json").createDirectories()
        val scenes = dir.resolve("Scenes").createDirectories()
        val terrain = dir.resolve("terrainJson").createDirectories()
        json.resolve("dic_gk.json").writeText(
            """[
                { "gkid": 1, "gkname": "Stage 1", "stageMode": "battle" },
                { "gkid": 2, "gkname": "Stage 2", "stageMode": "battle" }
            ]""".trimIndent(),
        )
        json.resolve("dic_hero.json").writeText(heroRows)
        if (includeAssemblyTables) {
            json.resolve("dic_job.json").writeText("""[{ "jobid": 1, "name": "Soldier", "move": 3 }]""")
            json.resolve("dic_skill.json").writeText(
                """[{ "skid": 1, "name": "Strike", "type": 0, "hurt_num": 100, "mp_consume": 0 }]""",
            )
            json.resolve("map_terrain.json").writeText("""[{ "mapid": 0, "name": "Plain" }]""")
        }
        terrain.resolve("terrainMap_1.json").writeText(
            """{"map_width":3,"map_height":3,"map_value":[[0,0,0],[0,0,0],[0,0,0]]}""",
        )
        scenes.resolve("S_00.eex_new").writeBytes(stage1Script)
        return dir.toString()
    }

    private fun richHeroRows(): String =
        """[
            {
              "hid": 101, "name": "Enemy", "jobid": 1, "atk": 30, "def": 20,
              "ints": 10, "burst": 5, "level": 1, "hp": 100, "skill": 1
            },
            {
              "hid": 999, "name": "Hidden", "jobid": 1, "atk": 25, "def": 18,
              "ints": 8, "burst": 4, "level": 1, "hp": 90, "skill": 1
            }
        ]""".trimIndent()

    private fun enemyRec(units: List<DispatchUnit>, cmd: Int = 0x47): ByteArray =
        dispatchRec(
            cmd = cmd,
            layout = DispatchLayout(slots = 80, stride = 0x38, xOff = 0xe, yOff = 0x14),
            units = units,
        )

    private fun actorVisibleRec(actor: Int, cmd: Int = 0x4c): ByteArray =
        ByteArray(0x10).also {
            putS16(it, 0x00, cmd)
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
