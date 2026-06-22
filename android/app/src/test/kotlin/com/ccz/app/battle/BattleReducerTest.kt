package com.ccz.app.battle

import com.ccz.core.battle.BattleOutcome
import com.ccz.core.battle.BattleState
import com.ccz.core.battle.Gameplay
import com.ccz.core.model.Combatant
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the pure presentation reducer over the demo seed on the JVM (no device). Proves the
 * app's tap / skill-pick loop renders and forwards through game-core without owning combat truth:
 * the selection, its loadout, and its targets follow what game-core reports legal, and every state
 * change is the authority's, never the reducer's own arithmetic.
 */
class BattleReducerTest {
    private val context = DemoBattle.context()
    private val script = DemoBattle.script()
    private val reducer = BattleReducer(context, script)

    private fun start(): BattleUiState = reducer.initial(DemoBattle.initialState())
    private fun playerUnit(state: BattleState): Combatant = state.units.values.first { it.faction == Faction.PLAYER }
    private fun enemyUnit(state: BattleState): Combatant = state.units.values.first { it.faction == Faction.ENEMY }
    private fun select(unitId: String): BattleUiState = reducer.tapTile(start(), start().state.units.getValue(unitId).pos)

    /** A copy of [state] with the named units reduced to 0 hp (dead), to drive win/lose without RNG. */
    private fun withDead(state: BattleState, vararg ids: String): BattleState =
        state.copy(units = state.units.mapValues { (id, u) -> if (id in ids) u.copy(vitals = u.vitals.copy(hp = 0)) else u })

    @Test
    fun freshBattleIsOngoing() {
        assertEquals(BattleOutcome.ONGOING, start().outcome)
    }

    @Test
    fun routingEveryEnemySurfacesVictory() {
        // AnnihilateEnemies: both foes down → VICTORY (no RNG — outcome reads unit aliveness).
        val ui = reducer.initial(withDead(DemoBattle.initialState(), "foe", "foe2"))
        assertEquals(BattleOutcome.VICTORY, ui.outcome)
    }

    @Test
    fun protagonistDownSurfacesDefeat() {
        // ProtectAlive("guan") in the lose list: the 主将 falling decides defeat (dormant until enemy AI).
        val ui = reducer.initial(withDead(DemoBattle.initialState(), "guan"))
        assertEquals(BattleOutcome.DEFEAT, ui.outcome)
    }

    @Test
    fun aDecidedBattleIgnoresFurtherInput() {
        val won = reducer.initial(withDead(DemoBattle.initialState(), "foe", "foe2"))
        val guanPos = won.state.units.getValue("guan").pos
        assertSame("a finished battle is terminal — taps do nothing", won, reducer.tapTile(won, guanPos))
        assertSame(won, reducer.endTurn(won))
        assertSame(won, reducer.selectSkill(won, "strike"))
    }

    @Test
    fun outcomeStaysInSyncWithStateAfterAnAcceptedCommand() {
        // After any accepted command the reducer's outcome must equal the authority's verdict for the new
        // state — proving it polls Gameplay.outcome rather than holding a stale or self-decided value.
        val moved = reducer.endTurn(start())
        assertEquals(Gameplay.outcome(moved.state, script), moved.outcome)
        assertEquals(BattleOutcome.ONGOING, moved.outcome)
    }

    @Test
    fun tappingFriendlyUnitSelectsItAndExposesDestinations() {
        val ui = start()
        val player = playerUnit(ui.state)
        val after = reducer.tapTile(ui, player.pos)
        assertEquals(player.id, after.selection?.unit)
        val destinations = after.selection?.destinations ?: emptySet()
        assertTrue("own tile is a wait-in-place stop", player.pos in destinations)
        assertTrue("a selected unit has reachable tiles beyond its own", destinations.size > 1)
    }

