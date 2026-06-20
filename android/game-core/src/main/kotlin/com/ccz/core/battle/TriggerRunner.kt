package com.ccz.core.battle

import com.ccz.core.event.SScript

/**
 * Runs an S-script's mid-battle triggers and ties them to win/lose settlement.
 * Triggers are evaluated in declaration order (deterministic); a `once` trigger
 * fires at most once (tracked in [BattleProgress.firedTriggers]). Pure — no RNG.
 *
 * The battle loop calls [applyPre] at battle start, [tick] after each resolved
 * command, and [applyPost] at battle end. [tick] fires eligible triggers, then
 * settles win/lose via [WinLose] so trigger-driven force_win / kills end the battle.
 */
object TriggerRunner {
    fun applyPre(state: BattleState, script: SScript, ctx: ScriptContext): Resolution =
        BattleOps.applyOps(state, script.pre, ctx)

    fun applyPost(state: BattleState, script: SScript, ctx: ScriptContext): Resolution =
        BattleOps.applyOps(state, script.post, ctx)

    fun tick(state: BattleState, script: SScript, ctx: ScriptContext): Resolution {
        val fired = fireTriggers(state, script, ctx)
        val settled = WinLose.settle(fired.state, script)
        return Resolution(settled.state, fired.events + settled.events)
    }

    private fun fireTriggers(state: BattleState, script: SScript, ctx: ScriptContext): Resolution {
        var current = state
        val events = mutableListOf<Event>()
        for (trigger in script.mid) {
            if (trigger.once && current.hasFired(trigger.id)) continue
            if (!TriggerConditions.met(current, trigger.whenCondition)) continue
            if (trigger.once) current = current.markFired(trigger.id) // only once-triggers need tracking
            val resolution = BattleOps.applyOps(current, trigger.actions, ctx)
            current = resolution.state
            events += resolution.events
        }
        return Resolution(current, events)
    }
}
