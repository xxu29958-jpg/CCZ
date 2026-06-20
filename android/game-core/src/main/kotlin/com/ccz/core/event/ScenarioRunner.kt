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
 * Execution stops at the first [ScenarioOp.Choice]: player input is a separate,
 * replayable step handled by a later slice, so the choice is surfaced via
 * [Playback.pausedAt] and ops after it are not run. A step budget guards against
 * branch loops authored into content — when exceeded the run halts fail-safe
 * ([Playback.haltedOnBudget]) instead of looping forever. Branch/choice targets are
 * assumed resolvable (ContentEventValidator checks R-script label references); an
 * unresolvable target is treated as "no jump" rather than a crash.
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

    fun run(script: RScript, vars: Map<String, Int> = emptyMap()): Playback {
        val labels = labelIndex(script.ops)
        val events = mutableListOf<ScenarioOp>()
        val state = vars.toMutableMap()
        var budget = script.ops.size.toLong() * STEPS_PER_OP + 1
        var pc = 0
        while (pc in script.ops.indices) {
            if (budget-- <= 0) return Playback(events, state, haltedOnBudget = true)
            when (val op = script.ops[pc]) {
                is ScenarioOp.Label -> pc++
                is ScenarioOp.SetVar -> { state[op.name] = op.value; pc++ }
                is ScenarioOp.Branch ->
                    pc = if ((state[op.variable] ?: 0) == op.equals) labels[op.target] ?: (pc + 1) else pc + 1
                is ScenarioOp.Choice -> return Playback(events, state, pausedAt = op)
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

    private fun labelIndex(ops: List<ScenarioOp>): Map<String, Int> =
        ops.withIndex()
            .mapNotNull { (index, op) -> (op as? ScenarioOp.Label)?.let { it.name to index } }
            .toMap()
}
