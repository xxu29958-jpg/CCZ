package com.ccz.app.campaign

import com.ccz.core.battle.BattleRules
import com.ccz.core.battle.Command
import com.ccz.core.event.ScenarioOp
import com.ccz.core.model.Pos
import com.ccz.core.save.SaveEnvelope
import com.ccz.core.save.SaveRejection
import com.ccz.core.save.SaveVersions
import com.ccz.core.save.ScenarioRejection
import com.ccz.core.save.ScenarioReplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the app composition-layer replay wiring over the REAL campaign ([CampaignRuntime]): the bundled
 * battle pack + R-script table feed game-core's two replay axes, while failures stay fail-closed and
 * return no partial battle/cutscene result.
 */
class CampaignReplayDriverTest {
    private fun envelope(
        commands: List<Command> = emptyList(),
        scenarios: List<ScenarioReplay> = emptyList(),
    ): SaveEnvelope {
        val manifest = CampaignRuntime.battlePack().manifest
        return SaveEnvelope(
            versions = SaveVersions(
                saveSchemaVersion = SaveVersions.SUPPORTED_SAVE_SCHEMA_VERSION,
                rulesVersion = BattleRules.RULES_VERSION,
                engineVersion = "0.1.0",
                nativeFormatVersion = manifest.nativeFormatVersion,
                contentVersion = manifest.contentVersion,
            ),
            initialState = CampaignRuntime.initialState(),
            commands = commands,
            scenarios = scenarios,
        )
    }

    @Test
    fun replaysBattleCommandsAndScenarioChoicesFromTheRealCampaign() {
        val outcome = CampaignReplayDriver.load(
            envelope(
                // 关羽 (hero_2) at (1,4) steps to the empty 荒地 (1,3), then stands down (move-then-act).
                commands = listOf(Command.Move("hero_2", Pos(1, 3)), Command.Wait("hero_2")),
                scenarios = listOf(ScenarioReplay(CampaignRuntime.INTRO_SCRIPT_ID, choices = listOf(0))),
            ),
        ) as CampaignReplayDriver.Outcome.Replayed

        assertEquals(Pos(1, 3), outcome.battleState.units.getValue("hero_2").pos)
        assertTrue(outcome.battleState.hasMoved("hero_2"))
        assertTrue(outcome.battleState.hasActed("hero_2"))
        val playback = outcome.scenarioPlaybacks.single()
        assertEquals("the recorded intro choice drives the scenario vars", 1, playback.vars["plan"])
        assertNull(playback.pausedAt)
        assertFalse(playback.haltedOnBudget)
        assertEquals(ScenarioOp.FadeOut, playback.events.last())
    }

    @Test
    fun missingScenarioScriptIsRejectedByTheSaveAxis() {
        val outcome = CampaignReplayDriver.load(envelope(scenarios = listOf(ScenarioReplay("missing"))))
            as CampaignReplayDriver.Outcome.SaveRejected

        assertEquals(SaveRejection.CORRUPT_SCENARIO, outcome.reason)
    }

    @Test
    fun incompleteScenarioChoicesAreRejectedByTheScenarioAxis() {
        val outcome = CampaignReplayDriver.load(
            envelope(scenarios = listOf(ScenarioReplay(CampaignRuntime.INTRO_SCRIPT_ID))),
        ) as CampaignReplayDriver.Outcome.ScenarioRejected

        assertEquals(ScenarioRejection.INCOMPLETE_REPLAY, outcome.reason)
    }
}
