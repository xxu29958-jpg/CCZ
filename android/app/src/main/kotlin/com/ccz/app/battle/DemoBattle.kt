package com.ccz.app.battle

import com.ccz.core.battle.BattleContext
import com.ccz.core.battle.BattleMap
import com.ccz.core.battle.BattleState
import com.ccz.core.battle.MapTile
import com.ccz.core.model.CombatIdentity
import com.ccz.core.model.CombatRates
import com.ccz.core.model.CombatStats
import com.ccz.core.model.CombatVitals
import com.ccz.core.model.Combatant
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import com.ccz.core.model.UnitClass

/**
 * A hardcoded battle seed so the shell has something concrete to render and drive while
 * the real content-fed battle driver layer is still pending. This is plain initial input
 * to game-core (map + classes + an opening [BattleState]) — NOT a second source of combat
 * truth: the app never reads these values to decide an outcome, it only hands them to
 * [com.ccz.core.battle.Gameplay] and renders what comes back. It will be replaced by a
 * native-content-loaded scenario once the driver layer lands.
 */
object DemoBattle {
    const val WIDTH = 7
    const val HEIGHT = 6

    /**
     * Skill id a tapped basic attack is submitted with. Matches the entry in the demo
     * context's skill table ([com.ccz.core.battle.Resolver.DEMO_SKILLS]); the skill's range
     * lives there, in the authority — this is just which skill the demo's basic attack uses.
     */
    const val BASIC_ATTACK = "atk"

    fun context(): BattleContext = BattleContext(map = demoMap(), classes = classes())

    fun initialState(): BattleState =
        BattleState(units = units().associateBy { it.id }, turn = 1, active = Faction.PLAYER, rngState = 1L)

    private fun demoMap(): BattleMap {
        val rows = (0 until HEIGHT).map { y -> (0 until WIDTH).map { x -> tileAt(x, y) } }
        return BattleMap(WIDTH, HEIGHT, rows)
    }

    private fun tileAt(x: Int, y: Int): MapTile = when {
        x == 3 && y in 1..3 -> MapTile("wall", moveCost = 1, passable = false)
        y == 4 && x in 2..5 -> MapTile("forest", moveCost = 2)
        else -> MapTile("plain", moveCost = 1)
    }

    private fun classes(): Map<String, UnitClass> = mapOf(
        "cavalry" to UnitClass("cavalry", "Cavalry", "horse", move = 5),
        "infantry" to UnitClass("infantry", "Infantry", "foot", move = 4),
        "archer" to UnitClass("archer", "Archer", "foot", move = 4),
    )

    private fun units(): List<Combatant> = listOf(
        // Guan (cavalry) is still maneuvering up the left flank; Zhang (infantry) has reached
        // the front line adjacent to the enemy archer, so a basic attack is legal from turn one.
        unit(CombatIdentity("guan", "Guan Yu", "cavalry", Faction.PLAYER), Pos(1, 1), hp = 240),
        unit(CombatIdentity("zhang", "Zhang Fei", "infantry", Faction.PLAYER), Pos(4, 2), hp = 220),
        unit(CombatIdentity("foe", "Enemy Archer", "archer", Faction.ENEMY), Pos(5, 2), hp = 180),
    )

    private fun unit(identity: CombatIdentity, pos: Pos, hp: Int): Combatant =
        Combatant(
            identity = identity,
            pos = pos,
            vitals = CombatVitals(hp = hp, hpMax = hp),
            stats = CombatStats(atk = 120, def = 80, mat = 40, res = 60),
            rates = CombatRates(),
        )
}
