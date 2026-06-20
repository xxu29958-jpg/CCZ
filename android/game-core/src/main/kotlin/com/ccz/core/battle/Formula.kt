package com.ccz.core.battle

import com.ccz.core.model.CombatRates
import com.ccz.core.model.CounterRelation
import com.ccz.core.rng.Rng
import kotlin.math.max

enum class Rounding { TRUNCATE, CEIL, ROUND }

data class BattleRules(
    val rounding: Rounding = Rounding.TRUNCATE,
    val damage: DamageRuleSet = DamageRuleSet(),
) {
    companion object {
        /**
         * Bump whenever a battle formula constant (DamageRuleSet / CounterRuleSet /
         * Rounding default) or the RNG consumption order changes. GoldenReplayTest is
         * the tripwire: a changed golden means rules changed, so bump this. Replay/save
         * envelopes record it so cross-version replays can detect rule drift.
         */
        const val RULES_VERSION = 1

        val DEFAULT = BattleRules()
    }
}

data class DamageRuleSet(
    val chipPermille: Int = 50,
    val critPct: Int = 130,
    val comboCoeffPct: Int = 50,
    val counter: CounterRuleSet = CounterRuleSet(),
    val blockReducePct: Int = 70,
)

data class CounterRuleSet(
    val favorPct: Int = 130,
    val unfavorPct: Int = 75,
)

private fun mulDiv(value: Long, numerator: Int, denominator: Int, rounding: Rounding): Long {
    val n = value * numerator
    return when (rounding) {
        Rounding.TRUNCATE -> n / denominator
        Rounding.CEIL -> (n + denominator - 1) / denominator
        Rounding.ROUND -> (n + denominator / 2) / denominator
    }
}

data class HitProfile(
    val hit: Boolean,
    val crit: Boolean,
    val combo: Boolean,
    val blocked: Boolean,
)

data class DamageInput(
    val atk: Int,
    val def: Int,
    val skillCoeffPct: Int,
    val modifiers: DamageModifiers = DamageModifiers(),
    val flags: DamageFlags = DamageFlags(),
)

data class DamageModifiers(
    val generalDmgPct: Int = 100,
    val fiveStatPct: Int = 100,
)

data class DamageFlags(
    val crit: Boolean = false,
    val counter: CounterRelation? = null,
    val blocked: Boolean = false,
)

object Formula {
    /**
     * RNG consumption order is a deterministic contract:
     * hit -> crit -> combo -> block.
     */
    fun rollHitProfile(attacker: CombatRates, defender: CombatRates, rng: Rng): HitProfile {
        val hit = rng.nextPercent() < (attacker.hit - defender.evade).coerceIn(0, 100)
        if (!hit) return HitProfile(hit = false, crit = false, combo = false, blocked = false)

        val crit = rng.nextPercent() < (attacker.crit - defender.critResist).coerceIn(0, 100)
        val combo = rng.nextPercent() < (attacker.combo - defender.comboResist).coerceIn(0, 100)
        val blocked = rng.nextPercent() < (defender.block - attacker.precision).coerceIn(0, 100)
        return HitProfile(hit = true, crit = crit, combo = combo, blocked = blocked)
    }

    fun damage(input: DamageInput, rules: BattleRules = BattleRules.DEFAULT): Int {
        val raw = input.atk - input.def
        if (raw <= 0) {
            return max(1, mulDiv(input.atk.toLong(), rules.damage.chipPermille, 1000, rules.rounding).toInt())
        }

        var value = raw.toLong()
        value = mulDiv(value, input.skillCoeffPct, 100, rules.rounding)
        value = mulDiv(value, input.modifiers.generalDmgPct, 100, rules.rounding)
        value = mulDiv(value, input.modifiers.fiveStatPct, 100, rules.rounding)

        if (input.flags.crit) value = mulDiv(value, rules.damage.critPct, 100, rules.rounding)

        when (input.flags.counter) {
            CounterRelation.FAVOR -> value = mulDiv(value, rules.damage.counter.favorPct, 100, rules.rounding)
            CounterRelation.UNFAVOR -> value = mulDiv(value, rules.damage.counter.unfavorPct, 100, rules.rounding)
            null -> Unit
        }

        if (input.flags.blocked) value = mulDiv(value, rules.damage.blockReducePct, 100, rules.rounding)

        return max(1L, value).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
