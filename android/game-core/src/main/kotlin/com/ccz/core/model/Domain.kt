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

// 8 flat fields: a combat entity (identity/pos/vitals/stats/rates) plus its three condition layers (the inert
// `statuses` tag set, timed stat `effects`, and timed `ailments`). Kept FLAT rather than grouped because the
// save format is additive — each condition layer is a new top-level field (default empty) so older saves still
// decode; grouping would move them under a sub-object and break that forward-compat. Not a god-object, so the
// param gate is suppressed.
@Suppress("LongParameterList")
data class Combatant(
    val identity: CombatIdentity,
    val pos: Pos,
    val vitals: CombatVitals,
    val stats: CombatStats,
    val rates: CombatRates,
    val statuses: Set<String> = emptySet(),
    // Timed stat modifications currently active (ADR 0008 Phase 3); empty for a fresh/instant-only unit.
    // Default empty keeps existing construction and pre-Phase-3 saves byte-identical.
    val effects: List<ActiveEffect> = emptyList(),
    // Timed command-legality ailments currently active (ADR 0008); empty for an unafflicted unit. Default
    // empty keeps existing construction and pre-v5 saves byte-identical.
    val ailments: List<ActiveAilment> = emptyList(),
) {
    val id: String get() = identity.id
    val name: String get() = identity.name
    val classId: String get() = identity.classId
    val faction: Faction get() = identity.faction
    val hp: Int get() = vitals.hp
    val hpMax: Int get() = vitals.hpMax
    val alive: Boolean get() = hp > 0

    /** True while a [Ailment.SILENCE] is active — the unit may not cast (it may still move/attack/wait). */
    val silenced: Boolean get() = ailments.any { it.kind == Ailment.SILENCE }

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
    // Engine-owned effects this skill applies beyond damage (ADR 0008). Default empty = a pure damage
    // skill, byte-identical to before (existing Skill construction, DEMO_SKILLS, and goldens are unchanged).
    // A skill with non-empty effects is a "cast" skill, resolved via Command.Cast / Resolver.cast.
    val effects: List<SkillEffect> = emptyList(),
)
