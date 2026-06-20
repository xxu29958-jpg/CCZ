package com.ccz.core.save

import com.ccz.core.battle.BattleRules
import com.ccz.core.battle.BattleState
import com.ccz.core.battle.Command
import com.ccz.core.battle.Resolver
import com.ccz.core.model.Skill
import com.ccz.core.model.UnitClass

/**
 * Loads a [SaveEnvelope] by validating its version axes fail-closed, then replaying
 * the recorded command sequence through the [Resolver] from the initial state.
 *
 * Three reject gates (CCZ_ENGINE_RULES §Save/Replay): a FUTURE save schema version this
 * build cannot understand, a rules-version mismatch (the save was produced under
 * different battle-formula rules, so a replay would diverge — 宁可拒绝也不破坏回放), and a
 * corrupt command referencing a unit/skill absent from the initial state or skill table.
 * Replay re-applies already-accepted commands directly, exactly like the live flow's
 * accepted path, so it consumes RNG identically and is deterministic.
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
        classes: Map<String, UnitClass>,
        skills: Map<String, Skill> = Resolver.DEMO_SKILLS,
        rules: BattleRules = BattleRules.DEFAULT,
    ): Outcome {
        val rejection = check(envelope.versions) ?: commandIntegrity(envelope, skills)
        return if (rejection != null) {
            Outcome.Rejected(rejection)
        } else {
            Outcome.Loaded(replay(envelope, classes, skills, rules))
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
                is Command.EndTurn -> false
            }
        }
        return if (corrupt) SaveRejection.CORRUPT_COMMAND else null
    }

    private fun replay(
        envelope: SaveEnvelope,
        classes: Map<String, UnitClass>,
        skills: Map<String, Skill>,
        rules: BattleRules,
    ): BattleState {
        var state = envelope.initialState
        envelope.commands.forEach { command ->
            state = Resolver.apply(state, command, classes, skills, rules).state
        }
        return state
    }
}
