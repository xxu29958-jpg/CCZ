package com.ccz.contentpack.json

import com.ccz.contentpack.ClassCombat
import com.ccz.contentpack.ClassDef
import com.ccz.contentpack.ClassGrowth
import com.ccz.contentpack.ClassMovement
import com.ccz.contentpack.ItemDef
import com.ccz.contentpack.MapDef
import com.ccz.contentpack.MapSize
import com.ccz.contentpack.PortraitSubjectDef
import com.ccz.contentpack.RangeDef
import com.ccz.contentpack.SkillDef
import com.ccz.contentpack.SkillUse
import com.ccz.contentpack.TerrainBonuses
import com.ccz.contentpack.TerrainDef
import com.ccz.contentpack.UnitAssets
import com.ccz.contentpack.UnitDef
import com.ccz.contentpack.UnitIdentity
import com.ccz.contentpack.UnitLoadout
import com.ccz.contentpack.UnitProfile
import com.ccz.core.model.AccuracyRates
import com.ccz.core.model.BurstRates
import com.ccz.core.model.CombatRates
import com.ccz.core.model.CombatStats
import com.ccz.core.model.Pos
import com.ccz.core.model.SkillEffect

internal fun toClass(index: Int, dto: ClassDto): ClassDef {
    val path = "classes[$index]"
    return ClassDef(
        id = dto.id,
        name = dto.name,
        movement = ClassMovement(dto.movement.moveType, dto.movement.move, dto.movement.terrainCost),
        combat = ClassCombat(
            // Counter values are validated + canonicalized to the enum name (fail-closed on unknown);
            // the class-id keys are preserved. Domain stores strings (no game-core enum dep here).
            counters = dto.combat.counters.mapValues { decodeCounterRelation("$path.counters", it.value).name },
            terrainAffinity = dto.combat.terrainAffinity,
            skills = dto.combat.skills,
            growth = ClassGrowth(
                atk = dto.combat.growth.atk,
                def = dto.combat.growth.def,
                mat = dto.combat.growth.mat,
                res = dto.combat.growth.res,
                hp = dto.combat.growth.hp,
            ),
        ),
    )
}

internal fun toUnit(index: Int, dto: UnitDto): UnitDef {
    val path = "units[$index]"
    return UnitDef(
        identity = UnitIdentity(
            id = dto.identity.id,
            name = dto.identity.name,
            classId = dto.identity.classId,
            faction = decodeFaction("$path.faction", dto.identity.faction),
        ),
        profile = UnitProfile(
            level = dto.profile.level,
            hpMax = dto.profile.hpMax,
            stats = toStats(dto.profile.stats),
            rates = toRates(dto.profile.rates),
            grade = dto.profile.grade,
        ),
        loadout = UnitLoadout(skills = dto.loadout.skills, items = dto.loadout.items),
        assets = UnitAssets(portrait = dto.assets.portrait, spriteSet = dto.assets.spriteSet),
    )
}

internal fun toTerrain(dto: TerrainDto): TerrainDef =
    TerrainDef(
        id = dto.id,
        name = dto.name,
        moveCost = dto.moveCost,
        passable = dto.passable,
        bonuses = TerrainBonuses(dto.bonuses.defBonus, dto.bonuses.avoidBonus, dto.bonuses.heal),
    )

internal fun toSkill(index: Int, dto: SkillDto): SkillDef =
    SkillDef(
        id = dto.id,
        name = dto.name,
        kind = decodeDamageKind("skills[$index].kind", dto.kind),
        powerCoeff = dto.powerCoeff,
        use = SkillUse(
            range = RangeDef(min = dto.use.range.min, max = dto.use.range.max),
            area = dto.use.area,
            targeting = dto.use.targeting,
            mpCost = dto.use.mpCost,
        ),
        effects = dto.effects.mapIndexed { i, effect -> toSkillEffect("skills[$index].effects[$i]", effect) },
    )

private fun toSkillEffect(path: String, dto: SkillEffectDto): SkillEffect = when (dto) {
    // amount is carried as-is (a bound, not an enum), validated >= 1 by ContentValidator; only the
    // target band is whitelisted fail-closed here, mirroring how kind decodes through decodeDamageKind.
    is SkillEffectDto.Heal -> SkillEffect.Heal(decodeEffectTarget("$path.target", dto.target), dto.amount)
}

internal fun toItem(dto: ItemDto): ItemDef =
    ItemDef(
        id = dto.id,
        name = dto.name,
        type = dto.type,
        statMods = dto.statMods?.let { toStats(it) },
        effects = dto.effects,
        equipClass = dto.equipClass,
    )

internal fun toMap(dto: MapDto): MapDef =
    MapDef(
        id = dto.id,
        size = MapSize(dto.size.width, dto.size.height),
        tileset = dto.tileset,
        tiles = dto.tiles,
        spawnPoints = dto.spawnPoints.mapValues { entry -> entry.value.map { toPos(it) } },
        fog = dto.fog,
    )

internal fun toPortraitSubject(dto: PortraitSubjectDto): PortraitSubjectDef =
    PortraitSubjectDef(id = dto.id, name = dto.name, portrait = dto.portrait)

internal fun toStats(dto: StatsDto): CombatStats =
    CombatStats(atk = dto.atk, def = dto.def, mat = dto.mat, res = dto.res)

internal fun toRates(dto: RatesDto): CombatRates =
    CombatRates(
        accuracy = AccuracyRates(dto.accuracy.hit, dto.accuracy.evade, dto.accuracy.precision, dto.accuracy.block),
        burst = BurstRates(dto.burst.crit, dto.burst.critResist, dto.burst.combo, dto.burst.comboResist),
    )

internal fun toPos(dto: PosDto): Pos = Pos(x = dto.x, y = dto.y)
