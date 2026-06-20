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
        /** The newest save schema this build can read. A future (higher) value is rejected. */
        const val SUPPORTED_SAVE_SCHEMA_VERSION = 1
    }
}

/**
 * A self-contained replay/save: the initial battle state (which carries the RNG state)
 * plus the ordered, already-accepted command sequence. Replaying = folding [commands]
 * through the resolver from [initialState]; see CCZ_ENGINE_RULES: Replay = initial state
 * + command sequence.
 */
data class SaveEnvelope(
    val versions: SaveVersions,
    val initialState: BattleState,
    val commands: List<Command>,
)

/** Why a save cannot be loaded. `null` from [SaveLoader.check] means version-loadable. */
enum class SaveRejection { FUTURE_SCHEMA_VERSION, RULES_VERSION_MISMATCH, CORRUPT_COMMAND }
