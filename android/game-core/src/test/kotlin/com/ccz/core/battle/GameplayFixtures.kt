package com.ccz.core.battle

import com.ccz.core.model.AccuracyRates
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

internal const val SEED = 777L

internal fun combatant(
    id: String,
    faction: Faction,
    pos: Pos,
    hp: Int = 100,
    classId: String = "inf",
): Combatant =
    Combatant(
        identity = CombatIdentity(id, id, classId, faction),
        pos = pos,
        vitals = CombatVitals(hp = hp, hpMax = 100),
        stats = CombatStats(atk = 80, def = 20, mat = 30, res = 10),
        rates = CombatRates(accuracy = AccuracyRates(hit = 100)),
    )

internal fun stateOf(vararg units: Combatant, active: Faction = Faction.PLAYER, turn: Int = 1): BattleState =
    BattleState(units = units.associateBy { it.id }, turn = turn, active = active, rngState = SEED)

internal fun classesOf(move: Int = 5): Map<String, UnitClass> =
    mapOf("inf" to UnitClass("inf", "Infantry", "foot", move))

internal fun skillsOf(id: String = "atk", range: RangeSpec = RangeSpec.MELEE): Map<String, Skill> =
    mapOf(id to Skill(id, "Attack", DamageKind.PHYSICAL, 100, range))

internal fun flat(width: Int, height: Int, cost: Int = 1): BattleMap =
    BattleMap.uniform(width, height, MapTile("plain", cost))

internal fun contextOf(
    map: BattleMap,
    classes: Map<String, UnitClass> = classesOf(),
    skills: Map<String, Skill> = skillsOf(),
): BattleContext = BattleContext(map = map, classes = classes, skills = skills)
