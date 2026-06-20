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

/**
 * Translates between the domain [SaveEnvelope] and its wire [SaveEnvelopeDto]. The
 * decode side whitelists enum strings (faction / outcome) fail-closed — an unknown
 * value throws [SaveDecodeException] rather than silently defaulting, mirroring the
 * native-content `decodeFaction` boundary.
 */
internal object SaveMappers {
    fun toDto(envelope: SaveEnvelope): SaveEnvelopeDto = SaveEnvelopeDto(
        versions = versionsDto(envelope.versions),
        initialState = stateDto(envelope.initialState),
        commands = envelope.commands.map { commandDto(it) },
        scenarios = envelope.scenarios.map { ScenarioReplayDto(it.scriptId, it.choices) },
    )

    fun toDomain(dto: SaveEnvelopeDto): SaveEnvelope = SaveEnvelope(
        versions = versions(dto.versions),
        initialState = state(dto.initialState),
        commands = dto.commands.map { command(it) },
        scenarios = dto.scenarios.map { ScenarioReplay(it.scriptId, it.choices) },
    )

    // --- encode side (domain -> DTO) ---

    private fun versionsDto(v: SaveVersions) = SaveVersionsDto(
        saveSchemaVersion = v.saveSchemaVersion,
        rulesVersion = v.rulesVersion,
        engineVersion = v.engineVersion,
        nativeFormatVersion = v.nativeFormatVersion,
        contentVersion = v.contentVersion,
        converterVersion = v.converterVersion,
    )

    private fun stateDto(s: BattleState) = BattleStateDto(
        units = s.units.mapValues { combatantDto(it.value) },
        turn = s.turn,
        active = s.active.name,
        rngState = s.rngState,
        progress = BattleProgressDto(s.progress.outcome.name, s.progress.vars, s.progress.firedTriggers),
    )

    private fun combatantDto(c: Combatant) = CombatantDto(
        identity = CombatIdentityDto(c.identity.id, c.identity.name, c.identity.classId, c.identity.faction.name),
        pos = PosDto(c.pos.x, c.pos.y),
        vitals = CombatVitalsDto(c.vitals.hp, c.vitals.hpMax),
        stats = CombatStatsDto(c.stats.atk, c.stats.def, c.stats.mat, c.stats.res),
        rates = CombatRatesDto(
            AccuracyRatesDto(c.rates.accuracy.hit, c.rates.accuracy.evade, c.rates.accuracy.precision, c.rates.accuracy.block),
            BurstRatesDto(c.rates.burst.crit, c.rates.burst.critResist, c.rates.burst.combo, c.rates.burst.comboResist),
        ),
        statuses = c.statuses,
    )

    private fun commandDto(c: Command): CommandDto = when (c) {
        is Command.Move -> CommandDto.Move(c.unit, PosDto(c.to.x, c.to.y))
        is Command.Attack -> CommandDto.Attack(c.attacker, c.target, c.skill)
        is Command.EndTurn -> CommandDto.EndTurn(c.faction.name)
    }

    // --- decode side (DTO -> domain, enums whitelisted fail-closed) ---

    private fun versions(v: SaveVersionsDto) = SaveVersions(
        saveSchemaVersion = v.saveSchemaVersion,
        rulesVersion = v.rulesVersion,
        engineVersion = v.engineVersion,
        nativeFormatVersion = v.nativeFormatVersion,
        contentVersion = v.contentVersion,
        converterVersion = v.converterVersion,
    )

    private fun state(s: BattleStateDto) = BattleState(
        units = s.units.mapValues { combatant(it.value) },
        turn = s.turn,
        active = faction("initial_state.active", s.active),
        rngState = s.rngState,
        progress = BattleProgress(outcome("initial_state.progress.outcome", s.progress.outcome), s.progress.vars, s.progress.firedTriggers),
    )

    private fun combatant(c: CombatantDto) = Combatant(
        identity = CombatIdentity(c.identity.id, c.identity.name, c.identity.classId, faction("combatant.faction", c.identity.faction)),
        pos = Pos(c.pos.x, c.pos.y),
        vitals = CombatVitals(c.vitals.hp, c.vitals.hpMax),
        stats = CombatStats(c.stats.atk, c.stats.def, c.stats.mat, c.stats.res),
        rates = CombatRates(
            AccuracyRates(c.rates.accuracy.hit, c.rates.accuracy.evade, c.rates.accuracy.precision, c.rates.accuracy.block),
            BurstRates(c.rates.burst.crit, c.rates.burst.critResist, c.rates.burst.combo, c.rates.burst.comboResist),
        ),
        statuses = c.statuses,
    )

    private fun command(c: CommandDto): Command = when (c) {
        is CommandDto.Move -> Command.Move(c.unit, Pos(c.to.x, c.to.y))
        is CommandDto.Attack -> Command.Attack(c.attacker, c.target, c.skill)
        is CommandDto.EndTurn -> Command.EndTurn(faction("command.end_turn.faction", c.faction))
    }

    private fun faction(path: String, value: String): Faction =
        Faction.entries.firstOrNull { it.name == value }
            ?: throw SaveDecodeException("$path: unknown faction: $value")

    private fun outcome(path: String, value: String): BattleOutcome =
        BattleOutcome.entries.firstOrNull { it.name == value }
            ?: throw SaveDecodeException("$path: unknown outcome: $value")
}
