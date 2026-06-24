package com.ccz.contentpack.assembly

import com.ccz.contentpack.ClassGrowth
import com.ccz.contentpack.UnitDef
import com.ccz.core.battle.ScriptContext
import com.ccz.core.model.CombatIdentity
import com.ccz.core.model.CombatVitals
import com.ccz.core.model.Combatant
import com.ccz.core.model.Pos

/**
 * Assembles validated [UnitDef]s into the off-map [Combatant] templates a
 * SpawnUnit op draws from at runtime ([ScriptContext.reserves], keyed by unit id).
 *
 * Reserves enter at full HP. Their [Combatant.pos] is a placeholder ([OFF_MAP])
 * because a reserve is not on the board until a SpawnUnit op places it — the op
 * overwrites this with its target tile. Content is assumed already validated
 * (unique ids, resolvable references) by ContentValidator, so duplicate ids are
 * not re-checked here. The unit's loadout / assets are presentation.
 *
 * The unit's combat panel is BUDGETED to its [level][com.ccz.contentpack.UnitProfile.level], scaled by
 * its quality [grade][com.ccz.contentpack.UnitProfile.grade], via [GrowthBudget] using its class's
 * [ClassGrowth] (ADR 0006): a pure, deterministic, assembly-time closed form baked into the snapshot, so
 * replay never recomputes and the battle RNG is untouched. With the default empty growth map (no class
 * supplies weights) every panel equals its base — the engine's prior behaviour — which is why goldens
 * stay byte-identical; grade only matters once growth weights are present.
 */
object BattleAssembler {
    /** Sentinel position for an unplaced reserve; SpawnUnit overwrites it with op.at. */
    private val OFF_MAP = Pos(-1, -1)

    /** Maps each unit to its reserve [Combatant] template (panel budgeted by class growth), keyed by id. */
    fun reserves(
        units: List<UnitDef>,
        growthByClass: Map<String, ClassGrowth> = emptyMap(),
        cfg: GrowthConfig = GrowthConfig(),
    ): Map<String, Combatant> =
        units.associate { it.id to it.toReserveCombatant(growthByClass[it.classId] ?: ClassGrowth(), cfg) }

    /** Convenience: a [ScriptContext] whose reserves are assembled (and budgeted) from [units]. */
    fun scriptContext(
        units: List<UnitDef>,
        growthByClass: Map<String, ClassGrowth> = emptyMap(),
        cfg: GrowthConfig = GrowthConfig(),
    ): ScriptContext = ScriptContext(reserves(units, growthByClass, cfg))

    private fun UnitDef.toReserveCombatant(growth: ClassGrowth, cfg: GrowthConfig): Combatant {
        // Quality tier scales how fast class growth accrues; grade 0 (the default) is the neutral 100%.
        val gradeMulPct = GrowthBudget.gradeMulPct(grade = profile.grade, cfg = cfg)
        val hpMax = GrowthBudget.budgetHp(profile.hpMax, growth, profile.level, gradeMulPct, cfg)
        return Combatant(
            identity = CombatIdentity(
                id = identity.id,
                name = identity.name,
                classId = identity.classId,
                faction = identity.faction,
            ),
            pos = OFF_MAP,
            vitals = CombatVitals(hp = hpMax, hpMax = hpMax),
            stats = GrowthBudget.budgetStats(profile.stats, growth, profile.level, gradeMulPct, cfg),
            rates = profile.rates,
        )
    }
}
