package com.ccz.modimport

import com.ccz.contentpack.ContentValidator
import com.ccz.contentpack.assembly.CampaignAssembler
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class StageMigrationReport(
    @SerialName("source_mod") val sourceMod: String,
    @SerialName("binding_policy") val bindingPolicy: String,
    @SerialName("opcode_profile") val opcodeProfile: String,
    val totals: StageMigrationTotals,
    val stages: List<StageMigrationRow>,
)

@Serializable
data class StageMigrationTotals(
    val stages: StageStatusTotals,
    val diagnostics: StageDiagnosticTotals,
    val objectives: StageObjectiveTotals,
    val deployment: StageDeploymentTotals,
)

@Serializable
data class StageStatusTotals(
    @SerialName("stage_rows") val stageRows: Int,
    val ready: Int,
    val blocked: Int,
)

@Serializable
data class StageDiagnosticTotals(
    @SerialName("missing_script") val missingScript: Int,
    @SerialName("missing_map") val missingMap: Int,
    @SerialName("map_errors") val mapErrors: Int,
    @SerialName("deployment_errors") val deploymentErrors: Int,
    @SerialName("empty_deployment") val emptyDeployment: Int,
    @SerialName("objective_errors") val objectiveErrors: Int,
)

@Serializable
data class StageObjectiveTotals(
    val unsupported: Int,
)

@Serializable
data class StageDeploymentTotals(
    @SerialName("reinforcement_records") val reinforcementRecords: Int,
    val units: Int,
    val collisions: Int,
    @SerialName("unknown_hids") val unknownHids: Int,
    @SerialName("collision_groups") val collisionGroups: StageCollisionEvidenceTotals = StageCollisionEvidenceTotals(),
)

@Serializable
data class StageCollisionEvidenceTotals(
    val groups: Int = 0,
    val units: Int = 0,
    @SerialName("groups_with_script_refs") val groupsWithScriptRefs: Int = 0,
    @SerialName("script_ref_rows") val scriptRefRows: Int = 0,
    val coverage: StageCollisionCoverageTotals = StageCollisionCoverageTotals(),
    val resolution: StageCollisionResolutionTotals = StageCollisionResolutionTotals(),
)

@Serializable
data class StageCollisionCoverageTotals(
    @SerialName("groups_without_refs") val groupsWithoutRefs: Int = 0,
    @SerialName("groups_all_units_referenced") val groupsAllUnitsReferenced: Int = 0,
    @SerialName("groups_one_unreferenced_unit") val groupsOneUnreferencedUnit: Int = 0,
    @SerialName("groups_with_unreferenced_units") val groupsWithUnreferencedUnits: Int = 0,
    @SerialName("groups_mixed_refs") val groupsMixedRefs: Int = 0,
)

@Serializable
data class StageCollisionResolutionTotals(
    @SerialName("groups_with_proposals") val groupsWithProposals: Int = 0,
    @SerialName("stages_with_proposals") val stagesWithProposals: Int = 0,
    @SerialName("stages_with_only_proposed_collision_groups")
    val stagesWithOnlyProposedCollisionGroups: Int = 0,
    @SerialName("stages_ready_if_proposals_applied") val stagesReadyIfProposalsApplied: Int = 0,
    @SerialName("opening_units") val openingUnits: Int = 0,
    @SerialName("deferred_units") val deferredUnits: Int = 0,
)

@Serializable
data class StageMigrationRow(
    val stage: StageIdentity,
    val assets: StageAssets,
    val map: StageMapProbe? = null,
    val deployment: StageDeploymentProbe = StageDeploymentProbe(),
    val objectives: StageObjectiveProbe = StageObjectiveProbe(),
    val diagnostics: StageDiagnostics,
)

@Serializable
data class StageIdentity(
    val gkid: Int,
    val name: String,
    @SerialName("stage_mode") val stageMode: String = "",
)

@Serializable
data class StageAssets(
    @SerialName("script_file") val scriptFile: String,
    @SerialName("map_file") val mapFile: String,
    @SerialName("map_binding") val mapBinding: String,
)

@Serializable
data class StageMapProbe(
    @SerialName("map_width") val mapWidth: Int? = null,
    @SerialName("map_height") val mapHeight: Int? = null,
)

