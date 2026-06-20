package com.ccz.core.battle

import com.ccz.core.model.AccuracyRates
import com.ccz.core.model.BurstRates
import com.ccz.core.model.CombatRates
import com.ccz.core.rng.Rng
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the RNG consumption contract of [Formula.rollHitProfile]:
 * order hit -> crit -> combo -> block, and consumption COUNT (4 rolls on hit,
 * 1 on miss via short-circuit). Count is hard-pinned via the splitmix64 state
 * delta; the seed->profile mapping (order) is additionally pinned by
 * [GoldenReplayTest]'s event stream. See CCZ_ENGINE_RULES: RNG 消费顺序是规则契约.
 */
class RngContractTest {
    @Test
    fun hitPathConsumesExactlyFourRolls() {
        val rng = Rng.seed(SEED)
        val profile = Formula.rollHitProfile(attackerAllPass(), defenderAllPass(), rng)

        assertEquals(HitProfile(hit = true, crit = true, combo = true, blocked = true), profile)
        assertEquals(SEED + 4 * STEP, rng.snapshot())
    }

    @Test
    fun missPathShortCircuitsAfterOneRoll() {
        val rng = Rng.seed(SEED)
        val profile = Formula.rollHitProfile(attackerNeverHits(), defenderAllPass(), rng)

        assertEquals(HitProfile(hit = false, crit = false, combo = false, blocked = false), profile)
        assertEquals(SEED + STEP, rng.snapshot())
    }

    private fun attackerAllPass(): CombatRates =
        CombatRates(
            accuracy = AccuracyRates(hit = 100, precision = 0),
            burst = BurstRates(crit = 100, combo = 100),
        )

    private fun attackerNeverHits(): CombatRates =
        CombatRates(accuracy = AccuracyRates(hit = 0))

    private fun defenderAllPass(): CombatRates =
        CombatRates(
            accuracy = AccuracyRates(evade = 0, block = 100),
            burst = BurstRates(critResist = 0, comboResist = 0),
        )

    private companion object {
        const val SEED = 12345L

        // splitmix64 increment applied per Rng.next(); state advances by this each nextPercent().
        const val STEP = -0x61c8864680b583ebL
    }
}