    @Test
    fun tappingLegalDestinationMovesUnitAndKeepsItSelectedToAct() {
        val ui = start()
        val player = playerUnit(ui.state)
        val selected = reducer.tapTile(ui, player.pos)
        val destination = selected.selection!!.destinations.first { it != player.pos }
        val moved = reducer.tapTile(selected, destination)
        assertEquals("authority placed the unit on the tapped tile", destination, moved.state.units.getValue(player.id).pos)
        // Move-then-act: the unit stays selected so it can still attack or wait this turn.
        assertEquals("the moved unit stays selected", player.id, moved.selection?.unit)
        assertTrue("a moved unit has no further destinations", moved.selection!!.destinations.isEmpty())
        assertTrue("the move is logged", moved.log.size > ui.log.size)
    }

    @Test
    fun aUnitCanMoveThenAttackInOneTurn() {
        val ui = start()
        // Guan moves adjacent to the southern spearman (foe2 at (1,4)), then strikes it — move-then-act.
        val selected = reducer.tapTile(ui, ui.state.units.getValue("guan").pos)
        val moved = reducer.tapTile(selected, Pos(1, 3))
        assertEquals("guan stays selected to act after moving", "guan", moved.selection?.unit)
        assertTrue("the adjacent spearman is now a target", "foe2" in (moved.selection?.targets ?: emptySet()))
        val attacked = reducer.tapTile(moved, Pos(1, 4))
        assertTrue("move-then-attack consumed guan's action", attacked.state.hasActed("guan"))
        assertNull("after attacking, the exhausted unit is deselected", attacked.selection)
    }

    @Test
    fun waitingStandsTheSelectedUnitDown() {
        val selected = reducer.tapTile(start(), start().state.units.getValue("guan").pos)
        val waited = reducer.wait(selected)
        assertTrue("guan is exhausted after waiting", waited.state.hasActed("guan"))
        assertNull("waiting deselects", waited.selection)
    }

    @Test
    fun anExhaustedUnitCannotBeReselected() {
        val waited = reducer.wait(reducer.tapTile(start(), start().state.units.getValue("guan").pos))
        val reTap = reducer.tapTile(waited, waited.state.units.getValue("guan").pos)
        assertNull("a unit that already acted this turn cannot be selected again", reTap.selection)
    }

    @Test
    fun tappingEnemyUnitDoesNotSelectIt() {
        val ui = start()
        val after = reducer.tapTile(ui, enemyUnit(ui.state).pos)
        assertNull("the enemy is not the player's to command this turn", after.selection)
    }

    @Test
    fun endTurnRunsTheEnemyTurnAndReturnsControlToThePlayer() {
        val ui = start()
        val after = reducer.endTurn(ui)
        // endTurn now drives the whole enemy turn: player ends (→ENEMY), the AI acts, then ENEMY ends
        // (→PLAYER). Control returns to the player and the turn counter advanced for both end-turns.
        assertEquals("control returns to the player", Faction.PLAYER, after.state.active)
        assertEquals("both sides' end-turns advanced the counter", ui.state.turn + 2, after.state.turn)
    }

    @Test
    fun theEnemyTakesItsTurnAutomaticallyOnEndTurn() {
        val ui = start()
        val after = reducer.endTurn(ui)
        val enemyMoved = after.state.units.values.any {
            it.faction == Faction.ENEMY && it.pos != ui.state.units.getValue(it.id).pos
        }
        val playerHurt = after.state.units.values.any {
            it.faction == Faction.PLAYER && it.hp < ui.state.units.getValue(it.id).hp
        }
        assertTrue("the enemy advanced or attacked during its auto-driven turn", enemyMoved || playerHurt)
        assertEquals("the enemy turn completed and control returned to the player", Faction.PLAYER, after.state.active)
    }

    @Test
    fun tappingUnreachableTileWhileSelectedLeavesStateUnchanged() {
        val ui = start()
        val player = playerUnit(ui.state)
        val selected = reducer.tapTile(ui, player.pos)
        val faraway = Pos(DemoBattle.WIDTH - 1, DemoBattle.HEIGHT - 1)
        assertTrue("precondition: tile is out of move range", faraway !in (selected.selection?.destinations ?: emptySet()))
        val after = reducer.tapTile(selected, faraway)
        assertEquals("unmoved units stay put", ui.state.units, after.state.units)
        assertEquals("a non-destination tap appends nothing to the log", ui.log, after.log)
        assertNull("selection clears on an empty non-destination tap", after.selection)
    }