@Serializable
data class StageDeploymentProbe(
    @SerialName("deployment_units") val deploymentUnits: Int = 0,
    @SerialName("enemy_units") val enemyUnits: Int = 0,
    @SerialName("friend_units") val friendUnits: Int = 0,
    @SerialName("reinforcement_records") val reinforcementRecords: Int = 0,
    val collisions: Int = 0,
    @SerialName("unknown_hids") val unknownHids: Int = 0,
)

@Serializable
data class StageObjectiveProbe(
    @SerialName("objective_win") val objectiveWin: Int = 0,
    @SerialName("objective_lose") val objectiveLose: Int = 0,
    @SerialName("objective_unsupported") val objectiveUnsupported: Int = 0,
    @SerialName("objective_out_of_roster") val objectiveOutOfRoster: Int = 0,
)

@Serializable
data class StageDiagnostics(
    val status: String,
    val reasons: List<String> = emptyList(),
    @SerialName("collision_groups") val collisionGroups: List<StageCollisionGroup> = emptyList(),
    @SerialName("collision_resolution_preview")
    val collisionResolutionPreview: StageCollisionResolutionPreview? = null,
    @SerialName("trial_assembly")
    val trialAssembly: StageTrialAssemblyProbe? = null,
)

@Serializable
data class StageCollisionGroup(
    val x: Int,
    val y: Int,
    val units: List<StageCollisionUnit>,
    @SerialName("script_refs") val scriptRefs: List<StageCollisionScriptRef> = emptyList(),
    @SerialName("script_ref_coverage")
    val scriptRefCoverage: StageCollisionScriptRefCoverage = StageCollisionScriptRefCoverage(),
    @SerialName("resolution_proposal")
    val resolutionProposal: StageCollisionResolutionProposal? = null,
)

@Serializable
data class StageCollisionUnit(
    val side: String,
    val hid: Int,
    val level: Int,
    val slot: Int,
    @SerialName("record_offset") val recordOffset: String,
    @SerialName("raw_words") val rawWords: List<Int>,
)

@Serializable
data class StageCollisionScriptRef(
    val kind: String,
    val hid: Int,
    @SerialName("record_offset") val recordOffset: String,
    val values: List<Int>,
)

@Serializable
data class StageCollisionScriptRefCoverage(
    val bucket: String = "no_script_refs",
    @SerialName("referenced_hids") val referencedHids: List<Int> = emptyList(),
    @SerialName("single_unreferenced_unit") val singleUnreferencedUnit: StageCollisionUnitKey? = null,
)

@Serializable
data class StageCollisionUnitKey(
    val side: String,
    val hid: Int,
    val slot: Int,
)

@Serializable
data class StageCollisionResolutionProposal(
    val kind: String,
    @SerialName("opening_unit") val openingUnit: StageCollisionUnitKey,
    @SerialName("deferred_units") val deferredUnits: List<StageCollisionUnitKey>,
)

@Serializable
data class StageCollisionResolutionPreview(
    @SerialName("status_after_proposals") val statusAfterProposals: String,
    @SerialName("remaining_reasons") val remainingReasons: List<String>,
    @SerialName("proposed_groups") val proposedGroups: Int,
    @SerialName("unresolved_groups") val unresolvedGroups: Int,
)

@Serializable
data class StageTrialAssemblyProbe(
    val status: String,
    val reasons: List<String> = emptyList(),
    @SerialName("opening_units") val openingUnits: Int = 0,
    @SerialName("deferred_units") val deferredUnits: Int = 0,
)

/**
 * Scans the legacy stage catalog against the decrypted script/map files and emits a fail-closed migration plan.
 *
 * Proven binding: `S_<gkid-1>.eex_new` is the stage script. Map binding is deliberately labeled as ordinal
 * legacy-native policy (`terrainMap_<gkid>.json`), because the EEX script and `dic_gk` table do not carry a
 * direct map id. This planner validates that inferred pair with the existing map and deployment importers.
 */
object LegacyStageMigrationPlanner {
    private const val SOURCE_MOD = "trssgshz"
    private const val READY = "ready"
    private const val BLOCKED = "blocked"
    private const val MAP_BINDING = "terrainMap_<gkid> ordinal legacy-native binding"
    private const val BINDING_POLICY = "script=S_<gkid-1>.eex_new; map=terrainMap_<gkid>.json"
    private const val HERO_PREFIX = "hero_"
    private const val TRIAL_ISSUE_LIMIT = 5

    private val reader = Json { ignoreUnknownKeys = true; isLenient = true }
    private val writer = Json { prettyPrint = true }

