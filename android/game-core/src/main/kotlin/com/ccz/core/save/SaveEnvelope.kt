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
         * v2 added the scenario (cutscene) replay axis ([SaveEnvelope.scenarios]); a v1 save
         * simply omits the field and decodes with an empty list (forward-compatible).
         */
        const val SUPPORTED_SAVE_SCHEMA_VERSION = 2
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
enum class SaveRejection { FUTURE_SCHEMA_VERSION, RULES_VERSION_MISMATCH, CORRUPT_COMMAND }
