package com.ccz.core.battle

import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Terrain avoid (FE/AW defensive ground): a flat evasion bonus from the tile a unit stands on, lowering
 * the attacker's hit chance against it. Only the hit threshold shifts (never the RNG draw order), so a
 * battle without avoid terrain — every existing golden — is unchanged. Fixtures: attacker hit 100,
 * defender evade 0, so on open ground every attack lands for (atk 80 − def 20) = 60 damage.
 */
class TerrainAvoidTest {
    private val attack = Command.Attack("a", "e", "atk")

    private fun resolveAttack(map: BattleMap?): Resolution {
        val state = stateOf(
            combatant("a", Faction.PLAYER, Pos(0, 0)),
            combatant("e", Faction.ENEMY, Pos(1, 0)),
        )
        return Resolver.apply(state, attack, ResolveContext(classesOf(), skillsOf(), BattleRules.DEFAULT, map))
    }

    /** A 2x1 map whose only evasive tile is (1,0), where the defender stands. */
    private fun defenderOnAvoid(avoidBonus: Int): BattleMap =
        BattleMap(2, 1, listOf(listOf(MapTile("plain", 1), MapTile("forest", 1, avoidBonus = avoidBonus))))

    @Test
    fun highAvoidTerrainMakesTheAttackMiss() {
        // avoid 100 -> effective hit (100 - 0 - 100).coerceIn(0,100) = 0 -> guaranteed miss, any seed
        val resolution = resolveAttack(defenderOnAvoid(100))
        assertEquals(100, resolution.state.unit("e").hp, "a missed attack deals no damage")
        assertTrue(resolution.events.any { it is Event.Missed }, "the attack is recorded as a miss")
    }

    @Test
    fun openGroundAndNoMapLetTheHitLand() {
        assertEquals(100 - 60, resolveAttack(defenderOnAvoid(0)).state.unit("e").hp, "avoid 0 = open ground, hit lands")
        assertEquals(100 - 60, resolveAttack(map = null).state.unit("e").hp, "no map = neutral, hit lands")
    }

    @Test
    fun avoidAppliesToDefenderTileNotAttackerTile() {
        // avoid 100 on the ATTACKER tile (0,0); the defender on open (1,0) is still hit for base damage.
        val map = BattleMap(2, 1, listOf(listOf(MapTile("forest", 1, avoidBonus = 100), MapTile("plain", 1))))
        assertEquals(100 - 60, resolveAttack(map).state.unit("e").hp)
    }
}
