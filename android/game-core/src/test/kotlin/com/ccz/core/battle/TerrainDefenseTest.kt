package com.ccz.core.battle

import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Terrain defense (FE/AW defensive ground): a flat bonus to the defender's effective DEF from the tile
 * it stands on, so a unit takes less while holding a fort/forest. Inert at 0 / when no map is threaded,
 * so existing combat (and the golden replay) is unchanged. Fixtures: a clean hit is (atk 80 − def 20) ×
 * skill 100% = 60 base damage; the defender "e" stands on (1,0), the attacker on open ground at (0,0).
 */
class TerrainDefenseTest {
    private val attack = Command.Attack("a", "e", "atk")

    private fun defenderHpAfterAttack(map: BattleMap?): Int {
        val state = stateOf(
            combatant("a", Faction.PLAYER, Pos(0, 0)),
            combatant("e", Faction.ENEMY, Pos(1, 0)),
        )
        val resolution = Resolver.apply(state, attack, ResolveContext(classesOf(), skillsOf(), BattleRules.DEFAULT, map))
        return resolution.state.unit("e").hp
    }

    /** A 2x1 map whose only defended tile is (1,0), where the defender stands. */
    private fun defenderOnDefense(defBonus: Int): BattleMap =
        BattleMap(2, 1, listOf(listOf(MapTile("plain", 1), MapTile("fort", 1, defBonus = defBonus))))

    @Test
    fun defensiveTerrainReducesIncomingDamage() {
        // def 20 + terrain 30 = 50 effective; raw 80-50 = 30 -> hp 100-30 = 70
        assertEquals(100 - 30, defenderHpAfterAttack(defenderOnDefense(30)))
    }

    @Test
    fun openGroundAndNoMapDealBaseDamage() {
        assertEquals(100 - 60, defenderHpAfterAttack(defenderOnDefense(0)), "defBonus 0 = open ground")
        assertEquals(100 - 60, defenderHpAfterAttack(map = null), "no map = neutral")
    }

    @Test
    fun heavyDefenseFloorsAttackToChip() {
        // def 20 + terrain 80 = 100 >= atk 80 -> raw <= 0 -> chip damage, far less than the 30-bonus case
        val chipHp = defenderHpAfterAttack(defenderOnDefense(80))
        assertTrue(chipHp > defenderHpAfterAttack(defenderOnDefense(30)), "more defense = less damage")
        assertTrue(chipHp < 100, "an attack always deals at least chip damage")
    }

    @Test
    fun defenseAppliesToDefenderTileNotAttackerTile() {
        // bonus on the ATTACKER tile (0,0); the defender on open (1,0) gets no protection -> base 60.
        val map = BattleMap(2, 1, listOf(listOf(MapTile("fort", 1, defBonus = 30), MapTile("plain", 1))))
        assertEquals(100 - 60, defenderHpAfterAttack(map))
    }
}