    fun plan(extractedRoot: String): StageMigrationReport {
        val context = contextFor(File(extractedRoot))
        val stages = readStageRows(context.jsonDir)
            .sortedBy { it.stageId }
            .map { planStage(it, context) }
        return StageMigrationReport(
            sourceMod = SOURCE_MOD,
            bindingPolicy = BINDING_POLICY,
            opcodeProfile = context.opcodeProfile.profileId,
            totals = totals(stages),
            stages = stages,
        )
    }

    fun planJson(extractedRoot: String): String =
        writer.encodeToString(StageMigrationReport.serializer(), plan(extractedRoot))

    private fun contextFor(root: File): PlannerContext {
        val jsonDir = File(root, "json")
        val sceneDir = File(root, "Scenes")
        val terrainDir = File(root, "terrainJson")
        if (!jsonDir.isDirectory) throw LegacyCommerceException("missing legacy json directory: ${jsonDir.path}")
        if (!sceneDir.isDirectory) throw LegacyCommerceException("missing legacy Scenes directory: ${sceneDir.path}")
        if (!terrainDir.isDirectory) throw LegacyCommerceException("missing legacy terrainJson directory: ${terrainDir.path}")
        return PlannerContext(jsonDir, sceneDir, terrainDir, readHeroIndex(jsonDir), detectOpcodeProfile(sceneDir, terrainDir))
    }

    private fun planStage(row: LegacyStagePlanRow, context: PlannerContext): StageMigrationRow {
        require(row.stageId > 0) { "dic_gk contains non-positive gkid: ${row.stageId}" }
        val reasons = ArrayList<String>()
        val scriptName = scriptName(row.stageId)
        val mapName = mapName(row.stageId)
        val script = File(context.sceneDir, scriptName)
        val map = File(context.terrainDir, mapName)
        if (!script.isFile) reasons.add("missing_script")
        val packMap = if (map.isFile) readMap(map, row.stageId, reasons) else null
        if (!map.isFile) reasons.add("missing_map")
        val deployment = if (script.isFile && packMap != null) {
            readDeployment(script, packMap.size.width, packMap.size.height, context, reasons)
        } else {
            null
        }
        val actorStateRefs = if (script.isFile) readActorStateRefs(script, context, reasons) else null
        val deploymentIssues = deployment?.let { validateDeployment(it, actorStateRefs, context, reasons) }
            ?: DeploymentIssueCounts()
        val objectives = if (script.isFile && deployment != null) {
            readObjectives(script, deployment, context, reasons)
        } else {
            null
        }
        if (deployment != null && deployment.units.isEmpty()) reasons.add("empty_deployment")
        val probe = StageProbe(packMap, deployment, objectives, deploymentIssues)
        val trialAssembly = trialAssembly(row, mapName, probe, context, reasons)
        return row.toMigrationRow(scriptName, mapName, probe.copy(trialAssembly = trialAssembly), reasons)
    }

    private fun readMap(map: File, stageId: Int, reasons: MutableList<String>): PackMap? =
        try {
            LegacyMapMapper.mapMap(read(map), id = "legacy_stage_${stageId}_map")
        } catch (e: RuntimeException) {
            reasons.add("map_error:${compact(e)}")
            null
        }

    private fun readDeployment(
        script: File,
        mapWidth: Int,
        mapHeight: Int,
        context: PlannerContext,
        reasons: MutableList<String>,
    ): LegacyRosterImporter.Deployment? =
        try {
            LegacyRosterImporter.importDeployment(script.readBytes(), mapWidth, mapHeight, context.opcodeProfile)
        } catch (e: RuntimeException) {
            reasons.add("deployment_error:${compact(e)}")
            null
        }

    private fun readActorStateRefs(
        script: File,
        context: PlannerContext,
        reasons: MutableList<String>,
    ): LegacyActorStateScanner.ActorStateRefs? =
        try {
            LegacyActorStateScanner.scan(script.readBytes(), context.opcodeProfile)
        } catch (e: RuntimeException) {
            reasons.add("actor_state_error:${compact(e)}")
            null
        }

    private fun readObjectives(
        script: File,
        deployment: LegacyRosterImporter.Deployment,
        context: PlannerContext,
        reasons: MutableList<String>,
    ): LegacyObjectiveImporter.ImportedObjectives? =
        try {
            val rosterIds = deployment.units.mapTo(HashSet()) { "$HERO_PREFIX${it.hid}" }
            LegacyObjectiveImporter.importObjectives(
                script.readBytes(),
                rosterIds,
                context.heroes.nameToId::get,
                context.opcodeProfile,
            )
        } catch (e: RuntimeException) {
            reasons.add("objective_error:${compact(e)}")
            null
        }

