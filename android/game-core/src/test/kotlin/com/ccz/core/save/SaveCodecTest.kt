package com.ccz.core.save

import com.ccz.core.battle.BattleOutcome
import com.ccz.core.battle.BattleProgress
import com.ccz.core.battle.BattleState
import com.ccz.core.battle.Command
import com.ccz.core.model.AccuracyRates
import com.ccz.core.model.BurstRates
import com.ccz.core.model.CombatIdentity
import com.ccz.core.model.CombatRates
import com.ccz.core.model.CombatStats
import com.ccz.core.model.CombatVitals
import com.ccz.core.model.Combatant
import com.ccz.core.model.Faction
import com.ccz.core.model.Pos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class SaveCodecTest {
    @Test
    fun roundTripPreservesFullEnvelope() {
        val envelope = sampleEnvelope()

        assertEquals(envelope, SaveCodec.decode(SaveCodec.encode(envelope)))
    }

    @Test
    fun roundTripPreservesEmptyCommandsAndUnits() {
        val envelope = SaveEnvelope(
            versions = SaveVersions(1, 1, "0.1.0", "1", "1.0.0"),
            initialState = BattleState(units = emptyMap(), turn = 1, active = Faction.PLAYER, rngState = 0L),
            commands = emptyList(),
        )

        assertEquals(envelope, SaveCodec.decode(SaveCodec.encode(envelope)))
    }

    @Test
    fun roundTripPreservesWaitCommand() {
        // The Wait command (action-economy "stand") must survive the on-disk codec via its
        // @SerialName("wait") discriminator, like Move/Attack/EndTurn.
        val envelope = SaveEnvelope(
            versions = SaveVersions(1, 1, "0.1.0", "1", "1.0.0"),
            initialState = BattleState(units = emptyMap(), turn = 1, active = Faction.PLAYER, rngState = 0L),
            commands = listOf(Command.Wait("zhaoyun")),
        )

        assertEquals(envelope, SaveCodec.decode(SaveCodec.encode(envelope)))
    }

    @Test
    fun encodeIsDeterministic() {
        val envelope = sampleEnvelope()

        assertEquals(SaveCodec.encode(envelope), SaveCodec.encode(envelope))
    }

    @Test
    fun malformedJsonFailsClosed() {
        assertFailsWith<SaveDecodeException> { SaveCodec.decode("not a save {{{") }
    }

    @Test
    fun unknownKeyFailsClosed() {
        val tampered = SaveCodec.encode(sampleEnvelope()).replaceFirst("{", "{\"bogus_key\":1,")

        assertFailsWith<SaveDecodeException> { SaveCodec.decode(tampered) }
    }

    @Test
    fun unknownFactionFailsClosed() {
        val tampered = SaveCodec.encode(sampleEnvelope()).replace("PLAYER", "MARTIAN")

        assertFailsWith<SaveDecodeException> { SaveCodec.decode(tampered) }
    }

    @Test
    fun unknownOutcomeFailsClosed() {
        val tampered = SaveCodec.encode(sampleEnvelope()).replace("VICTORY", "WON")

        assertFailsWith<SaveDecodeException> { SaveCodec.decode(tampered) }
    }

    @Test
    fun unknownCommandKindFailsClosed() {
        val tampered = SaveCodec.encode(sampleEnvelope()).replace("\"move\"", "\"teleport\"")

        assertFailsWith<SaveDecodeException> { SaveCodec.decode(tampered) }
    }

    @Test
    fun missingRequiredFieldFailsClosed() {
        // drop a required field (turn has no default) — strict decode must reject, not default-fill
        val tampered = SaveCodec.encode(sampleEnvelope()).replace("\"turn\":3,", "")

        assertFailsWith<SaveDecodeException> { SaveCodec.decode(tampered) }
    }

    @Test
    fun roundTripPreservesScenarioChoices() {
        val env = sampleEnvelope()

        val decoded = SaveCodec.decode(SaveCodec.encode(env))

        assertEquals(env.scenarios, decoded.scenarios)
        assertEquals(listOf(1, 0), decoded.scenarios.first().choices) // choice order pinned
    }

    @Test
    fun decodesLegacySaveMissingScenariosFieldAsEmpty() {
        // a v1 save written before the scenario axis has no "scenarios" key — must decode as empty
        val encoded = SaveCodec.encode(sampleEnvelope().copy(scenarios = emptyList()))
        val json = encoded.replace(",\"scenarios\":[]", "")
        assertNotEquals(encoded, json) // guard: the field must actually be stripped, else this test silently degrades

        assertEquals(emptyList(), SaveCodec.decode(json).scenarios)
    }

    @Test
    fun unitMapKeyMismatchingCombatantIdFailsClosed() {
        // diverge identity.id from its units-map key: shape-valid but an incoherent roster
        val encoded = SaveCodec.encode(sampleEnvelope())
        val tampered = encoded.replaceFirst("\"id\":\"zhaoyun\"", "\"id\":\"impostor\"")
        assertNotEquals(encoded, tampered) // guard: the id must actually diverge, else the test degrades

        assertFailsWith<SaveDecodeException> { SaveCodec.decode(tampered) }
    }

    private fun sampleEnvelope(): SaveEnvelope = SaveEnvelope(
        versions = SaveVersions(
            saveSchemaVersion = 1,
            rulesVersion = 1,
            engineVersion = "0.1.0",
            nativeFormatVersion = "1",
            contentVersion = "1.0.0",
            converterVersion = "0.0.1",
        ),
        initialState = BattleState(
            units = mapOf(
                "zhaoyun" to combatant("zhaoyun", Faction.PLAYER, Pos(2, 2), hp = 180, statuses = setOf("guard")),
                "e1" to combatant("e1", Faction.ENEMY, Pos(5, 2)),
            ),
            turn = 3,
            active = Faction.ALLY,
            rngState = 123_456_789_012_345L, // exceeds Int range — pins Long fidelity
            progress = BattleProgress(
                outcome = BattleOutcome.VICTORY,
                vars = mapOf("flag" to 1),
                firedTriggers = setOf("t1"),
            ),
        ),
        commands = listOf(
            Command.Move("zhaoyun", Pos(4, 2)),
            Command.Attack("zhaoyun", "e1", "atk"),
            Command.EndTurn(Faction.PLAYER),
        ),
        scenarios = listOf(
            ScenarioReplay("intro", choices = listOf(1, 0)),
            ScenarioReplay("epilogue"), // empty choices — pins the default-empty path too
        ),
    )

    private fun combatant(
        id: String,
        faction: Faction,
        pos: Pos,
        hp: Int = 200,
        statuses: Set<String> = emptySet(),
    ): Combatant = Combatant(
        identity = CombatIdentity(id, "Name-$id", "cavalry", faction),
        pos = pos,
        vitals = CombatVitals(hp = hp, hpMax = 200),
        stats = CombatStats(atk = 180, def = 120, mat = 60, res = 90),
        rates = CombatRates(
            AccuracyRates(hit = 95, evade = 10, precision = 5, block = 3),
            BurstRates(crit = 20, critResist = 4, combo = 15, comboResist = 2),
        ),
        statuses = statuses,
    )
}
