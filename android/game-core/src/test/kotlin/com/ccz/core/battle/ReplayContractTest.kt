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
import kotlin.test.assertNotEquals

class ReplayContractTest {
    @Test
    fun sameSeedAndCommandsReplayToSameResult() {
        val commands = listOf(
            Command.Move("zhaoyun", Pos(4, 2)),
            Command.Attack("zhaoyun", "e1", "atk"),
            Command.Attack("zhaoyun", "e1", "atk"),
            Command.EndTurn(Faction.PLAYER),
        )

        val first = runScenario(seed = 12345L, commands = commands)
        val second = runScenario(seed = 12345L, commands = commands)
        val differentSeed = runScenario(seed = 98765L, commands = commands)

        assertEquals(first, second)
        assertNotEquals(first.events, differentSeed.events)
    }

    @Test
    fun resolverReturnsNewStateWithoutMutatingInputState() {
        val initial = fresh(seed = 12345L)
        val resolution = Resolver.apply(initial, Command.Move("zhaoyun", Pos(4, 2)), classes)

        assertEquals(Pos(2, 2), initial.unit("zhaoyun").pos)
        assertEquals(Pos(4, 2), resolution.state.unit("zhaoyun").pos)
    }

    private fun runScenario(seed: Long, commands: List<Command>): ReplayResult {
        var state = fresh(seed)
        val events = mutableListOf<Event>()
        commands.forEach { command ->
            val resolution = Resolver.apply(state, command, classes)
            state = resolution.state
            events += resolution.events
        }
        return ReplayResult(state, events)
    }

    private fun fresh(seed: Long): BattleState =
        BattleState(
            units = listOf(playerUnit(), enemyUnit()).associateBy { it.id },
            turn = 1,
            active = Faction.PLAYER,
            rngState = seed,
        )

    private fun playerUnit(): Combatant =
        Combatant(
            identity = CombatIdentity("zhaoyun", "Zhao Yun", "cavalry", Faction.PLAYER),
            pos = Pos(2, 2),
            vitals = CombatVitals(hp = 200, hpMax = 200),
            stats = CombatStats(atk = 180, def = 120, mat = 60, res = 90),
            rates = CombatRates(accuracy = AccuracyRates(hit = 100), burst = BurstRates(crit = 20, combo = 15)),
        )

    private fun enemyUnit(): Combatant =
        Combatant(
            identity = CombatIdentity("e1", "Enemy Archer", "archer", Faction.ENEMY),
            pos = Pos(5, 2),
            vitals = CombatVitals(hp = 400, hpMax = 400),
            stats = CombatStats(atk = 140, def = 80, mat = 40, res = 70),
            rates = CombatRates(accuracy = AccuracyRates(hit = 95, evade = 10), burst = BurstRates(critResist = 5)),
        )

    private data class ReplayResult(
        val state: BattleState,
        val events: List<Event>,
    )

    private companion object {
        val classes = mapOf(
            "cavalry" to UnitClass(
                id = "cavalry",
                name = "Cavalry",
                moveType = "horse",
                move = 6,
                counters = mapOf("archer" to CounterRelation.FAVOR),
            ),
            "archer" to UnitClass(id = "archer", name = "Archer", moveType = "foot", move = 5),
        )
    }
}
