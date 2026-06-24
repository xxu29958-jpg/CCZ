package com.ccz.core.model

/**
 * Final combat panel after five stats, equipment, status effects, and content
 * modifiers have already been resolved. Core formulas use integers only.
 */
data class CombatStats(
    val atk: Int,
    val def: Int,
    val mat: Int,
    val res: Int,
)

data class AccuracyRates(
    val hit: Int = 100,
    val evade: Int = 0,
    val precision: Int = 0,
    val block: Int = 0,
)

data class BurstRates(
    val crit: Int = 0,
    val critResist: Int = 0,
    val combo: Int = 0,
    val comboResist: Int = 0,
)

/** Integer percentage rates. Attacker and defender values cancel by subtraction. */
data class CombatRates(
    val accuracy: AccuracyRates = AccuracyRates(),
    val burst: BurstRates = BurstRates(),
) {
    val hit: Int get() = accuracy.hit
    val evade: Int get() = accuracy.evade
    val precision: Int get() = accuracy.precision
    val block: Int get() = accuracy.block
    val crit: Int get() = burst.crit
    val critResist: Int get() = burst.critResist
    val combo: Int get() = burst.combo
    val comboResist: Int get() = burst.comboResist
}

enum class Faction { PLAYER, ALLY, ENEMY }
enum class DamageKind { PHYSICAL, STRATEGY }
enum class CounterRelation { FAVOR, UNFAVOR }

data class Pos(val x: Int, val y: Int)

data class UnitClass(
    val id: String,
    val name: String,
    val moveType: String,
    val move: Int,
    val counters: Map<String, CounterRelation> = emptyMap(),
    val terrain: ClassTerrain = ClassTerrain(),
)

/**
 * A class's terrain interaction, keyed by [MapTile.terrainId] (grouped so [UnitClass] stays within the
 * parameter gate). Both maps are opt-in: an absent terrain (or the empty default) is neutral.
 *
 * - [moveCost]: per-class enter cost — `>= 1` overrides the tile's own [MapTile.moveCost], `<= 0` makes
 *   the terrain impassable for the class (Advance-Wars movement types; mirrors legacy `dic_jobWalk`).
 * - [affinity]: combat percent applied to the unit's outgoing damage from the tile it stands on
 *   (100 = neutral, 120 = +20%); favorable-ground combat (FFT/Fire Emblem; mirrors `dic_jobTerrain`).
 */
data class ClassTerrain(
    val moveCost: Map<String, Int> = emptyMap(),
    val affinity: Map<String, Int> = emptyMap(),
)

data class CombatIdentity(
    val id: String,
    val name: String,
    val classId: String,
    val faction: Faction,
)

data class CombatVitals(
    val hp: Int,
    val hpMax: Int,
)

data class Combatant(
    val identity: CombatIdentity,
    val pos: Pos,
    val vitals: CombatVitals,
    val stats: CombatStats,
    val rates: CombatRates,
    val statuses: Set<String> = emptySet(),
) {
    val id: String get() = identity.id
    val name: String get() = identity.name
    val classId: String get() = identity.classId
    val faction: Faction get() = identity.faction
    val hp: Int get() = vitals.hp
    val hpMax: Int get() = vitals.hpMax
    val alive: Boolean get() = hp > 0

    fun withHp(hp: Int): Combatant = copy(vitals = vitals.copy(hp = hp))
}

/** Inclusive attack-distance band, measured in Manhattan tiles (no diagonals). */
data class RangeSpec(val min: Int, val max: Int) {
    init {
        require(min in 0..max) { "range min ($min) must be in 0..max ($max)" }
    }

    fun covers(distance: Int): Boolean = distance in min..max

    companion object {
        val MELEE = RangeSpec(1, 1)
    }
}

data class Skill(
    val id: String,
    val name: String,
    val kind: DamageKind,
    val powerCoeff: Int,
    val range: RangeSpec = RangeSpec.MELEE,
)