    private fun validateDeployment(
        deployment: LegacyRosterImporter.Deployment,
        actorStateRefs: LegacyActorStateScanner.ActorStateRefs?,
        context: PlannerContext,
        reasons: MutableList<String>,
    ): DeploymentIssueCounts {
        val collisions = deployment.units.size - deployment.units.distinctBy { it.x to it.y }.size
        val unknownHids = deployment.units.map { it.hid }.filterNot { it in context.heroes.hids }.toSet().size
        if (collisions > 0) reasons.add("deployment_collisions:$collisions")
        if (unknownHids > 0) reasons.add("unknown_deployment_hids:$unknownHids")
        return DeploymentIssueCounts(
            collisions = collisions,
            unknownHids = unknownHids,
            collisionGroups = collisionGroups(deployment, actorStateRefs),
        )
    }

    private fun collisionGroups(
        deployment: LegacyRosterImporter.Deployment,
        actorStateRefs: LegacyActorStateScanner.ActorStateRefs?,
    ): List<StageCollisionGroup> =
        deployment.traces
            .groupBy { it.unit.x to it.unit.y }
            .filterValues { traces -> traces.size > 1 }
            .map { (cell, traces) ->
                val units = traces.map(::collisionUnit)
                val scriptRefs = collisionScriptRefs(traces, actorStateRefs)
                val coverage = collisionScriptRefCoverage(units, scriptRefs)
                StageCollisionGroup(
                    x = cell.first,
                    y = cell.second,
                    units = units,
                    scriptRefs = scriptRefs,
                    scriptRefCoverage = coverage,
                    resolutionProposal = collisionResolutionProposal(units, scriptRefs, coverage),
                )
            }
            .sortedWith(compareBy(StageCollisionGroup::y, StageCollisionGroup::x))

    private fun collisionScriptRefCoverage(
        units: List<StageCollisionUnit>,
        scriptRefs: List<StageCollisionScriptRef>,
    ): StageCollisionScriptRefCoverage {
        val referencedHids = scriptRefs.map { it.hid }.distinct().sorted()
        val referenced = referencedHids.toHashSet()
        val unreferencedUnits = units.filter { it.hid !in referenced }
        return StageCollisionScriptRefCoverage(
            bucket = collisionScriptRefCoverageBucket(scriptRefs, unreferencedUnits),
            referencedHids = referencedHids,
            singleUnreferencedUnit = unreferencedUnits.singleOrNull()?.let(::collisionUnitKey),
        )
    }

    private fun collisionScriptRefCoverageBucket(
        scriptRefs: List<StageCollisionScriptRef>,
        unreferencedUnits: List<StageCollisionUnit>,
    ): String =
        when {
            scriptRefs.isEmpty() -> "no_script_refs"
            unreferencedUnits.isEmpty() -> "all_units_referenced"
            unreferencedUnits.size == 1 -> "one_unreferenced_unit"
            else -> "mixed_refs"
        }

    private fun collisionUnitKey(unit: StageCollisionUnit): StageCollisionUnitKey =
        StageCollisionUnitKey(
            side = unit.side,
            hid = unit.hid,
            slot = unit.slot,
        )

    private fun collisionResolutionProposal(
        units: List<StageCollisionUnit>,
        scriptRefs: List<StageCollisionScriptRef>,
        coverage: StageCollisionScriptRefCoverage,
    ): StageCollisionResolutionProposal? {
        val openingUnit = coverage.singleUnreferencedUnit ?: return null
        if (coverage.bucket != "one_unreferenced_unit" || scriptRefs.isEmpty()) return null
        val referencedHids = coverage.referencedHids.toHashSet()
        val deferredUnits = units.filter { it.hid in referencedHids }.map(::collisionUnitKey)
        if (deferredUnits.isEmpty()) return null
        return StageCollisionResolutionProposal(
            kind = "opening_unit_with_deferred_actor_state_refs",
            openingUnit = openingUnit,
            deferredUnits = deferredUnits,
        )
    }

