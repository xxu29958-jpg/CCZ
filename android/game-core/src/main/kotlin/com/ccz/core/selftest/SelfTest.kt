package com.ccz.core.selftest

import com.ccz.core.battle.Command
import com.ccz.core.battle.Event
import com.ccz.core.battle.ResolveContext
import com.ccz.core.battle.Resolver
import com.ccz.core.battle.BattleState
import com.ccz.core.model.AccuracyRates
import com.ccz.core.model.BurstRates
import com.ccz.core.model.CombatRates
import com.ccz.core.model.CombatStats
import com.ccz.core.model.CounterRelation
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import com.ccz.core.model.UnitClass
import com.ccz.core.model.Combatant
import com.ccz.core.model.CombatIdentity
import com.ccz.core.model.CombatVitals

fun main() {
    val classes = sampleClasses()
    val commands = sampleCommands()
    val first = runScenario(seed = 12345L, classes = classes, commands = commands)
    val second = runScenario(seed = 12345L, classes = classes, commands = commands)
    val third = runScenario(seed = 98765L, classes = classes, commands = commands)

    check(first == second) {
        "same seed produced different state or events"
    }
    check(first.second != third.second) {
        "different seed unexpectedly produced identical event stream"
    }
    println("OK deterministic game-core self-test passed")
}

private fun sampleClasses(): Map<String, UnitClass> =
    mapOf(
        "cavalry" to UnitClass(
            id = "cavalry",
            name = "Cavalry",
            moveType = "horse",
            move = 6,
            counters = mapOf("archer" to CounterRelation.FAVOR),
        ),
        "archer" to UnitClass(
            id = "archer",
            name = "Archer",
            moveType = "foot",
            move = 5,
        ),
    )

private fun sampleCommands(): List<Command> =
    listOf(
        Command.Move("zhaoyun", Pos(4, 2)),
        Command.Attack("zhaoyun", "e1", "atk"),
        Command.Attack("zhaoyun", "e1", "atk"),
        Command.EndTurn(Faction.PLAYER),
    )

private fun fresh(seed: Long): BattleState {
    val zhaoyun = playerUnit()
    val enemy = enemyUnit()
    return BattleState(
        units = mapOf(zhaoyun.id to zhaoyun, enemy.id to enemy),
        turn = 1,
        active = Faction.PLAYER,
        rngState = seed,
    )
}

private fun playerUnit(): Combatant =
    Combatant(
        identity = CombatIdentity(
            id = "zhaoyun",
            name = "Zhao Yun",
            classId = "cavalry",
            faction = Faction.PLAYER,
        ),
        pos = Pos(2, 2),
        vitals = CombatVitals(hp = 200, hpMax = 200),
        stats = CombatStats(atk = 180, def = 120, mat = 60, res = 90),
        rates = CombatRates(
            accuracy = AccuracyRates(hit = 100),
            burst = BurstRates(crit = 20, combo = 15),
        ),
    )

private fun enemyUnit(): Combatant =
    Combatant(
        identity = CombatIdentity(
            id = "e1",
            name = "Enemy Archer",
            classId = "archer",
            faction = Faction.ENEMY,
        ),
        pos = Pos(5, 2),
        vitals = CombatVitals(hp = 400, hpMax = 400),
        stats = CombatStats(atk = 140, def = 80, mat = 40, res = 70),
        rates = CombatRates(
            accuracy = AccuracyRates(hit = 95, evade = 10),
            burst = BurstRates(critResist = 5),
        ),
    )

private fun runScenario(
    seed: Long,
    classes: Map<String, UnitClass>,
    commands: List<Command>,
): Pair<BattleState, List<Event>> {
    var state = fresh(seed)
    val events = mutableListOf<Event>()
    for (command in commands) {
        val resolution = Resolver.apply(state, command, ResolveContext(classes))
        state = resolution.state
        events += resolution.events
    }
    return state to events
}
