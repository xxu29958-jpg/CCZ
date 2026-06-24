package com.ccz.core.battle

import com.ccz.core.model.ClassTerrain
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import com.ccz.core.model.UnitClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Terrain combat affinity (FFT/Fire-Emblem favorable-ground): the attacker's class affinity for the
 * tile it stands on scales its outgoing damage. Inert at 100 / when no map is threaded, so existing
 * combat (and the golden replay) is unchanged — verified here by the neutral baseline.
 *
 * With the fixtures, a clean hit deals (atk 80 − def 20) × skill 100% = 60 base damage before affinity.
 */
class TerrainCombatTest {
    private val attack = Command.Attack("a", "e", "atk")

    private fun defenderHpAfterAttack(affinity: Map<String, Int>, map: BattleMap?): Int {
        val state = stateOf(
            combatant("a", Faction.PLAYER, Pos(0, 0)),
            combatant("e", Faction.ENEMY, Pos(1, 0)),
        )
        val classes = mapOf("inf" to UnitClass("inf", "Infantry", "foot", 5, terrain = ClassTerrain(affinity = affinity)))
        val resolution = Resolver.apply(state, attack, ResolveContext(classes, skillsOf(), BattleRules.DEFAULT, map))
        return resolution.state.unit("e").hp
    }

    @Test
    fun favorableTerrainIncreasesOutgoingDamage() {
        // base 60 damage; +20% affinity -> 72 -> hp 100-72=28
        assertEquals(100 - 72, defenderHpAfterAttack(mapOf("plain" to 120), flat(2, 1)))
    }

    @Test
    fun unfavorableTerrainReducesOutgoingDamage() {
        // base 60 damage; -20% affinity -> 48 -> hp 100-48=52
        assertEquals(100 - 48, defenderHpAfterAttack(mapOf("plain" to 80), flat(2, 1)))
    }

    @Test
    fun neutralAffinityAndNoMapAndNoEntryAllDealBaseDamage() {
        val baseline = 100 - 60
        assertEquals(baseline, defenderHpAfterAttack(mapOf("plain" to 100), flat(2, 1)), "affinity 100 = neutral")
        assertEquals(baseline, defenderHpAfterAttack(emptyMap(), flat(2, 1)), "no entry for the tile = neutral")
        assertEquals(baseline, defenderHpAfterAttack(mapOf("plain" to 120), map = null), "no map = neutral")
    }

    @Test
    fun affinityAppliesToAttackerTileNotDefenderTile() {
        // attacker on a 'hill' (affinity 150); the lone non-uniform map gives the attacker tile a
        // distinct terrain. Damage scales by the attacker's tile, proving it is attacker-side.
        val map = BattleMap(2, 1, listOf(listOf(MapTile("hill", 1), MapTile("plain", 1))))
        val state = stateOf(
            combatant("a", Faction.PLAYER, Pos(0, 0)),
            combatant("e", Faction.ENEMY, Pos(1, 0)),
        )
        val classes = mapOf("inf" to UnitClass("inf", "Infantry", "foot", 5, terrain = ClassTerrain(affinity = mapOf("hill" to 150))))
        val hp = Resolver.apply(state, attack, ResolveContext(classes, skillsOf(), BattleRules.DEFAULT, map)).state.unit("e").hp
        assertTrue(hp == 100 - 90, "attacker on hill (150%): 60*1.5=90 damage -> hp $hp")
    }
}