    private fun collisionScriptRefs(
        traces: List<LegacyRosterImporter.RosterTrace>,
        actorStateRefs: LegacyActorStateScanner.ActorStateRefs?,
    ): List<StageCollisionScriptRef> {
        if (actorStateRefs == null) return emptyList()
        val hids = traces.mapTo(HashSet()) { it.unit.hid }
        return hids.flatMap { hid -> actorStateRefs.forActor(hid).map { ref -> hid to ref } }
            .sortedWith(compareBy({ it.first }, { actorStateOffset(it.second) }))
            .map { (hid, ref) -> collisionScriptRef(hid, ref) }
    }

    private fun actorStateOffset(ref: LegacyActorStateScanner.ActorStateRef): Int =
        when (ref) {
            is LegacyActorStateScanner.ActorStateRef.Visible -> ref.record.offset
            is LegacyActorStateScanner.ActorStateRef.ArmyChange -> ref.record.offset
        }

    private fun collisionScriptRef(
        hid: Int,
        ref: LegacyActorStateScanner.ActorStateRef,
    ): StageCollisionScriptRef =
        when (ref) {
            is LegacyActorStateScanner.ActorStateRef.Visible -> StageCollisionScriptRef(
                kind = "set_actor_visible",
                hid = hid,
                recordOffset = hexOffset(ref.record.offset),
                values = listOf(ref.record.mode, ref.record.argument),
            )
            is LegacyActorStateScanner.ActorStateRef.ArmyChange -> StageCollisionScriptRef(
                kind = "army_change",
                hid = hid,
                recordOffset = hexOffset(ref.record.offset),
                values = listOf(ref.record.state, ref.record.mode),
            )
        }

    private fun collisionUnit(trace: LegacyRosterImporter.RosterTrace): StageCollisionUnit {
        val unit = trace.unit
        return StageCollisionUnit(
            side = unit.side.name.lowercase(),
            hid = unit.hid,
            level = unit.level,
            slot = trace.slot,
            recordOffset = hexOffset(trace.recordOffset),
            rawWords = trace.rawWords,
        )
    }

    private fun hexOffset(offset: Int): String = "0x${offset.toString(16)}"

    private fun LegacyStagePlanRow.toMigrationRow(
        scriptName: String,
        mapName: String,
        probe: StageProbe,
        reasons: List<String>,
    ): StageMigrationRow {
        val enemyUnits = probe.deployment?.units.orEmpty().count { it.side == LegacyRosterImporter.Side.ENEMY }
        val friendUnits = probe.deployment?.units.orEmpty().count { it.side == LegacyRosterImporter.Side.FRIEND }
        val collisionGroups = probe.deploymentIssues.collisionGroups
        return StageMigrationRow(
            stage = StageIdentity(gkid = stageId, name = name, stageMode = stageMode),
            assets = StageAssets(scriptFile = scriptName, mapFile = mapName, mapBinding = MAP_BINDING),
            map = probe.map?.let { StageMapProbe(mapWidth = it.size.width, mapHeight = it.size.height) },
            deployment = StageDeploymentProbe(
                deploymentUnits = probe.deployment?.units?.size ?: 0,
                enemyUnits = enemyUnits,
                friendUnits = friendUnits,
                reinforcementRecords = probe.deployment?.reinforcementRecords ?: 0,
                collisions = probe.deploymentIssues.collisions,
                unknownHids = probe.deploymentIssues.unknownHids,
            ),
            objectives = StageObjectiveProbe(
                objectiveWin = probe.objectives?.win?.size ?: 0,
                objectiveLose = probe.objectives?.lose?.size ?: 0,
                objectiveUnsupported = probe.objectives?.unsupported?.size ?: 0,
                objectiveOutOfRoster = probe.objectives?.outOfRoster?.size ?: 0,
            ),
            diagnostics = StageDiagnostics(
                status = if (reasons.isEmpty()) READY else BLOCKED,
                reasons = reasons,
                collisionGroups = collisionGroups,
                collisionResolutionPreview = collisionResolutionPreview(reasons, collisionGroups),
                trialAssembly = probe.trialAssembly,
            ),
        )
    }

    private fun collisionResolutionPreview(
        reasons: List<String>,
        groups: List<StageCollisionGroup>,
    ): StageCollisionResolutionPreview? {
        if (groups.none { it.resolutionProposal != null }) return null
        val unresolvedGroups = groups.count { it.resolutionProposal == null }
        val remainingReasons = if (unresolvedGroups == 0) {
            reasons.filterNot { it.startsWith("deployment_collisions:") }
        } else {
            reasons
        }
        return StageCollisionResolutionPreview(
            statusAfterProposals = if (remainingReasons.isEmpty()) READY else BLOCKED,
            remainingReasons = remainingReasons,
            proposedGroups = groups.size - unresolvedGroups,
            unresolvedGroups = unresolvedGroups,
        )
    }

