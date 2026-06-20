package com.ccz.core.battle

import com.ccz.core.model.AccuracyRates
import com.ccz.core.model.BurstRates
import com.ccz.core.model.CombatIdentity
import com.ccz.core.model.CombatRates
import com.ccz.core.model.CombatStats
import com.ccz.core.model.CombatVitals
import com.ccz.core.model.Combatant
import com.ccz.core.model.CounterRelation
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import com.ccz.core.model.UnitClass
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cross-version golden: pins the exact event stream + final state for a fixed
 * (seed, command-sequence). Unlike [ReplayContractTest] (same-build determinism),
 * this catches formula-constant / RNG-order drift across versions. If a rule
 * constant legitimately changes, regenerate GOLDEN AND bump
 * [BattleRules.RULES_VERSION] (CCZ_ENGINE_RULES: 公式常量变化 = 规则版本变化).
 */
class GoldenReplayTest {
    @Test
    fun fixedSeedScenarioMatchesGolden() {
        assertEquals(GOLDEN, runScenario())
    }

    private fun runScenario(): String {
        var state = fresh()
        val log = StringBuilder("rules=").append(BattleRules.RULES_VERSION).append('\n')
        COMMANDS.forEach { command ->
            val resolution = Resolver.apply(state, command, CLASSES)
            state = resolution.state
            resolution.events.forEach { log.append("E ").append(it).append('\n') }
        }
        state.units.values.sortedBy { it.id }.forEach {
            log.append("U ").append(it.id).append(" hp=").append(it.hp).append('\n')
        }
        return log.append("rng=").append(state.rngState).toString()
    }

    private fun fresh(): BattleState =
        BattleState(
            units = listOf(player(), enemy()).associateBy { it.id },
            turn = 1,
            active = Faction.PLAYER,
            rngState = SEED,
        )

    private fun player(): Combatant =
        Combatant(
            identity = CombatIdentity("zhaoyun", "Zhao Yun", "cavalry", Faction.PLAYER),
            pos = Pos(2, 2),
            vitals = CombatVitals(hp = 200, hpMax = 200),
            stats = CombatStats(atk = 180, def = 120, mat = 60, res = 90),
            rates = CombatRates(accuracy = AccuracyRates(hit = 100), burst = BurstRates(crit = 20, combo = 15)),
        )

    private fun enemy(): Combatant =
        Combatant(
            identity = CombatIdentity("e1", "Enemy Archer", "archer", Faction.ENEMY),
            pos = Pos(5, 2),
            vitals = CombatVitals(hp = 400, hpMax = 400),
            stats = CombatStats(atk = 140, def = 80, mat = 40, res = 70),
            rates = CombatRates(accuracy = AccuracyRates(hit = 95, evade = 10), burst = BurstRates(critResist = 5)),
        )

    private companion object {
        const val SEED = 12345L

        // Captured from a green run; regenerate intentionally + bump RULES_VERSION on rule change.
        val GOLDEN =
            """
            rules=1
            E Moved(unit=zhaoyun, from=Pos(x=2, y=2), to=Pos(x=4, y=2))
            E Damaged(target=e1, amount=130, crit=false, combo=false, broke=true)
            E Damaged(target=e1, amount=65, crit=false, combo=true, broke=true)
            E Damaged(target=e1, amount=130, crit=false, combo=false, broke=true)
            E TurnEnded(faction=PLAYER)
            U e1 hp=75
            U zhaoyun hp=200
            rng=-1028001813962157855
            """.trimIndent()

        val COMMANDS = listOf(
            Command.Move("zhaoyun", Pos(4, 2)),
            Command.Attack("zhaoyun", "e1", "atk"),
            Command.Attack("zhaoyun", "e1", "atk"),
            Command.EndTurn(Faction.PLAYER),
        )

        val CLASSES = mapOf(
            "cavalry" to UnitClass("cavalry", "Cavalry", "horse", 6, mapOf("archer" to CounterRelation.FAVOR)),
            "archer" to UnitClass("archer", "Archer", "foot", 5),
        )
    }
}
