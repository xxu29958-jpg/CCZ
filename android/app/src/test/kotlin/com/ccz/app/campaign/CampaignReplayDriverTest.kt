package com.ccz.app.campaign

import com.ccz.app.battle.CampaignContent
import com.ccz.app.battle.DemoBattle
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
 * Pins the app composition-layer replay wiring: the bundled content supplies the battle tables and
 * R-script table to game-core's two replay axes, while failures stay fail-closed and return no partial
 * battle/cutscene result.
 */
class CampaignReplayDriverTest {
    private fun envelope(
        commands: List<Command> = emptyList(),
        scenarios: List<ScenarioReplay> = emptyList(),
    ): SaveEnvelope {
        val manifest = CampaignContent.pack().manifest
        return SaveEnvelope(
            versions = SaveVersions(
                saveSchemaVersion = SaveVersions.SUPPORTED_SAVE_SCHEMA_VERSION,
                rulesVersion = BattleRules.RULES_VERSION,
                engineVersion = "0.1.0",
                nativeFormatVersion = manifest.nativeFormatVersion,
                contentVersion = manifest.contentVersion,
            ),
            initialState = DemoBattle.initialState(),
            commands = commands,
            scenarios = scenarios,
        )
    }

    @Test
    fun replaysBattleCommandsAndScenarioChoicesFromBundledContent() {
        val initial = DemoBattle.initialState()
        val outcome = CampaignReplayDriver.load(
            envelope(
                commands = listOf(
                    Command.Attack("zhang", "foe", "strike"),
                    Command.Move("guan", Pos(1, 3)),
                    Command.Wait("guan"),
                ),
                scenarios = listOf(ScenarioReplay(CampaignContent.INTRO_SCRIPT_ID, choices = listOf(0))),
            ),
        ) as CampaignReplayDriver.Outcome.Replayed

        assertTrue(outcome.battleState.units.getValue("foe").hp < initial.units.getValue("foe").hp)
        assertEquals(Pos(1, 3), outcome.battleState.units.getValue("guan").pos)
        assertTrue(outcome.battleState.hasMoved("guan"))
        assertTrue(outcome.battleState.hasActed("guan"))
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
            envelope(scenarios = listOf(ScenarioReplay(CampaignContent.INTRO_SCRIPT_ID))),
        ) as CampaignReplayDriver.Outcome.ScenarioRejected

        assertEquals(ScenarioRejection.INCOMPLETE_REPLAY, outcome.reason)
    }
}
