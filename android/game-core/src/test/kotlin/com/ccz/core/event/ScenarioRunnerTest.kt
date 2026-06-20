package com.ccz.core.event

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScenarioRunnerTest {
    @Test
    fun linearPresentationOpsEmittedInOrder() {
        val ops = listOf(
            ScenarioOp.Dialogue(DialogueLine(text = "hi")),
            ScenarioOp.Portrait("zhaoyun", emotion = "smile"),
            ScenarioOp.Wait(2),
            ScenarioOp.PlayBgm("theme"),
            ScenarioOp.SceneTransition("camp"),
            ScenarioOp.FadeIn,
            ScenarioOp.FadeOut,
        )

        val pb = ScenarioRunner.run(rScript(ops))

        assertEquals(ops, pb.events) // all 7 presentation-op kinds emitted in order
        assertTrue(pb.vars.isEmpty())
        assertNull(pb.pausedAt)
        assertFalse(pb.haltedOnBudget)
    }

    @Test
    fun setVarEvolvesVarsAndIsNotEmitted() {
        val pb = ScenarioRunner.run(rScript(listOf(ScenarioOp.SetVar("flag", 3), ScenarioOp.FadeIn)))

        assertEquals(3, pb.vars["flag"])
        assertEquals(listOf(ScenarioOp.FadeIn), pb.events) // SetVar consumed as control flow, not emitted
    }

    @Test
    fun branchTakenJumpsToLabelSkippingInterveningOps() {
        val pb = ScenarioRunner.run(
            rScript(
                listOf(
                    ScenarioOp.SetVar("go", 1),
                    ScenarioOp.Branch(variable = "go", equals = 1, target = "end"),
                    ScenarioOp.Dialogue(DialogueLine(text = "skipped")),
                    ScenarioOp.Label("end"),
                    ScenarioOp.FadeOut,
                ),
            ),
        )

        assertEquals(listOf(ScenarioOp.FadeOut), pb.events) // dialogue between branch and label skipped
    }

    @Test
    fun branchNotTakenFallsThroughAndUnsetVarReadsZero() {
        val shown = ScenarioOp.Dialogue(DialogueLine(text = "shown"))
        val pb = ScenarioRunner.run(
            rScript(
                listOf(
                    ScenarioOp.Branch(variable = "go", equals = 1, target = "end"), // go unset = 0 != 1
                    shown,
                    ScenarioOp.Label("end"),
                ),
            ),
        )

        assertEquals(listOf(shown), pb.events)
    }

    @Test
    fun initialVarsDriveBranch() {
        val pb = ScenarioRunner.run(
            rScript(
                listOf(
                    ScenarioOp.Branch(variable = "go", equals = 1, target = "end"),
                    ScenarioOp.Dialogue(DialogueLine(text = "skipped")),
                    ScenarioOp.Label("end"),
                    ScenarioOp.FadeOut,
                ),
            ),
            vars = mapOf("go" to 1),
        )

        assertEquals(listOf(ScenarioOp.FadeOut), pb.events) // injected var makes the branch jump
        assertEquals(1, pb.vars["go"])
    }

    @Test
    fun choicePausesBeforeLaterOps() {
        val before = ScenarioOp.Dialogue(DialogueLine(text = "before"))
        val choice = ScenarioOp.Choice(prompt = "pick", options = listOf(ChoiceOption(text = "a")))
        val pb = ScenarioRunner.run(
            // SetVar before the choice must be reflected in the paused snapshot (replay resumes from it)
            rScript(listOf(before, ScenarioOp.SetVar("k", 9), choice, ScenarioOp.FadeOut)), // FadeOut must not run
        )

        assertEquals(listOf(before), pb.events)
        assertEquals(choice, pb.pausedAt)
        assertEquals(9, pb.vars["k"]) // vars consumed before the pause are snapshotted for resume
        assertFalse(pb.haltedOnBudget)
    }

    @Test
    fun unconditionalBackBranchHaltsOnBudget() {
        val pb = ScenarioRunner.run(
            rScript(
                listOf(
                    ScenarioOp.Label("loop"),
                    ScenarioOp.Branch(variable = "x", equals = 0, target = "loop"), // x always 0 -> infinite
                ),
            ),
        )

        assertTrue(pb.haltedOnBudget)
        assertTrue(pb.events.isEmpty())
        assertNull(pb.pausedAt)
    }

    @Test
    fun branchToUnknownTargetFallsThroughWithoutJumpOrCrash() {
        val after = ScenarioOp.Dialogue(DialogueLine(text = "after"))
        val pb = ScenarioRunner.run(
            rScript(
                listOf(
                    // condition met but target label absent (validator guards content; runner must fail-safe)
                    ScenarioOp.Branch(variable = "go", equals = 0, target = "nonexistent"),
                    after,
                ),
            ),
        )

        assertEquals(listOf(after), pb.events) // unresolvable target => no jump, fall through (not a crash)
        assertFalse(pb.haltedOnBudget)
        assertNull(pb.pausedAt)
    }

    @Test
    fun legalBackJumpConvergesWithoutHalt() {
        val body = ScenarioOp.Dialogue(DialogueLine(text = "body"))
        val pb = ScenarioRunner.run(
            rScript(
                listOf(
                    ScenarioOp.Label("top"), // 0
                    ScenarioOp.Branch(variable = "loops", equals = 1, target = "done"), // 1: exits on 2nd pass
                    body, // 2
                    ScenarioOp.SetVar("loops", 1), // 3
                    ScenarioOp.Branch(variable = "always", equals = 0, target = "top"), // 4: back-jump once
                    ScenarioOp.Label("done"), // 5
                    ScenarioOp.FadeOut, // 6
                ),
            ),
        )

        assertEquals(listOf(body, ScenarioOp.FadeOut), pb.events) // body once, then exit — finite back-jump
        assertFalse(pb.haltedOnBudget) // a legal finite back-jump must NOT trip the loop guard
    }

    @Test
    fun emptyScriptYieldsEmptyPlayback() {
        val pb = ScenarioRunner.run(rScript(emptyList()))

        assertTrue(pb.events.isEmpty())
        assertTrue(pb.vars.isEmpty())
        assertNull(pb.pausedAt)
        assertFalse(pb.haltedOnBudget)
    }

    @Test
    fun runIsDeterministicForSameInput() {
        val script = rScript(
            listOf(
                ScenarioOp.SetVar("a", 2),
                ScenarioOp.Branch(variable = "a", equals = 2, target = "skip"),
                ScenarioOp.Dialogue(DialogueLine(text = "x")),
                ScenarioOp.Label("skip"),
                ScenarioOp.FadeOut,
            ),
        )

        assertEquals(ScenarioRunner.run(script), ScenarioRunner.run(script))
    }

    @Test
    fun choiceSelectsOptionAppliesSetVarsAndGoto() {
        val choice = ScenarioOp.Choice(
            prompt = "pick",
            options = listOf(
                ChoiceOption(text = "left", goto = "left_path", setVars = mapOf("path" to 0)),
                ChoiceOption(text = "right", goto = "right_path", setVars = mapOf("path" to 1)),
            ),
        )
        val script = rScript(
            listOf(
                choice, // 0
                ScenarioOp.Label("left_path"), // 1
                ScenarioOp.Dialogue(DialogueLine(text = "left")), // 2
                ScenarioOp.Label("right_path"), // 3
                ScenarioOp.Dialogue(DialogueLine(text = "right")), // 4
            ),
        )

        val pb = ScenarioRunner.run(script, choices = listOf(1)) // pick "right"

        assertEquals(listOf(ScenarioOp.Dialogue(DialogueLine(text = "right"))), pb.events)
        assertEquals(1, pb.vars["path"]) // chosen option's setVars applied
        assertNull(pb.pausedAt)
    }

    @Test
    fun choiceWithoutGotoFallsThroughInOrder() {
        val choice = ScenarioOp.Choice(prompt = "?", options = listOf(ChoiceOption(text = "stay"))) // goto null

        val pb = ScenarioRunner.run(rScript(listOf(choice, ScenarioOp.FadeOut)), choices = listOf(0))

        assertEquals(listOf(ScenarioOp.FadeOut), pb.events) // no goto => fall through to next op
        assertNull(pb.pausedAt)
    }

    @Test
    fun multipleChoicesConsumedInSequence() {
        val first = ScenarioOp.Choice("1", listOf(ChoiceOption(text = "a", setVars = mapOf("x" to 1))))
        val second = ScenarioOp.Choice("2", listOf(ChoiceOption(text = "b", setVars = mapOf("y" to 2))))

        val pb = ScenarioRunner.run(
            rScript(listOf(first, ScenarioOp.Wait(1), second, ScenarioOp.FadeOut)),
            choices = listOf(0, 0),
        )

        assertEquals(listOf(ScenarioOp.Wait(1), ScenarioOp.FadeOut), pb.events)
        assertEquals(1, pb.vars["x"])
        assertEquals(2, pb.vars["y"])
    }

    @Test
    fun exhaustedChoicesPauseAtNextChoice() {
        val first = ScenarioOp.Choice("1", listOf(ChoiceOption(text = "a")))
        val second = ScenarioOp.Choice("2", listOf(ChoiceOption(text = "b")))

        val pb = ScenarioRunner.run(
            rScript(listOf(first, ScenarioOp.FadeIn, second, ScenarioOp.FadeOut)),
            choices = listOf(0), // only enough for the first choice
        )

        assertEquals(listOf(ScenarioOp.FadeIn), pb.events) // ran past first choice, paused at second
        assertEquals(second, pb.pausedAt)
        assertFalse(pb.haltedOnBudget) // paused on exhausted input, not a budget halt
    }

    @Test
    fun outOfRangeChoiceIndexPausesFailSafe() {
        val choice = ScenarioOp.Choice(prompt = "?", options = listOf(ChoiceOption(text = "only")))

        val pb = ScenarioRunner.run(rScript(listOf(choice, ScenarioOp.FadeOut)), choices = listOf(5))

        assertEquals(emptyList(), pb.events) // out-of-range option => pause, FadeOut not reached
        assertEquals(choice, pb.pausedAt)
        assertFalse(pb.haltedOnBudget) // fail-safe pause, not a budget halt
    }

    @Test
    fun runWithChoicesIsDeterministic() {
        val choice = ScenarioOp.Choice(
            prompt = "pick",
            options = listOf(
                ChoiceOption(text = "a", goto = "end", setVars = mapOf("p" to 7)),
                ChoiceOption(text = "b"),
            ),
        )
        val script = rScript(
            listOf(
                choice,
                ScenarioOp.Dialogue(DialogueLine(text = "skipped")),
                ScenarioOp.Label("end"),
                ScenarioOp.FadeOut,
            ),
        )

        assertEquals(ScenarioRunner.run(script, choices = listOf(0)), ScenarioRunner.run(script, choices = listOf(0)))
    }

    @Test
    fun choiceGotoToUnknownLabelFallsThroughFailSafe() {
        // goto an unresolvable label (validator guards content; runner must fail-safe like Branch)
        val choice = ScenarioOp.Choice("?", listOf(ChoiceOption(text = "x", goto = "missing")))

        val pb = ScenarioRunner.run(rScript(listOf(choice, ScenarioOp.FadeOut)), choices = listOf(0))

        assertEquals(listOf(ScenarioOp.FadeOut), pb.events) // unresolvable goto => fall through, not a crash
        assertNull(pb.pausedAt)
        assertFalse(pb.haltedOnBudget)
    }

    @Test
    fun choiceSetVarsOverwriteExistingVar() {
        val choice = ScenarioOp.Choice("?", listOf(ChoiceOption(text = "x", setVars = mapOf("path" to 1))))

        val pb = ScenarioRunner.run(
            rScript(listOf(choice)),
            vars = mapOf("path" to 99), // pre-existing var the option's setVars must overwrite
            choices = listOf(0),
        )

        assertEquals(1, pb.vars["path"]) // putAll overwrites the prior value
    }

    @Test
    fun negativeChoiceIndexPausesFailSafe() {
        val choice = ScenarioOp.Choice("?", listOf(ChoiceOption(text = "only")))

        val pb = ScenarioRunner.run(rScript(listOf(choice, ScenarioOp.FadeOut)), choices = listOf(-1))

        assertEquals(emptyList(), pb.events) // negative index resolves to no option => pause
        assertEquals(choice, pb.pausedAt)
    }

    private fun rScript(ops: List<ScenarioOp>): RScript = RScript(id = "r", ops = ops)
}
