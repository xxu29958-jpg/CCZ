package com.ccz.core.save

import com.ccz.core.battle.BattleOutcome
import com.ccz.core.battle.BattleProgress
import com.ccz.core.battle.BattleState
import com.ccz.core.battle.Command
import com.ccz.core.model.AccuracyRates
import com.ccz.core.model.ActiveAilment
import com.ccz.core.model.ActiveEffect
import com.ccz.core.model.AffectedStat
import com.ccz.core.model.Ailment
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
        // progress.moved/acted (the per-turn action economy) are intentionally NOT serialized: the only
        // state the save captures is a fresh battle start where both are empty, and replay re-derives
        // them by folding the command stream. If a "save mid-battle" feature ever captures a non-fresh
        // state, this drops the economy silently (an exhausted unit would reload able to act) — that
        // feature must persist moved/acted (schema bump) or assert the state is fresh before encoding here.
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
        effects = c.effects.map { ActiveEffectDto(it.stat.name, it.amount, it.remaining) },
        ailments = c.ailments.map { ActiveAilmentDto(it.kind.name, it.remaining) },
    )

    private fun commandDto(c: Command): CommandDto = when (c) {
        is Command.Move -> CommandDto.Move(c.unit, PosDto(c.to.x, c.to.y))
        is Command.Attack -> CommandDto.Attack(c.attacker, c.target, c.skill)
        is Command.Cast -> CommandDto.Cast(c.caster, c.target, c.skill)
        is Command.Wait -> CommandDto.Wait(c.unit)
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
        units = s.units.mapValues { (key, dto) -> checkedCombatant(key, dto) },
        turn = s.turn,
        active = faction("initial_state.active", s.active),
        rngState = s.rngState,
        progress = BattleProgress(outcome("initial_state.progress.outcome", s.progress.outcome), s.progress.vars, s.progress.firedTriggers),
    )

    /**
     * The units map key and the combatant's own [Combatant.id] are one identifier in the
     * domain ([BattleState.unit] reads by key, `withUnit` re-keys by `unit.id`). A save whose
     * key diverges from the wrapped id is shape-valid but incoherent — it would round-trip,
     * pass command integrity (which checks the map keys), then alias/duplicate the unit on
     * replay. Reject it at the decode boundary, fail-closed like the faction/outcome whitelist.
     */
    private fun checkedCombatant(key: String, dto: CombatantDto): Combatant {
        val unit = combatant(dto)
        if (key != unit.identity.id) {
            throw SaveDecodeException("initial_state.units: map key '$key' != combatant id '${unit.identity.id}'")
        }
        return unit
    }

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
        effects = c.effects.map { ActiveEffect(affectedStat("combatant.effects.stat", it.stat), it.amount, it.remaining) },
        ailments = c.ailments.map { ActiveAilment(ailmentKind("combatant.ailments.kind", it.kind), it.remaining) },
    )

    private fun command(c: CommandDto): Command = when (c) {
        is CommandDto.Move -> Command.Move(c.unit, Pos(c.to.x, c.to.y))
        is CommandDto.Attack -> Command.Attack(c.attacker, c.target, c.skill)
        is CommandDto.Cast -> Command.Cast(c.caster, c.target, c.skill)
        is CommandDto.Wait -> Command.Wait(c.unit)
        is CommandDto.EndTurn -> Command.EndTurn(faction("command.end_turn.faction", c.faction))
    }

    private fun faction(path: String, value: String): Faction =
        Faction.entries.firstOrNull { it.name == value }
            ?: throw SaveDecodeException("$path: unknown faction: $value")

    private fun outcome(path: String, value: String): BattleOutcome =
        BattleOutcome.entries.firstOrNull { it.name == value }
            ?: throw SaveDecodeException("$path: unknown outcome: $value")

    private fun affectedStat(path: String, value: String): AffectedStat =
        AffectedStat.entries.firstOrNull { it.name == value }
            ?: throw SaveDecodeException("$path: unknown stat: $value")

    private fun ailmentKind(path: String, value: String): Ailment =
        Ailment.entries.firstOrNull { it.name == value }
            ?: throw SaveDecodeException("$path: unknown ailment: $value")
}
