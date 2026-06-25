package com.ccz.app.battle

import com.ccz.core.battle.BattleMap
import com.ccz.core.battle.MapTile
import com.ccz.core.model.ActiveAilment
import com.ccz.core.model.ActiveEffect
import com.ccz.core.model.AffectedStat
import com.ccz.core.model.Ailment
import com.ccz.core.model.CombatIdentity
import com.ccz.core.model.CombatRates
import com.ccz.core.model.CombatStats
import com.ccz.core.model.CombatVitals
import com.ccz.core.model.Combatant
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the pure terrain-inspection projection on the JVM (no device). Proves the :app layer reads
 * the tile's cover straight off the authoritative [BattleMap] — it names what game-core already
 * carries (move cost + def/avoid/heal) and never invents a modifier.
 */
class TerrainInfoTest {
    private val plain = MapTile(terrainId = "t_plain", moveCost = 1)
    private val forest = MapTile(terrainId = "t_forest", moveCost = 2, defBonus = 2, avoidBonus = 15)
    private val wall = MapTile(terrainId = "t_wall", moveCost = 1, passable = false, defBonus = 30)
    private val map = BattleMap(width = 3, height = 1, rows = listOf(listOf(plain, forest, wall)))

    // The wall is deliberately absent so the fallback-to-id path is exercised.
    private val names = mapOf("t_plain" to "平原", "t_forest" to "森林")
    private fun nameOf(id: String): String = names[id] ?: id

    @Test
    fun resolvesTheTilesNameAndCoverFields() {
        val info = terrainInfoAt(map, ::nameOf, Pos(1, 0))!!
        assertEquals("森林", info.name)
        assertEquals(2, info.moveCost)
        assertTrue(info.passable)
        assertEquals(2, info.defBonus)
        assertEquals(15, info.avoidBonus)
        assertEquals(0, info.heal)
    }

    @Test
    fun fallsBackToTheTerrainIdWhenNoNameIsRegistered() {
        val info = terrainInfoAt(map, ::nameOf, Pos(2, 0))!!
        assertEquals("an unnamed terrain shows its id", "t_wall", info.name)
    }

    @Test
    fun returnsNullForAnOffMapTile() {
        assertNull("inspecting a tile off the map yields nothing", terrainInfoAt(map, ::nameOf, Pos(9, 9)))
    }

    @Test
    fun bonusLinesListOnlyTheNonZeroModifiers() {
        val lines = terrainBonusLines(terrainInfoAt(map, ::nameOf, Pos(1, 0))!!)
        assertEquals("forest has def + avoid but no heal", listOf("防御 +2", "回避 +15"), lines)
    }

    @Test
    fun bonusLinesAreEmptyForOpenGround() {
        assertTrue("open ground grants no cover", terrainBonusLines(terrainInfoAt(map, ::nameOf, Pos(0, 0))!!).isEmpty())
    }

    @Test
    fun bonusLinesFlagAnImpassableTile() {
        val lines = terrainBonusLines(terrainInfoAt(map, ::nameOf, Pos(2, 0))!!)
        assertEquals("impassability is reported first", "不可通行", lines.first())
        assertTrue("a wall still lists its defense cover", "防御 +30" in lines)
    }

    @Test
    fun combatantSummaryShowsHpAndAllFourStats() {
        val unit = Combatant(
            identity = CombatIdentity("u", "Hero", "cls", Faction.PLAYER),
            pos = Pos(0, 0),
            vitals = CombatVitals(hp = 30, hpMax = 100),
            stats = CombatStats(atk = 80, def = 20, mat = 30, res = 10),
            rates = CombatRates(),
        )
        assertEquals("HP 30/100 · ATK 80 · DEF 20 · MAT 30 · RES 10", combatantSummary(unit))
    }

    @Test
    fun combatantSummaryAppendsActiveAilmentsWithRemainingTurns() {
        val unit = Combatant(
            identity = CombatIdentity("u", "Hero", "cls", Faction.PLAYER),
            pos = Pos(0, 0),
            vitals = CombatVitals(hp = 30, hpMax = 100),
            stats = CombatStats(atk = 80, def = 20, mat = 30, res = 10),
            rates = CombatRates(),
            ailments = listOf(ActiveAilment(Ailment.SILENCE, 2)),
        )
        assertEquals("HP 30/100 · ATK 80 · DEF 20 · MAT 30 · RES 10 · 沉默 2", combatantSummary(unit))
    }

    @Test
    fun combatantSummaryAppendsActiveTimedStatEffectsWithSignAndRemaining() {
        // ADR 0008 buff/debuff legibility: a +15 ATK buff and a -20 DEF debuff show their signed delta and
        // remaining turns, so the stat number alone no longer hides that it is temporary.
        val unit = Combatant(
            identity = CombatIdentity("u", "Hero", "cls", Faction.PLAYER),
            pos = Pos(0, 0),
            vitals = CombatVitals(hp = 30, hpMax = 100),
            stats = CombatStats(atk = 95, def = 0, mat = 30, res = 10),
            rates = CombatRates(),
            effects = listOf(ActiveEffect(AffectedStat.ATK, 15, 2), ActiveEffect(AffectedStat.DEF, -20, 1)),
        )
        assertEquals("HP 30/100 · ATK 95 · DEF 0 · MAT 30 · RES 10 · ATK +15 (2) · DEF -20 (1)", combatantSummary(unit))
    }

    private fun unitWith(
        ailments: List<ActiveAilment> = emptyList(),
        effects: List<ActiveEffect> = emptyList(),
    ): Combatant = Combatant(
        identity = CombatIdentity("u", "Hero", "cls", Faction.PLAYER),
        pos = Pos(0, 0),
        vitals = CombatVitals(hp = 100, hpMax = 100),
        stats = CombatStats(atk = 80, def = 20, mat = 30, res = 10),
        rates = CombatRates(),
        effects = effects,
        ailments = ailments,
    )

    @Test
    fun statusChipsCondenseConditionsAndFlagHostileState() {
        // Silence + a buff + a debuff → "沉" + "↑" + "↓"; any ailment/debuff makes it a hostile (orange) state.
        val afflicted = unitWith(
            ailments = listOf(ActiveAilment(Ailment.SILENCE, 2)),
            effects = listOf(ActiveEffect(AffectedStat.ATK, 15, 2), ActiveEffect(AffectedStat.DEF, -20, 1)),
        )
        assertEquals("沉↑↓", statusChips(afflicted))
        assertTrue(hasHostileStatus(afflicted))
        // A buff-only unit shows just "↑" and is NOT hostile (blue chip).
        val buffed = unitWith(effects = listOf(ActiveEffect(AffectedStat.ATK, 15, 2)))
        assertEquals("↑", statusChips(buffed))
        assertFalse(hasHostileStatus(buffed))
    }

    @Test
    fun statusChipsAreEmptyForACleanUnit() {
        assertEquals("", statusChips(unitWith()))
        assertFalse(hasHostileStatus(unitWith()))
    }

    @Test
    fun combatantSummaryLabelsAStunAilment() {
        val unit = Combatant(
            identity = CombatIdentity("u", "Hero", "cls", Faction.PLAYER),
            pos = Pos(0, 0),
            vitals = CombatVitals(hp = 30, hpMax = 100),
            stats = CombatStats(atk = 80, def = 20, mat = 30, res = 10),
            rates = CombatRates(),
            ailments = listOf(ActiveAilment(Ailment.STUN, 1)),
        )
        assertEquals("HP 30/100 · ATK 80 · DEF 20 · MAT 30 · RES 10 · 麻痹 1", combatantSummary(unit))
    }
}
