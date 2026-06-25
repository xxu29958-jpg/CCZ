package com.ccz.core.save

import com.ccz.core.battle.BattleRules
import com.ccz.core.battle.Command
import com.ccz.core.battle.ResolveContext
import com.ccz.core.battle.Resolver
import com.ccz.core.battle.combatant
import com.ccz.core.battle.stateOf
import com.ccz.core.event.RScript
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import com.ccz.core.model.UnitClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class SaveLoaderTest {
    private val classes = mapOf("inf" to UnitClass("inf", "Infantry", "foot", 5))
    private val resolve = ResolveContext(classes)

    // Two attacks consume RNG, so replay determinism is meaningfully exercised (not just a no-op fold).
    private val commands = listOf(
        Command.Attack("h", "e", "atk"),
        Command.Attack("h", "e", "atk"),
        Command.EndTurn(Faction.PLAYER),
    )

    private fun versions(
        schema: Int = SaveVersions.SUPPORTED_SAVE_SCHEMA_VERSION,
        rules: Int = BattleRules.RULES_VERSION,
    ): SaveVersions = SaveVersions(
        saveSchemaVersion = schema,
        rulesVersion = rules,
        engineVersion = "0.1",
        nativeFormatVersion = "1",
        contentVersion = "0.1.0",
    )

    private fun envelope(versions: SaveVersions = versions()): SaveEnvelope =
        SaveEnvelope(
            versions = versions,
            initialState = stateOf(
                combatant("h", Faction.PLAYER, Pos(0, 0)),
                combatant("e", Faction.ENEMY, Pos(1, 0)),
            ),
            commands = commands,
        )

    @Test
    fun rejectsFutureSaveSchemaVersion() {
        val future = versions(schema = SaveVersions.SUPPORTED_SAVE_SCHEMA_VERSION + 1)
        assertEquals(SaveRejection.FUTURE_SCHEMA_VERSION, SaveLoader.check(future))
        val outcome = SaveLoader.load(envelope(future), resolve)
        assertEquals(SaveRejection.FUTURE_SCHEMA_VERSION, assertIs<SaveLoader.Outcome.Rejected>(outcome).reason)
    }

    @Test
    fun rejectsRulesVersionMismatch() {
        val drifted = versions(rules = BattleRules.RULES_VERSION + 1)
        assertEquals(SaveRejection.RULES_VERSION_MISMATCH, SaveLoader.check(drifted))
        val outcome = SaveLoader.load(envelope(drifted), resolve) // also exercise the full load() gate
        assertEquals(SaveRejection.RULES_VERSION_MISMATCH, assertIs<SaveLoader.Outcome.Rejected>(outcome).reason)
    }

    @Test
    fun acceptsCurrentAndOlderSchemaButNotFuture() {
        assertEquals(null, SaveLoader.check(versions()))
        // only a FUTURE schema is rejected; an older one is loadable (migration is a later concern)
        assertEquals(null, SaveLoader.check(versions(schema = SaveVersions.SUPPORTED_SAVE_SCHEMA_VERSION - 1)))
        assertIs<SaveLoader.Outcome.Loaded>(SaveLoader.load(envelope(), resolve))
    }

    @Test
    fun replayReproducesManualResolverRun() {
        val env = envelope()
        var manual = env.initialState
        commands.forEach { manual = Resolver.apply(manual, it, resolve).state }
        val loaded = assertIs<SaveLoader.Outcome.Loaded>(SaveLoader.load(env, resolve))
        assertEquals(manual, loaded.finalState)
        // prove the replay actually advanced RNG (not a no-op fold that would pass trivially)
        assertNotEquals(env.initialState.rngState, loaded.finalState.rngState)
    }

    @Test
    fun replayIsDeterministicAcrossTwoLoads() {
        val env = envelope()
        val first = assertIs<SaveLoader.Outcome.Loaded>(SaveLoader.load(env, resolve))
        val second = assertIs<SaveLoader.Outcome.Loaded>(SaveLoader.load(env, resolve))
        assertEquals(first.finalState, second.finalState)
    }

    @Test
    fun rejectsCommandReferencingAbsentUnitGracefully() {
        val env = envelope().copy(commands = listOf(Command.Attack("ghost", "e", "atk")))
        val outcome = SaveLoader.load(env, resolve) // clean rejection, not a mid-replay exception
        assertEquals(SaveRejection.CORRUPT_COMMAND, assertIs<SaveLoader.Outcome.Rejected>(outcome).reason)
    }

    @Test
    fun rejectsCommandReferencingAbsentSkillGracefully() {
        val env = envelope().copy(commands = listOf(Command.Attack("h", "e", "no_such_skill")))
        val outcome = SaveLoader.load(env, resolve)
        assertEquals(SaveRejection.CORRUPT_COMMAND, assertIs<SaveLoader.Outcome.Rejected>(outcome).reason)
    }

    @Test
    fun rejectsMoveReferencingAbsentUnitGracefully() {
        // valid version, so the Move integrity branch is actually exercised (not version-short-circuited)
        val env = envelope().copy(commands = listOf(Command.Move("ghost", Pos(1, 1))))
        val outcome = SaveLoader.load(env, resolve)
        assertEquals(SaveRejection.CORRUPT_COMMAND, assertIs<SaveLoader.Outcome.Rejected>(outcome).reason)
    }

    @Test
    fun rejectsAttackReferencingAbsentTargetGracefully() {
        val env = envelope().copy(commands = listOf(Command.Attack("h", "ghost", "atk")))
        val outcome = SaveLoader.load(env, resolve)
        assertEquals(SaveRejection.CORRUPT_COMMAND, assertIs<SaveLoader.Outcome.Rejected>(outcome).reason)
    }

    @Test
    fun rejectsCastReferencingAbsentUnitGracefully() {
        // The Cast integrity branch (ADR 0008) mirrors Attack: a caster/target/skill ref absent from the
        // initial roster or skill table is corrupt and rejected cleanly before the replay fold.
        val env = envelope().copy(commands = listOf(Command.Cast("ghost", "e", "atk")))
        val outcome = SaveLoader.load(env, resolve)
        assertEquals(SaveRejection.CORRUPT_COMMAND, assertIs<SaveLoader.Outcome.Rejected>(outcome).reason)
    }

    @Test
    fun rejectsWaitReferencingAbsentUnitGracefully() {
        // The Wait integrity branch mirrors Move: a unit ref absent from the initial roster is corrupt.
        val env = envelope().copy(commands = listOf(Command.Wait("ghost")))
        val outcome = SaveLoader.load(env, resolve)
        assertEquals(SaveRejection.CORRUPT_COMMAND, assertIs<SaveLoader.Outcome.Rejected>(outcome).reason)
    }

    @Test
    fun acceptsEmptyCommandEnvelope() {
        val env = envelope().copy(commands = emptyList())
        assertIs<SaveLoader.Outcome.Loaded>(SaveLoader.load(env, resolve))
    }

    @Test
    fun versionGatePrecedesCommandIntegrity() {
        // both a future schema AND a corrupt command — the version gate is checked first
        val future = versions(schema = SaveVersions.SUPPORTED_SAVE_SCHEMA_VERSION + 1)
        val env = envelope(future).copy(commands = listOf(Command.Move("ghost", Pos(1, 1))))
        val outcome = SaveLoader.load(env, resolve)
        assertEquals(SaveRejection.FUTURE_SCHEMA_VERSION, assertIs<SaveLoader.Outcome.Rejected>(outcome).reason)
    }

    @Test
    fun rejectsScenarioReferencingAbsentScriptGracefully() {
        val env = envelope().copy(scenarios = listOf(ScenarioReplay("missing")))
        val outcome = SaveLoader.load(env, resolve, scripts = emptyMap())
        assertEquals(SaveRejection.CORRUPT_SCENARIO, assertIs<SaveLoader.Outcome.Rejected>(outcome).reason)
    }

    @Test
    fun acceptsScenarioReferencingKnownScript() {
        val env = envelope().copy(scenarios = listOf(ScenarioReplay("intro")))
        val scripts = mapOf("intro" to RScript("intro", emptyList()))
        assertIs<SaveLoader.Outcome.Loaded>(SaveLoader.load(env, resolve, scripts = scripts))
    }

    @Test
    fun commandIntegrityPrecedesScenarioIntegrity() {
        // both a corrupt command AND an absent-script scenario — the command gate is checked first
        val env = envelope().copy(
            commands = listOf(Command.Move("ghost", Pos(1, 1))),
            scenarios = listOf(ScenarioReplay("missing")),
        )
        val outcome = SaveLoader.load(env, resolve, scripts = emptyMap())
        assertEquals(SaveRejection.CORRUPT_COMMAND, assertIs<SaveLoader.Outcome.Rejected>(outcome).reason)
    }
}
