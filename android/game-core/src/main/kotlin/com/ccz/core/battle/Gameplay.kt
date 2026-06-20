package com.ccz.core.battle

/**
 * Gameplay-layer entry point. Validates a command against the authoritative state
 * before letting [Resolver] mutate anything: illegal commands are rejected
 * deterministically and never reach the resolver, so they consume no RNG and
 * leave state untouched. This is the only path the app / battle loop should use
 * to submit commands; replay re-applies already-accepted commands through
 * [Resolver] directly. See ARCHITECTURE: Gameplay = command validation.
 */
object Gameplay {
    sealed interface Outcome {
        data class Accepted(val resolution: Resolution) : Outcome
        data class Rejected(val reason: RejectReason) : Outcome
    }

    fun submit(state: BattleState, command: Command, context: BattleContext): Outcome {
        val reason = CommandValidator.check(state, command, context)
        return if (reason != null) {
            Outcome.Rejected(reason)
        } else {
            Outcome.Accepted(Resolver.apply(state, command, context.classes, context.skills, context.rules))
        }
    }
}