    @Test
    fun staleDestinationTapFailsClosedWithoutMutatingState() {
        val base = start()
        val player = playerUnit(base.state)
        // An empty, out-of-range tile: a destination set that no longer matches what the authority
        // accepts. tapTile routes it to submitMove, which must defer to the rejection.
        val illegal = Pos(DemoBattle.WIDTH - 1, DemoBattle.HEIGHT - 1)
        val stale = base.copy(selection = Selection(unit = player.id, destinations = setOf(illegal)))
        val after = reducer.tapTile(stale, illegal)
        assertEquals("the reducer never moves a unit the authority rejected", base.state.units, after.state.units)
        assertNull("selection clears after a rejected submit", after.selection)
        assertTrue("the rejection is surfaced in the log", after.log.any { it.contains("rejected") })
    }

    @Test
    fun eventLogTruncatesToMaxLines() {
        var ui = start() // one opening line
        repeat(MAX_LOG_LINES + 1) { ui = reducer.endTurn(ui) } // overflow the cap
        assertEquals("log is capped at MAX_LOG_LINES", MAX_LOG_LINES, ui.log.size)
        assertFalse("the oldest line is dropped past the cap", ui.log.any { it.startsWith("Battle start") })
    }

    @Test
    fun selectingAFrontlineUnitExposesAnInRangeEnemyAsTarget() {
        val after = select("zhang")
        assertEquals("zhang", after.selection?.unit)
        assertTrue("the adjacent enemy is reported as a legal attack target", "foe" in (after.selection?.targets ?: emptySet()))
    }

    @Test
    fun tappingAnInRangeEnemyAttacksItThroughTheAuthority() {
        val ui = start()
        val foeHpBefore = ui.state.units.getValue("foe").hp
        val selected = reducer.tapTile(ui, ui.state.units.getValue("zhang").pos)
        assertTrue("precondition: the enemy is in range", "foe" in (selected.selection?.targets ?: emptySet()))
        val attacked = reducer.tapTile(selected, selected.state.units.getValue("foe").pos)
        assertTrue(
            "the authority applied damage the reducer never computed",
            attacked.state.units.getValue("foe").hp < foeHpBefore,
        )
        assertNull("selection clears after an attack", attacked.selection)
        assertTrue("the attack is logged", attacked.log.size > ui.log.size)
    }

    @Test
    fun selectingAUnitWhoseActiveSkillReachesNoEnemyExposesNoTargets() {
        val after = select("guan") // default skill Strike (reach 1) reaches nothing from (1,2)
        assertEquals("the unit is still selectable to move", "guan", after.selection?.unit)
        assertTrue("no enemy sits within the active skill's range", (after.selection?.targets ?: emptySet()).isEmpty())
    }

    @Test
    fun staleTargetTapFailsClosedWithoutMutatingState() {
        val base = start()
        // Guan is far out of Strike range of the enemy, so a target set naming it is stale.
        // tapTile routes it to submitAttack, which must defer to the authority's rejection.
        val stale = base.copy(selection = Selection(unit = "guan", skills = listOf("strike"), selectedSkill = "strike", targets = setOf("foe")))
        val after = reducer.tapTile(stale, base.state.units.getValue("foe").pos)
        assertEquals("the reducer never damages a unit the authority rejected", base.state.units, after.state.units)
        assertNull("selection clears after a rejected submit", after.selection)
        assertTrue("the rejection is surfaced in the log", after.log.any { it.contains("rejected") })
    }

