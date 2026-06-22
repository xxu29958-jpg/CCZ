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
import com.ccz.core.model.DamageKind
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import com.ccz.core.model.RangeSpec
import com.ccz.core.model.Skill
import com.ccz.core.model.UnitClass

/**
 * A hardcoded battle seed so the shell has something concrete to render and drive while
 * the real content-fed battle driver layer is still pending. This is plain initial input
 * to game-core (map + classes + the skill table + per-unit loadouts + an opening
 * [BattleState]) — NOT a second source of combat truth: the app never reads these values to
 * decide an outcome, it only hands them to [com.ccz.core.battle.Gameplay] and renders what
 * comes back. It will be replaced by a native-content-loaded scenario once the driver layer
 * lands (which is also what will fill a loadout for every unit instead of this demo map).
 */
object DemoBattle {
    const val WIDTH = 7
    const val HEIGHT = 6

    fun context(): BattleContext =
        BattleContext(map = demoMap(), classes = classes(), skills = skills(), loadouts = loadouts())

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

    // The skill table is the authority on each skill's reach; loadouts (below) say which unit
    // may use which — game-core enforces both, the app only renders the choice.
    private fun skills(): Map<String, Skill> = mapOf(
        "strike" to Skill("strike", "Strike", DamageKind.PHYSICAL, powerCoeff = 100, range = RangeSpec.MELEE),
        "spear" to Skill("spear", "Spear Thrust", DamageKind.PHYSICAL, powerCoeff = 110, range = RangeSpec(1, 2)),
        "bow" to Skill("bow", "Bow Shot", DamageKind.PHYSICAL, powerCoeff = 80, range = RangeSpec(2, 3)),
    )

    private fun loadouts(): Map<String, List<String>> = mapOf(
        "guan" to listOf("strike", "spear"),
        "zhang" to listOf("strike"),
        "foe" to listOf("bow"),
        "foe2" to listOf("strike"),
    )

    private fun units(): List<Combatant> = listOf(
        // Zhang (infantry) is locked in melee with the archer to the east; Guan (cavalry) carries a
        // spear, so switching his skill from Strike (reach 1) to Spear (reach 1–2) brings the
        // spearman two tiles south into range — the demo shows targets changing with the skill.
        unit(CombatIdentity("guan", "Guan Yu", "cavalry", Faction.PLAYER), Pos(1, 2), hp = 240),
        unit(CombatIdentity("zhang", "Zhang Fei", "infantry", Faction.PLAYER), Pos(4, 2), hp = 220),
        unit(CombatIdentity("foe", "Enemy Archer", "archer", Faction.ENEMY), Pos(5, 2), hp = 180),
        unit(CombatIdentity("foe2", "Enemy Spearman", "infantry", Faction.ENEMY), Pos(1, 4), hp = 200),
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
