package com.ccz.app.scenario

import com.ccz.core.event.ScenarioOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the pure scenario reducer over the demo cutscene on the JVM (no device). Proves the app's
 * advance / choose loop renders and forwards through the deterministic ScenarioRunner without owning
 * scenario truth: the events shown, the branch a choice takes, and the vars are the runner's, never the
 * reducer's; out-of-turn or out-of-range input is a fail-closed no-op, not a fabricated step.
 */
class ScenarioReducerTest {
    private val reducer = ScenarioReducer(DemoScenario.script())

    /** Advance through every currently-emitted op (stops at a pending choice or the end). */
    private fun playEmitted(ui: ScenarioUiState): ScenarioUiState {
        var s = ui
        while (s.current != null) s = reducer.advance(s)
        return s
    }

    private fun dialogueText(op: ScenarioOp?): String = (op as ScenarioOp.Dialogue).line.text

    @Test
    fun initialShowsFirstEmittedOp() {
        val ui = reducer.initial()
        assertEquals(0, ui.cursor)
        assertEquals(ScenarioOp.FadeIn, ui.current)
        assertNull("no choice before its ops are shown", ui.pausedChoice)
        assertFalse(ui.finished)
    }

    @Test
    fun emittedOpsPrecedeTheFirstChoice() {
        // FadeIn, SceneTransition, PlayBgm, Portrait, two Dialogues — then the Choice pauses (not emitted).
        assertEquals(6, reducer.initial().playback.events.size)
    }

    @Test
    fun advancingThroughEmittedOpsReachesTheChoice() {
        val atChoice = playEmitted(reducer.initial())
        assertNull("cursor consumed every emitted op", atChoice.current)
        assertEquals("如何进军？", atChoice.pausedChoice?.prompt)
        assertFalse("a pending choice is not 'finished'", atChoice.finished)
    }

    @Test
    fun advancingPastTheChoiceIsANoOp() {
        val atChoice = playEmitted(reducer.initial())
        assertSame("advance must not run past the authority's output", atChoice, reducer.advance(atChoice))
    }

    @Test
    fun choosingBeforeAwaitingIsANoOp() {
        val start = reducer.initial()
        assertSame("a choice is only valid once its ops are shown", start, reducer.choose(start, 0))
    }

    @Test
    fun outOfRangeChoiceIsANoOp() {
        val atChoice = playEmitted(reducer.initial())
        assertSame(atChoice, reducer.choose(atChoice, 5))
        assertSame(atChoice, reducer.choose(atChoice, -1))
    }

    @Test
    fun assaultChoiceTakesTheAssaultPath() {
        val resolved = reducer.choose(playEmitted(reducer.initial()), 0)
        assertEquals(1, resolved.playback.vars["plan"])
        // cursor preserved → first post-choice op is the assault line; the flank line is skipped.
        assertEquals("全军压上，一鼓作气！", dialogueText(resolved.current))
    }

    @Test
    fun flankChoiceTakesTheFlankPath() {
        val resolved = reducer.choose(playEmitted(reducer.initial()), 1)
        assertEquals(2, resolved.playback.vars["plan"])
        assertEquals("遣一军绕后，断其归路。", dialogueText(resolved.current))
    }

    @Test
    fun playsThroughToFinished() {
        val ended = playEmitted(reducer.choose(playEmitted(reducer.initial()), 0))
        assertTrue(ended.finished)
        assertFalse(ended.halted)
        assertNull(ended.pausedChoice)
    }

    @Test
    fun choosingPreservesTheAlreadyShownPrefix() {
        // The preserved-cursor design is load-bearing: choose() re-runs with a longer choice list yet
        // keeps the cursor, trusting that the deterministic runner reproduces the already-seen ops as an
        // identical prefix and only appends. Lock that contract directly (not just via the demo's paths),
        // so a future runner change that stops being append-only/deterministic fails here.
        val atChoice = playEmitted(reducer.initial())
        val shown = atChoice.playback.events
        val resolved = reducer.choose(atChoice, 0)
        assertEquals("cursor is preserved across the choice", atChoice.cursor, resolved.cursor)
        assertEquals("already-shown ops are reproduced identically", shown, resolved.playback.events.take(shown.size))
        assertTrue("resolving a choice only appends new ops", resolved.playback.events.size > shown.size)
    }

    @Test
    fun sameChoicesProduceTheSamePlayback() {
        val first = reducer.choose(playEmitted(reducer.initial()), 0).playback.events
        val second = reducer.choose(playEmitted(reducer.initial()), 0).playback.events
        assertEquals("ScenarioRunner is deterministic for a given choice list", first, second)
    }
}