    private fun trialAssembly(
        row: LegacyStagePlanRow,
        mapName: String,
        probe: StageProbe,
        context: PlannerContext,
        reasons: List<String>,
    ): StageTrialAssemblyProbe? {
        val preview = collisionResolutionPreview(reasons, probe.deploymentIssues.collisionGroups) ?: return null
        if (preview.statusAfterProposals != READY) return null
        val deployment = probe.deployment ?: return null
        val trial = proposedTrialDeployments(deployment, probe.deploymentIssues.collisionGroups)
        return runCatching {
            assembleTrial(row, mapName, probe, context, trial)
        }.getOrElse { error ->
            StageTrialAssemblyProbe(
                status = BLOCKED,
                reasons = listOf("trial_assembly_error:${compact(error)}"),
                openingUnits = trial.opening.size,
                deferredUnits = trial.deferred.size,
            )
        }
    }

    private fun assembleTrial(
        row: LegacyStagePlanRow,
        mapName: String,
        probe: StageProbe,
        context: PlannerContext,
        trial: StageTrialDeployments,
    ): StageTrialAssemblyProbe {
        val battleId = stageBattleId(row.stageId)
        val mapId = "legacy_stage_${row.stageId}_map"
        val content = LegacyBattleBuilder.loadOnMap(
            meta = PackMeta(battleId, "0.0.0-trial", SOURCE_MOD, battleId),
            sources = trialSources(context.jsonDir),
            terrainMapJson = read(File(context.terrainDir, mapName)),
            spec = MapBattleSpec(
                battleId = battleId,
                mapId = mapId,
                protect = trial.opening.first().unit,
                placements = trial.opening,
                win = probe.objectives?.win?.ifEmpty { null },
                lose = probe.objectives?.lose?.ifEmpty { null },
            ),
            deferredPlacements = trial.deferred,
        )
        val validationIssues = ContentValidator.validate(content)
        if (validationIssues.isNotEmpty()) {
            return trialBlocked(validationIssues, trial)
        }
        CampaignAssembler.assemble(content, battleId, mapId)
        return StageTrialAssemblyProbe(
            status = READY,
            openingUnits = trial.opening.size,
            deferredUnits = trial.deferred.size,
        )
    }

    private fun trialBlocked(
        issues: List<com.ccz.contentpack.ValidationIssue>,
        trial: StageTrialDeployments,
    ): StageTrialAssemblyProbe =
        StageTrialAssemblyProbe(
            status = BLOCKED,
            reasons = issues.take(TRIAL_ISSUE_LIMIT).map { issue ->
                "content_validation:${issue.path}:${issue.message}"
            },
            openingUnits = trial.opening.size,
            deferredUnits = trial.deferred.size,
        )

    private fun proposedTrialDeployments(
        deployment: LegacyRosterImporter.Deployment,
        groups: List<StageCollisionGroup>,
    ): StageTrialDeployments {
        val deferredKeys = groups.mapNotNull { it.resolutionProposal }
            .flatMap { it.deferredUnits }
            .toHashSet()
        val opening = ArrayList<Placement>()
        val deferred = ArrayList<DeferredPlacement>()
        deployment.traces.forEach { trace ->
            val placement = placement(trace)
            if (trace.key() in deferredKeys) {
                deferred += DeferredPlacement(placement)
            } else {
                opening += placement
            }
        }
        return StageTrialDeployments(opening, deferred)
    }

    private fun placement(trace: LegacyRosterImporter.RosterTrace): Placement {
        val unit = trace.unit
        return Placement(
            unit = "$HERO_PREFIX${unit.hid}",
            x = unit.x,
            y = unit.y,
            level = maxOf(1, unit.level),
            faction = if (unit.side == LegacyRosterImporter.Side.ENEMY) {
                LegacyBattleBuilder.ENEMY_FACTION
            } else {
                LegacyBattleBuilder.ALLY_FACTION
            },
        )
    }

    private fun LegacyRosterImporter.RosterTrace.key(): StageCollisionUnitKey =
        StageCollisionUnitKey(
            side = unit.side.name.lowercase(),
            hid = unit.hid,
            slot = slot,
        )

