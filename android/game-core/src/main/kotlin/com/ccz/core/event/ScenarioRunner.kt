package com.ccz.core.event

/**
 * Deterministic R-script (cutscene) interpreter. Walks an [RScript]'s ops with a
 * program counter: control-flow ops ([ScenarioOp.Label] / [ScenarioOp.SetVar] /
 * [ScenarioOp.Branch]) are consumed to evolve the variable map and jump target,
 * while presentation ops (Dialogue / Portrait / Wait / SceneTransition / PlayBgm /
 * FadeIn / FadeOut) are emitted in execution order for the view layer. Pure and
 * deterministic — no RNG, integer vars only (an unset variable reads 0, matching
 * battle-side `BattleProgress.vars`).
 *
 * A [ScenarioOp.Choice] is resolved by consuming the next index from [run]'s
 * `choices` — the replayable player-input axis: each choice picks an option, applies
 * its `setVars`, and jumps to its `goto` label (or falls through). When `choices`
 * runs out (or names an out-of-range option) the run stops at that choice, surfaced
 * via [Playback.pausedAt], so `(script, vars, choices)` fully and deterministically
 * determines the output. A step budget guards against branch loops authored into
 * content — when exceeded the run halts fail-safe ([Playback.haltedOnBudget]) instead
 * of looping forever. Branch/choice targets are assumed resolvable
 * (ContentEventValidator checks R-script label references); an unresolvable target is
 * treated as "no jump" rather than a crash.
 */
object ScenarioRunner {
    /** Per-op step ceiling; bounds branch back-jumps without forbidding legitimate ones. */
    private const val STEPS_PER_OP = 100

    data class Playback(
        val events: List<ScenarioOp>,
        val vars: Map<String, Int>,
        val pausedAt: ScenarioOp.Choice? = null,
        val haltedOnBudget: Boolean = false,
    )

    fun run(
        script: RScript,
        vars: Map<String, Int> = emptyMap(),
        choices: List<Int> = emptyList(),
    ): Playback {
        val labels = labelIndex(script.ops)
        val events = mutableListOf<ScenarioOp>()
        val state = vars.toMutableMap()
        var budget = script.ops.size.toLong() * STEPS_PER_OP + 1
        var pc = 0
        var choiceCursor = 0
        while (pc in script.ops.indices) {
            if (budget-- <= 0) return Playback(events, state, haltedOnBudget = true)
            when (val op = script.ops[pc]) {
                is ScenarioOp.Label -> pc++
                is ScenarioOp.SetVar -> { state[op.name] = op.value; pc++ }
                is ScenarioOp.Branch -> pc = jump(branchTaken(op, state), op.target, labels, pc)
                is ScenarioOp.Choice -> {
                    val option = chosenOption(op, choices, choiceCursor)
                        ?: return Playback(events, state, pausedAt = op)
                    state.putAll(option.setVars)
                    choiceCursor++
                    pc = jump(taken = true, option.goto, labels, pc)
                }
                is ScenarioOp.Dialogue,
                is ScenarioOp.Portrait,
                is ScenarioOp.Wait,
                is ScenarioOp.SceneTransition,
                is ScenarioOp.PlayBgm,
                ScenarioOp.FadeIn,
                ScenarioOp.FadeOut,
                -> { events += op; pc++ }
            }
        }
        return Playback(events, state)
    }

    /** Branch condition: an unset variable reads 0 (matches battle-side `BattleProgress.varValue`). */
    private fun branchTaken(op: ScenarioOp.Branch, state: Map<String, Int>): Boolean =
        (state[op.variable] ?: 0) == op.equals

    /** Next pc for a (possibly null) jump target: resolve the label when [taken], else fall through. */
    private fun jump(taken: Boolean, target: String?, labels: Map<String, Int>, pc: Int): Int =
        if (taken && target != null) labels[target] ?: (pc + 1) else pc + 1

    /** The option a choice takes from the replay [choices] at [cursor], or null to pause (exhausted / out of range). */
    private fun chosenOption(op: ScenarioOp.Choice, choices: List<Int>, cursor: Int): ChoiceOption? =
        choices.getOrNull(cursor)?.let { op.options.getOrNull(it) }

    private fun labelIndex(ops: List<ScenarioOp>): Map<String, Int> =
        ops.withIndex()
            .mapNotNull { (index, op) -> (op as? ScenarioOp.Label)?.let { it.name to index } }
            .toMap()
}
