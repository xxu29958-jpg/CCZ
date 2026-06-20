package com.ccz.contentpack.assembly

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
 * not re-checked here. The unit's level / loadout / assets are presentation or
 * derive into the already-resolved combat panel, so they are not carried here.
 */
object BattleAssembler {
    /** Sentinel position for an unplaced reserve; SpawnUnit overwrites it with op.at. */
    private val OFF_MAP = Pos(-1, -1)

    /** Maps each unit to its reserve [Combatant] template, keyed by unit id. */
    fun reserves(units: List<UnitDef>): Map<String, Combatant> =
        units.associate { it.id to it.toReserveCombatant() }

    /** Convenience: a [ScriptContext] whose reserves are assembled from [units]. */
    fun scriptContext(units: List<UnitDef>): ScriptContext = ScriptContext(reserves(units))

    private fun UnitDef.toReserveCombatant(): Combatant = Combatant(
        identity = CombatIdentity(
            id = identity.id,
            name = identity.name,
            classId = identity.classId,
            faction = identity.faction,
        ),
        pos = OFF_MAP,
        vitals = CombatVitals(hp = profile.hpMax, hpMax = profile.hpMax),
        stats = profile.stats,
        rates = profile.rates,
    )
}