    private fun trialSources(jsonDir: File): LegacyTableSources =
        LegacyTableSources(
            dicJob = read(File(jsonDir, "dic_job.json")),
            dicSkill = read(File(jsonDir, "dic_skill.json")),
            dicHero = read(File(jsonDir, "dic_hero.json")),
            mapTerrain = read(File(jsonDir, "map_terrain.json")),
            dicJobWalk = optionalRead(File(jsonDir, "dic_jobWalk.json")),
            dicJobTerrain = optionalRead(File(jsonDir, "dic_jobTerrain.json")),
        )

    private fun optionalRead(file: File): String =
        if (file.isFile) file.readText(Charsets.UTF_8).removePrefix("\uFEFF") else ""

    private fun stageBattleId(stageId: Int): String = "legacy_stage_$stageId"

    private fun totals(stages: List<StageMigrationRow>): StageMigrationTotals =
        StageMigrationTotals(
            stages = StageStatusTotals(
                stageRows = stages.size,
                ready = stages.count { it.diagnostics.status == READY },
                blocked = stages.count { it.diagnostics.status == BLOCKED },
            ),
            diagnostics = StageDiagnosticTotals(
                missingScript = stages.countReason("missing_script"),
                missingMap = stages.countReason("missing_map"),
                mapErrors = stages.countReasonPrefix("map_error:"),
                deploymentErrors = stages.countReasonPrefix("deployment_error:"),
                emptyDeployment = stages.countReason("empty_deployment"),
                objectiveErrors = stages.countReasonPrefix("objective_error:"),
            ),
            objectives = StageObjectiveTotals(
                unsupported = stages.sumOf { it.objectives.objectiveUnsupported },
            ),
            deployment = StageDeploymentTotals(
                reinforcementRecords = stages.sumOf { it.deployment.reinforcementRecords },
                units = stages.sumOf { it.deployment.deploymentUnits },
                collisions = stages.sumOf { it.deployment.collisions },
                unknownHids = stages.sumOf { it.deployment.unknownHids },
                collisionGroups = collisionEvidenceTotals(stages),
            ),
        )

    private fun collisionEvidenceTotals(stages: List<StageMigrationRow>): StageCollisionEvidenceTotals {
        val groups = stages.flatMap { it.diagnostics.collisionGroups }
        return StageCollisionEvidenceTotals(
            groups = groups.size,
            units = groups.sumOf { it.units.size },
            groupsWithScriptRefs = groups.count { it.scriptRefs.isNotEmpty() },
            scriptRefRows = groups.sumOf { it.scriptRefs.size },
            coverage = collisionCoverageTotals(groups),
            resolution = collisionResolutionTotals(stages, groups),
        )
    }

    private fun collisionResolutionTotals(
        stages: List<StageMigrationRow>,
        groups: List<StageCollisionGroup>,
    ): StageCollisionResolutionTotals {
        val proposals = groups.mapNotNull { it.resolutionProposal }
        val collisionStages = stages.filter { it.diagnostics.collisionGroups.isNotEmpty() }
        return StageCollisionResolutionTotals(
            groupsWithProposals = proposals.size,
            stagesWithProposals = stages.count { stage ->
                stage.diagnostics.collisionGroups.any { it.resolutionProposal != null }
            },
            stagesWithOnlyProposedCollisionGroups = collisionStages.count { stage ->
                stage.diagnostics.collisionGroups.all { it.resolutionProposal != null }
            },
            stagesReadyIfProposalsApplied = stages.count { stage ->
                stage.diagnostics.collisionResolutionPreview?.statusAfterProposals == READY
            },
            openingUnits = proposals.size,
            deferredUnits = proposals.sumOf { it.deferredUnits.size },
        )
    }

    private fun collisionCoverageTotals(groups: List<StageCollisionGroup>): StageCollisionCoverageTotals =
        StageCollisionCoverageTotals(
            groupsWithoutRefs = groups.count { it.scriptRefs.isEmpty() },
            groupsAllUnitsReferenced = groups.count(::allCollisionUnitsReferenced),
            groupsOneUnreferencedUnit = groups.count { group -> unreferencedCollisionUnits(group) == 1 },
            groupsWithUnreferencedUnits = groups.count { group ->
                group.scriptRefs.isNotEmpty() && unreferencedCollisionUnits(group) > 0
            },
            groupsMixedRefs = groups.count { group ->
                group.scriptRefs.isNotEmpty() && unreferencedCollisionUnits(group) > 1
            },
        )

    private fun allCollisionUnitsReferenced(group: StageCollisionGroup): Boolean =
        group.scriptRefs.isNotEmpty() && unreferencedCollisionUnits(group) == 0

