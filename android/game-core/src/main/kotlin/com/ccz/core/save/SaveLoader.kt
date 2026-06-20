package com.ccz.core.save

import com.ccz.core.battle.BattleRules
import com.ccz.core.battle.BattleState
import com.ccz.core.battle.Resolver
import com.ccz.core.model.Skill
import com.ccz.core.model.UnitClass

/**
 * Loads a [SaveEnvelope] by validating its version axes fail-closed, then replaying
 * the recorded command sequence through the [Resolver] from the initial state.
 *
 * Two reject gates (CCZ_ENGINE_RULES §Save/Replay): a FUTURE save schema version this
 * build cannot understand, and a rules-version mismatch (the save was produced under
 * different battle-formula rules, so a replay would diverge — 宁可拒绝也不破坏回放).
 * Replay re-applies already-accepted commands directly, exactly like the live flow's
 * accepted path, so it consumes RNG identically and is deterministic.
 *
 * Replay assumes the envelope's commands are well-formed (they were accepted when
 * recorded). A corrupted envelope whose command references a unit/skill absent from
 * the initial state or skill table currently surfaces as an exception, not a clean
 * rejection; graceful command-integrity validation is deferred until save
 * serialization lands (there is no on-disk save codec yet).
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
        val rejection = check(envelope.versions)
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
