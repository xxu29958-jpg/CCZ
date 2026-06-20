package com.ccz.core.save

import com.ccz.core.event.ChoiceOption
import com.ccz.core.event.DialogueLine
import com.ccz.core.event.RScript
import com.ccz.core.event.ScenarioOp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ScenarioReplayerTest {
    @Test
    fun replaysScenariosToCompletedPlaybacks() {
        val intro = RScript(
            "intro",
            listOf(
                ScenarioOp.Dialogue(DialogueLine(text = "hi")),
                ScenarioOp.Choice("pick", listOf(ChoiceOption("a", goto = "end"), ChoiceOption("b"))),
                ScenarioOp.Dialogue(DialogueLine(text = "skipped")), // skipped when choice "a" jumps to end
                ScenarioOp.Label("end"),
                ScenarioOp.FadeOut,
            ),
        )
        val scenarios = listOf(ScenarioReplay("intro", choices = listOf(0))) // pick "a" -> goto end

        val outcome = ScenarioReplayer.replay(scenarios, mapOf("intro" to intro))

        val replayed = assertIs<ScenarioReplayer.Outcome.Replayed>(outcome)
        assertEquals(1, replayed.playbacks.size)
        assertEquals(
            listOf(ScenarioOp.Dialogue(DialogueLine(text = "hi")), ScenarioOp.FadeOut),
            replayed.playbacks.first().events, // proves the recorded choice actually drove execution
        )
    }

    @Test
    fun unknownScriptRejected() {
        val outcome = ScenarioReplayer.replay(listOf(ScenarioReplay("missing")), emptyMap())

        assertEquals(ScenarioRejection.UNKNOWN_SCRIPT, assertIs<ScenarioReplayer.Outcome.Rejected>(outcome).reason)
    }

    @Test
    fun choicesExhaustedAtChoiceRejectedAsIncomplete() {
        // a Choice with no recorded choice left -> ScenarioRunner pauses -> incomplete replay
        val script = RScript("s", listOf(ScenarioOp.Choice("pick", listOf(ChoiceOption("a")))))

        val outcome = ScenarioReplayer.replay(listOf(ScenarioReplay("s", choices = emptyList())), mapOf("s" to script))

        assertEquals(ScenarioRejection.INCOMPLETE_REPLAY, assertIs<ScenarioReplayer.Outcome.Rejected>(outcome).reason)
    }

    @Test
    fun branchLoopRejectedAsIncomplete() {
        // a script that loops past the step budget -> haltedOnBudget -> incomplete replay
        val script = RScript(
            "loop",
            listOf(
                ScenarioOp.Label("top"),
                ScenarioOp.Branch("x", 0, "top"), // x unset = 0 -> always jumps back
            ),
        )

        val outcome = ScenarioReplayer.replay(listOf(ScenarioReplay("loop")), mapOf("loop" to script))

        assertEquals(ScenarioRejection.INCOMPLETE_REPLAY, assertIs<ScenarioReplayer.Outcome.Rejected>(outcome).reason)
    }

    @Test
    fun emptyScenariosReplayEmpty() {
        val outcome = ScenarioReplayer.replay(emptyList(), emptyMap())

        assertEquals(emptyList(), assertIs<ScenarioReplayer.Outcome.Replayed>(outcome).playbacks)
    }

    @Test
    fun replaysMultipleScenariosInOrder() {
        val intro = RScript("intro", listOf(ScenarioOp.Dialogue(DialogueLine(text = "intro")), ScenarioOp.FadeOut))
        val outro = RScript("outro", listOf(ScenarioOp.Dialogue(DialogueLine(text = "outro"))))
        val scenarios = listOf(ScenarioReplay("intro"), ScenarioReplay("outro"))

        val outcome = ScenarioReplayer.replay(scenarios, mapOf("intro" to intro, "outro" to outro))

        val replayed = assertIs<ScenarioReplayer.Outcome.Replayed>(outcome)
        assertEquals(2, replayed.playbacks.size)
        assertEquals(
            listOf(ScenarioOp.Dialogue(DialogueLine(text = "intro")), ScenarioOp.FadeOut),
            replayed.playbacks[0].events, // playbacks track scenarios order
        )
        assertEquals(listOf(ScenarioOp.Dialogue(DialogueLine(text = "outro"))), replayed.playbacks[1].events)
    }

    @Test
    fun secondScenarioUnknownShortCircuits() {
        val ok = RScript("ok", listOf(ScenarioOp.FadeOut))
        // first resolves, second names an unknown script -> whole batch rejected, no partial playbacks leaked
        val scenarios = listOf(ScenarioReplay("ok"), ScenarioReplay("missing"))

        val outcome = ScenarioReplayer.replay(scenarios, mapOf("ok" to ok))

        assertEquals(ScenarioRejection.UNKNOWN_SCRIPT, assertIs<ScenarioReplayer.Outcome.Rejected>(outcome).reason)
    }

    @Test
    fun surplusChoicesAreToleratedAndReplayCompletes() {
        // more recorded choices than the script has Choice ops -> extras ignored, replay still completes
        val script = RScript(
            "s",
            listOf(ScenarioOp.Choice("pick", listOf(ChoiceOption("a"))), ScenarioOp.FadeOut),
        )
        val scenarios = listOf(ScenarioReplay("s", choices = listOf(0, 0, 0))) // 3 choices, 1 Choice op

        val outcome = ScenarioReplayer.replay(scenarios, mapOf("s" to script))

        assertEquals(listOf(ScenarioOp.FadeOut), assertIs<ScenarioReplayer.Outcome.Replayed>(outcome).playbacks.first().events)
    }
}