    private fun unreferencedCollisionUnits(group: StageCollisionGroup): Int {
        val referencedHids = group.scriptRefs.mapTo(HashSet()) { it.hid }
        return group.units.count { it.hid !in referencedHids }
    }

    private fun readStageRows(jsonDir: File): List<LegacyStagePlanRow> {
        val rows = readRows(jsonDir, "dic_gk.json", LegacyStagePlanRow.serializer())
        val seen = HashSet<Int>()
        rows.forEach { row ->
            if (!seen.add(row.stageId)) throw LegacyCommerceException("duplicate dic_gk.gkid: ${row.stageId}")
        }
        return rows
    }

    private fun readHeroIndex(jsonDir: File): HeroIndex {
        val rows = readRows(jsonDir, "dic_hero.json", LegacyHeroPlanRow.serializer())
            .filter { it.heroId > 0 }
        return HeroIndex(
            nameToId = rows.filter { it.name.isNotBlank() }.associate { it.name to "$HERO_PREFIX${it.heroId}" },
            hids = rows.mapTo(HashSet()) { it.heroId },
        )
    }

    private fun detectOpcodeProfile(sceneDir: File, terrainDir: File): LegacyEexOpcodeProfile {
        val script = File(sceneDir, scriptName(1))
        val map = File(terrainDir, mapName(1))
        if (!script.isFile || !map.isFile) return LegacyEexOpcodeProfile.LEGACY_DECODED
        val packMap = try {
            LegacyMapMapper.mapMap(read(map), id = "legacy_profile_probe_map")
        } catch (_: RuntimeException) {
            return LegacyEexOpcodeProfile.LEGACY_DECODED
        }
        return LegacyRosterImporter.detectOpcodeProfile(script.readBytes(), packMap.size.width, packMap.size.height)
    }

    private fun <T> readRows(dir: File, name: String, serializer: kotlinx.serialization.KSerializer<T>): List<T> =
        reader.decodeFromString(ListSerializer(serializer), read(File(dir, name)))

    private fun read(file: File): String {
        if (!file.isFile) throw LegacyCommerceException("missing legacy file: ${file.path}")
        return file.readText(Charsets.UTF_8).removePrefix("\uFEFF")
    }

    private fun scriptName(stageId: Int): String =
        "S_${(stageId - 1).toString().padStart(2, '0')}.eex_new"

    private fun mapName(stageId: Int): String = "terrainMap_$stageId.json"

    private fun compact(error: Throwable): String =
        (error.message ?: error::class.simpleName ?: "error").lineSequence().first().take(160)

    private fun List<StageMigrationRow>.countReason(reason: String): Int =
        count { row -> reason in row.diagnostics.reasons }

    private fun List<StageMigrationRow>.countReasonPrefix(prefix: String): Int =
        count { row -> row.diagnostics.reasons.any { it.startsWith(prefix) } }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2 && args[0].isNotBlank() && args[1].isNotBlank()) {
            "usage: <extractedRoot> <outPath>"
        }
        File(args[1]).apply { parentFile?.mkdirs() }.writeText(planJson(args[0]), Charsets.UTF_8)
    }
}

private data class PlannerContext(
    val jsonDir: File,
    val sceneDir: File,
    val terrainDir: File,
    val heroes: HeroIndex,
    val opcodeProfile: LegacyEexOpcodeProfile,
)

private data class HeroIndex(
    val nameToId: Map<String, String>,
    val hids: Set<Int>,
)

private data class StageProbe(
    val map: PackMap?,
    val deployment: LegacyRosterImporter.Deployment?,
    val objectives: LegacyObjectiveImporter.ImportedObjectives?,
    val deploymentIssues: DeploymentIssueCounts,
    val trialAssembly: StageTrialAssemblyProbe? = null,
)

private data class DeploymentIssueCounts(
    val collisions: Int = 0,
    val unknownHids: Int = 0,
    val collisionGroups: List<StageCollisionGroup> = emptyList(),
)

private data class StageTrialDeployments(
    val opening: List<Placement>,
    val deferred: List<DeferredPlacement>,
)

@Serializable
private data class LegacyStagePlanRow(
    @SerialName("gkid") val stageId: Int,
    @SerialName("gkname") val name: String,
    @SerialName("stageMode") val stageMode: String = "",
)

@Serializable
private data class LegacyHeroPlanRow(
    @SerialName("hid") val heroId: Int,
    val name: String = "",
)