    @Test
    fun attackingSurfacesADamagedEffectMatchingTheAuthoritysHpDrop() {
        val ui = start()
        val foeHpBefore = ui.state.units.getValue("foe").hp
        val selected = reducer.tapTile(ui, ui.state.units.getValue("zhang").pos)
        val attacked = reducer.tapTile(selected, selected.state.units.getValue("foe").pos)
        val dealt = foeHpBefore - attacked.state.units.getValue("foe").hp
        val damage = attacked.effects.filterIsInstance<BattleEffect.Damaged>().single { it.unit == "foe" }
        assertEquals("the badge shows the authority's damage, not a recomputed number", dealt, damage.amount)
    }

    @Test
    fun defeatingAUnitSurfacesADefeatedEffect() {
        // With the one-action-per-turn economy, a unit strikes once; put the archer at 1 hp so zhang's
        // single (seed-deterministic) hit downs it — the same attack the test above shows lands.
        val nearDead = DemoBattle.initialState().let { s ->
            s.copy(units = s.units.mapValues { (id, u) -> if (id == "foe") u.copy(vitals = u.vitals.copy(hp = 1)) else u })
        }
        val ui = reducer.initial(nearDead)
        val selected = reducer.tapTile(ui, ui.state.units.getValue("zhang").pos)
        val attacked = reducer.tapTile(selected, selected.state.units.getValue("foe").pos)
        assertFalse("the archer is defeated by the strike", attacked.state.units.getValue("foe").alive)
        assertTrue(
            "a KO badge is surfaced for the defeated unit",
            attacked.effects.any { it is BattleEffect.Defeated && it.unit == "foe" },
        )
    }

    @Test
    fun selectingAUnitClearsEffectsFromThePriorCommand() {
        val selected = reducer.tapTile(start(), start().state.units.getValue("zhang").pos)
        val attacked = reducer.tapTile(selected, selected.state.units.getValue("foe").pos)
        assertTrue("precondition: the attack left effects to clear", attacked.effects.isNotEmpty())
        val reselected = reducer.tapTile(attacked, attacked.state.units.getValue("guan").pos)
        assertEquals("the new selection lands on guan", "guan", reselected.selection?.unit)
        assertTrue("a fresh selection drops the prior command's badges", reselected.effects.isEmpty())
    }

    @Test
    fun selectingAUnitExposesItsLoadoutAndDefaultsToTheFirstSkill() {
        val after = select("guan")
        assertEquals("guan's loadout is surfaced for the picker", listOf("strike", "spear"), after.selection?.skills)
        assertEquals("the first loadout skill is active by default", "strike", after.selection?.selectedSkill)
    }

    @Test
    fun switchingSkillRetargetsByRange() {
        val selected = select("guan")
        assertTrue("Strike (reach 1) finds no target from guan's tile", (selected.selection?.targets ?: emptySet()).isEmpty())
        val withSpear = reducer.selectSkill(selected, "spear")
        assertEquals("the active skill switches", "spear", withSpear.selection?.selectedSkill)
        assertTrue("Spear (reach 1–2) brings the spearman into range", "foe2" in (withSpear.selection?.targets ?: emptySet()))
    }

    @Test
    fun selectSkillIgnoresASkillNotInTheLoadout() {
        val selected = select("guan")
        val unchanged = reducer.selectSkill(selected, "bow") // bow is not in guan's loadout
        assertEquals("the active skill is unchanged", selected.selection?.selectedSkill, unchanged.selection?.selectedSkill)
        assertEquals("targets are unchanged", selected.selection?.targets, unchanged.selection?.targets)
    }

    @Test
    fun selectSkillIsANoOpWithoutASelection() {
        val ui = start()
        assertEquals("with nothing selected there is no skill to switch", ui, reducer.selectSkill(ui, "strike"))
    }

    @Test
    fun tappingATileHoldingOnlyADeadUnitSelectsNothing() {
        val base = start()
        val foe = base.state.units.getValue("foe")
        // A defeated unit keeps its tile in state but no longer occupies it for selection (unitAt's
        // alive filter); tapping that tile must not select the corpse.
        val withDeadFoe = base.copy(state = base.state.withUnit(foe.withHp(0)))
        val after = reducer.tapTile(withDeadFoe, foe.pos)
        assertNull("a dead unit does not occupy its tile for selection", after.selection)
    }
}
