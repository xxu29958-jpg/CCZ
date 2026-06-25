package com.ccz.contentpack.json

import com.ccz.core.model.AffectedStat
import com.ccz.core.model.Ailment
import com.ccz.core.model.CounterRelation
import com.ccz.core.model.DamageKind
import com.ccz.core.model.EffectTarget
import com.ccz.core.model.Faction
import com.ccz.core.model.HealMode

/**
 * Enum decode boundary: JSON strings are matched (case-insensitively) against the
 * known enum names and rejected fail-closed on anything unknown. This is where the
 * "unknown enum" content gate lives (CCZ_ENGINE_RULES: unknown enum 在解码边界强制).
 */
internal fun decodeFaction(path: String, value: String): Faction =
    Faction.entries.firstOrNull { it.name == value.uppercase() }
        ?: throw ContentDecodeException("$path: unknown faction: $value")

internal fun decodeDamageKind(path: String, value: String): DamageKind =
    DamageKind.entries.firstOrNull { it.name == value.uppercase() }
        ?: throw ContentDecodeException("$path: unknown damage kind: $value")

internal fun decodeCounterRelation(path: String, value: String): CounterRelation =
    CounterRelation.entries.firstOrNull { it.name == value.uppercase() }
        ?: throw ContentDecodeException("$path: unknown counter relation: $value")

internal fun decodeEffectTarget(path: String, value: String): EffectTarget =
    EffectTarget.entries.firstOrNull { it.name == value.uppercase() }
        ?: throw ContentDecodeException("$path: unknown effect target: $value")

internal fun decodeHealMode(path: String, value: String): HealMode =
    HealMode.entries.firstOrNull { it.name == value.uppercase() }
        ?: throw ContentDecodeException("$path: unknown heal mode: $value")

internal fun decodeAffectedStat(path: String, value: String): AffectedStat =
    AffectedStat.entries.firstOrNull { it.name == value.uppercase() }
        ?: throw ContentDecodeException("$path: unknown stat: $value")

internal fun decodeAilment(path: String, value: String): Ailment =
    Ailment.entries.firstOrNull { it.name == value.uppercase() }
        ?: throw ContentDecodeException("$path: unknown ailment: $value")
