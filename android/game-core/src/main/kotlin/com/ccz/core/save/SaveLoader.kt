package com.ccz.core.save

import com.ccz.core.battle.BattleRules
import com.ccz.core.battle.BattleState
import com.ccz.core.battle.Command
import com.ccz.core.battle.ResolveContext
import com.ccz.core.battle.Resolver
import com.ccz.core.event.RScript
import com.ccz.core.model.Skill

/**
 * Loads a [SaveEnvelope] by validating its version axes fail-closed, then replaying
 * the recorded command sequence through the [Resolver] from the initial state.
 *
 * Three load gates (CCZ_ENGINE_RULES §Save/Replay), four reject reasons: a version check
 * (rejecting a FUTURE save schema this build cannot understand, or a rules-version mismatch
 * — the save was produced under different battle-formula rules, so a replay would diverge,
 * 宁可拒绝也不破坏回放) then a command-integrity check (a corrupt command referencing a
 * unit/skill absent from the initial state or skill table) then a scenario-integrity check
 * (a recorded cutscene naming a script absent from the loaded content). On-disk shape/enum
 * decoding — including the units-map roster coherence — is a separate, earlier concern in
 * [SaveCodec]. Replay re-applies already-accepted commands directly, exactly like the live
 * flow's accepted path, so it consumes RNG identically and is deterministic.
 *
 * Commands were accepted when recorded, so a well-formed envelope always resolves; a
 * tampered/corrupt envelope is caught fail-closed by [commandIntegrity] BEFORE replay,
 * yielding a clean CORRUPT_COMMAND rejection rather than a mid-replay exception. The
 * version gates take precedence (checked first).
 */
object SaveLoader {
    sealed interface Outcome {
        data class Loaded(val finalState: BattleState) : Outcome
        data class Rejected(val reason: SaveRejection) : Outcome
    }

    fun load(
        envelope: SaveEnvelope,
        resolve: ResolveContext,
        scripts: Map<String, RScript> = emptyMap(),
    ): Outcome {
        val rejection = check(envelope.versions)
            ?: commandIntegrity(envelope, resolve.skills)
            ?: scenarioIntegrity(envelope, scripts)
        return if (rejection != null) {
            Outcome.Rejected(rejection)
        } else {
            Outcome.Loaded(replay(envelope, resolve))
        }
    }

    fun check(versions: SaveVersions): SaveRejection? = when {
        versions.saveSchemaVersion > SaveVersions.SUPPORTED_SAVE_SCHEMA_VERSION -> SaveRejection.FUTURE_SCHEMA_VERSION
        versions.rulesVersion != BattleRules.RULES_VERSION -> SaveRejection.RULES_VERSION_MISMATCH
        else -> null
    }

    /**
     * Fail-closed check that every command resolves against the initial roster and skill
     * table — a tampered envelope referencing an absent unit/skill is rejected cleanly
     * rather than crashing the replay fold. Commands neither add nor remove units (spawn /
     * remove are battle-script ops, not commands), and [replay] folds commands through the
     * Resolver only (no triggers), so the initial roster is the complete reference set. If a
     * future battle loop ever records commands targeting trigger-spawned units, those ids
     * must be folded into the reference set here.
     */
    private fun commandIntegrity(envelope: SaveEnvelope, skills: Map<String, Skill>): SaveRejection? {
        val unitIds = envelope.initialState.units.keys
        val corrupt = envelope.commands.any { command ->
            when (command) {
                is Command.Move -> command.unit !in unitIds
                is Command.Attack ->
                    command.attacker !in unitIds || command.target !in unitIds || command.skill !in skills
                is Command.Wait -> command.unit !in unitIds
                is Command.EndTurn -> false
            }
        }
        return if (corrupt) SaveRejection.CORRUPT_COMMAND else null
    }

    /**
     * Fail-closed counterpart to [commandIntegrity] for the second (cutscene) replay axis:
     * every recorded [ScenarioReplay.scriptId] must resolve to a script in the current
     * content, mirroring how commandIntegrity demands its unit/skill references resolve. A
     * save pointing at a script absent from the loaded pack is rejected cleanly here rather
     * than dangling. This is the cheap reference pre-check; the deeper "recorded choices no
     * longer complete a content-drifted script" case (ScenarioRejection.INCOMPLETE_REPLAY)
     * is decided by [ScenarioReplayer] when the battle driver actually re-runs the cutscene.
     * With no scripts supplied (the default), any recorded scenario is unverifiable → rejected.
     */
    private fun scenarioIntegrity(envelope: SaveEnvelope, scripts: Map<String, RScript>): SaveRejection? =
        if (envelope.scenarios.any { it.scriptId !in scripts }) SaveRejection.CORRUPT_SCENARIO else null

    private fun replay(envelope: SaveEnvelope, resolve: ResolveContext): BattleState {
        var state = envelope.initialState
        envelope.commands.forEach { command ->
            state = Resolver.apply(state, command, resolve).state
        }
        return state
    }
}
