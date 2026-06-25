package com.ccz.core.save

import com.ccz.core.battle.BattleState
import com.ccz.core.battle.Command

/**
 * The independent version axes a save/replay must record (CCZ_ENGINE_RULES: 版本独立).
 * Bundled into one value object so [SaveEnvelope] stays within its parameter budget.
 * [rulesVersion] mirrors BattleRules.RULES_VERSION so a replay made under different
 * battle-formula rules can be detected rather than silently diverging.
 */
data class SaveVersions(
    val saveSchemaVersion: Int,
    val rulesVersion: Int,
    val engineVersion: String,
    val nativeFormatVersion: String,
    val contentVersion: String,
    val converterVersion: String? = null,
) {
    companion object {
        /**
         * The newest save schema this build can read; a future (higher) value is rejected.
         * v2 added the scenario (cutscene) replay axis ([SaveEnvelope.scenarios]); v3 added the
         * `cast` command variant (ADR 0008 skill effects). Older saves are forward-compatible — they
         * simply lack the new key/command kind and decode fine. A newer save is also refused by an
         * older build, fail-closed, but note WHERE the refusal happens: a v3 save that actually carries
         * a `cast` command throws [SaveDecodeException] during the strict single-pass decode (the older
         * build's [com.ccz.core.battle.Command] / CommandDto sealed set has no `cast` subtype, so the
         * discriminator is unknown) — this is BEFORE [SaveLoader.check]'s FUTURE_SCHEMA_VERSION gate,
         * which only ever sees an already-decoded envelope. So the version gate fires only for a newer
         * save that introduces no unknown discriminator; a new-command save is caught at decode instead.
         * Either way the outcome is a clean refusal, never a silent mis-load. (Clean FUTURE-version
         * CLASSIFICATION for new-command saves would require peeking the version before full decode — a
         * deliberate non-goal here, since both refusal paths are already safe.)
         */
        const val SUPPORTED_SAVE_SCHEMA_VERSION = 3
    }
}

/**
 * A replayable R-script (cutscene) run recorded alongside a battle: which script was
 * played ([scriptId], resolved against content) and the ordered player [choices] driving
 * its scenario choices. Replaying = `ScenarioRunner.run(script, vars, choices)`; the
 * script body itself lives in content (referenced by [SaveVersions.contentVersion]), not
 * in the save.
 */
data class ScenarioReplay(
    val scriptId: String,
    val choices: List<Int> = emptyList(),
)

/**
 * A self-contained replay/save: the initial battle state (which carries the RNG state),
 * the ordered already-accepted command sequence, and any cutscene [scenarios] played
 * (the second, R-script replay axis, save schema v2+). Replaying = folding [commands]
 * through the resolver from [initialState] and re-running each scenario's choices; see
 * CCZ_ENGINE_RULES: Replay = initial state + command sequence.
 */
data class SaveEnvelope(
    val versions: SaveVersions,
    val initialState: BattleState,
    val commands: List<Command>,
    val scenarios: List<ScenarioReplay> = emptyList(),
)

/** Why a save cannot be loaded. `null` from [SaveLoader.check] means version-loadable. */
enum class SaveRejection { FUTURE_SCHEMA_VERSION, RULES_VERSION_MISMATCH, CORRUPT_COMMAND, CORRUPT_SCENARIO }
