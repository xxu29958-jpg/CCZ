package com.ccz.core.battle

import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Terrain heal (FE/AW fort/village): a unit recovers HP at the start of its side's phase while standing
 * on a healing tile, capped at max HP. Deterministic (no RNG) and inert at heal 0 / when no map is
 * threaded, so existing combat and the golden replay (which runs without a map) are unchanged.
 */
class TerrainHealTest {
    /** Resolve EndTurn(ending) — which begins the OTHER side's phase — over [map], returning the result. */
    private fun afterEndTurn(startHp: Int, unitSide: Faction, ending: Faction, map: BattleMap?): Resolution {
        val unit = combatant("p", unitSide, Pos(0, 0), hp = startHp)
        return Resolver.apply(
            stateOf(unit, active = ending),
            Command.EndTurn(ending),
            ResolveContext(classesOf(), skillsOf(), BattleRules.DEFAULT, map),
        )
    }

    private fun healTile(heal: Int): BattleMap = BattleMap(1, 1, listOf(listOf(MapTile("fort", 1, heal = heal))))

    @Test
    fun woundedUnitHealsAtStartOfItsPhase() {
        // enemy ends its turn -> player phase begins -> the wounded player unit on a fort recovers 30
        val r = afterEndTurn(startHp = 50, unitSide = Faction.PLAYER, ending = Faction.ENEMY, map = healTile(30))
        assertEquals(80, r.state.unit("p").hp)
        assertTrue(r.events.contains(Event.Healed("p", 30)), "the recovery is reported")
    }

    @Test
    fun healCapsAtMaxHp() {
        val r = afterEndTurn(startHp = 90, unitSide = Faction.PLAYER, ending = Faction.ENEMY, map = healTile(30))
        assertEquals(100, r.state.unit("p").hp, "heal never overfills past max HP")
        assertTrue(r.events.contains(Event.Healed("p", 10)), "only the 10 HP actually gained is reported")
    }

    @Test
    fun fullHpUnitIsSkipped() {
        val r = afterEndTurn(startHp = 100, unitSide = Faction.PLAYER, ending = Faction.ENEMY, map = healTile(30))
        assertEquals(100, r.state.unit("p").hp)
        assertFalse(r.events.any { it is Event.Healed }, "a full-HP unit emits no heal event")
    }

    @Test
    fun noHealTileAndNoMapLeaveHpUnchanged() {
        assertEquals(50, afterEndTurn(50, Faction.PLAYER, Faction.ENEMY, healTile(0)).state.unit("p").hp, "heal 0 = no recovery")
        assertEquals(50, afterEndTurn(50, Faction.PLAYER, Faction.ENEMY, map = null).state.unit("p").hp, "no map = no recovery")
    }

    @Test
    fun onlyTheArrivingSidesUnitsHeal() {
        // player ends its turn -> ENEMY phase begins -> the player unit (not the arriving side) does not heal
        val r = afterEndTurn(startHp = 50, unitSide = Faction.PLAYER, ending = Faction.PLAYER, map = healTile(30))
        assertEquals(50, r.state.unit("p").hp, "only the side whose phase is starting recovers")
    }
}
