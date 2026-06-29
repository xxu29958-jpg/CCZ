package com.ccz.contentpack

import com.ccz.core.model.CombatStats
import com.ccz.core.model.EffectTarget
import com.ccz.core.model.HealMode
import com.ccz.core.model.SkillEffect

data class ValidationIssue(
    val path: String,
    val message: String,
)

object ContentValidator {
    const val SUPPORTED_NATIVE_FORMAT_VERSION = "1"

    fun validate(content: NativeContent): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()

        if (content.manifest.nativeFormatVersion != SUPPORTED_NATIVE_FORMAT_VERSION) {
            issues += ValidationIssue(
                "manifest.native_format_version",
                "unsupported native format version: ${content.manifest.nativeFormatVersion}",
            )
        }

        val indexes = ContentIndexes.from(content.tables)
        val scriptIds = content.events.sScripts.map { it.id }.toSet() + content.events.rScripts.map { it.id }
        issues += validateUniqueIds(content.tables)
        // Event scripts are addressed by id (manifest.entry, scenario-replay lookup) and the
        // event validator builds diagnostic paths keyed on script id, so ids must be unique.
        // They live on content.events (not ContentTables), so dedup them here directly.
        issues += uniqueIds("events.sScripts", content.events.sScripts.map { it.id })
        issues += uniqueIds("events.rScripts", content.events.rScripts.map { it.id })
        issues += uniqueIds("events.portrait_subjects", content.events.portraitSubjects.map { it.id })
        issues += validateManifestEntry(content.manifest, scriptIds)
        issues += validatePortraitSubjectIds(content.tables, content.events.portraitSubjects)
        issues += validateUnits(content.tables, indexes)
        issues += validateClasses(content.tables, indexes)
        issues += validateItems(content.tables, indexes)
        issues += validateNumericBounds(content.tables)
        issues += validateMaps(content.tables, indexes.terrainIds)
        issues += validateCommerce(content.commerce, indexes.itemIds, scriptIds)
        issues += ContentEventValidator.validate(
            events = content.events,
            unitIds = indexes.unitIds,
            itemIds = indexes.itemIds,
            portraitIds = indexes.unitIds + content.events.portraitSubjects.map { it.id },
        )

        return issues
    }

    private fun validateUniqueIds(tables: ContentTables): List<ValidationIssue> =
        uniqueIds("classes", tables.classes.map { it.id }) +
            uniqueIds("units", tables.units.map { it.id }) +
            uniqueIds("terrain", tables.terrain.map { it.id }) +
            uniqueIds("skills", tables.skills.map { it.id }) +
            uniqueIds("items", tables.items.map { it.id }) +
            uniqueIds("maps", tables.maps.map { it.id })

    private fun validateManifestEntry(manifest: ContentManifest, scriptIds: Set<String>): List<ValidationIssue> {
        val entry = manifest.entry
        return when {
            entry.isBlank() -> listOf(ValidationIssue("manifest.entry", "entry is blank"))
            entry !in scriptIds -> listOf(ValidationIssue("manifest.entry", "unknown entry script: $entry"))
            else -> emptyList()
        }
    }

    private fun validateUnits(tables: ContentTables, indexes: ContentIndexes): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        tables.units.forEachIndexed { index, unit ->
            if (unit.classId !in indexes.classIds) {
                issues += ValidationIssue("units[$index].class", "unknown class: ${unit.classId}")
            }
            unit.loadout.skills.filterNot { it in indexes.skillIds }.forEach {
                issues += ValidationIssue("units[$index].skills", "unknown skill: $it")
            }
            unit.loadout.items.filterNot { it in indexes.itemIds }.forEach {
                issues += ValidationIssue("units[$index].items", "unknown item: $it")
            }
        }
        return issues
    }

    private fun validateClasses(tables: ContentTables, indexes: ContentIndexes): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        tables.classes.forEachIndexed { index, cls ->
            cls.combat.counters.keys.filterNot { it in indexes.classIds }.forEach {
                issues += ValidationIssue("classes[$index].counters", "unknown class: $it")
            }
            cls.combat.skills.filterNot { it in indexes.skillIds }.forEach {
                issues += ValidationIssue("classes[$index].skills", "unknown skill: $it")
            }
            cls.combat.terrainAffinity.keys.filterNot { it in indexes.terrainIds }.forEach {
                issues += ValidationIssue("classes[$index].terrain_affinity", "unknown terrain: $it")
            }
            // Mirror terrain_affinity: a per-class move-cost override keyed on an unknown terrain id is
            // silently dropped at MoveReachability (the lookup is by the tile's id), so the author's
            // intended cost/impassability never applies — fail closed instead of fail open.
            cls.movement.terrainCost.keys.filterNot { it in indexes.terrainIds }.forEach {
                issues += ValidationIssue("classes[$index].terrain_cost", "unknown terrain: $it")
            }
        }
        return issues
    }

    private fun validateItems(tables: ContentTables, indexes: ContentIndexes): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        tables.items.forEachIndexed { index, item ->
            val equipClass = item.equipClass
            if (equipClass != null && equipClass !in indexes.classIds) {
                issues += ValidationIssue("items[$index].equip_class", "unknown class: $equipClass")
            }
        }
        return issues
    }

    private fun validatePortraitSubjectIds(
        tables: ContentTables,
        portraitSubjects: List<PortraitSubjectDef>,
    ): List<ValidationIssue> {
        val unitIds = tables.units.map { it.id }.toSet()
        return portraitSubjects.mapIndexedNotNull { index, subject ->
            if (subject.id in unitIds) {
                ValidationIssue("events.portrait_subjects[$index].id", "collides with unit id: ${subject.id}")
            } else {
                null
            }
        }
    }

    /**
     * Content-layer mirrors of game-core's own value-domain invariants, so a malformed pack
     * fails closed at content load instead of throwing deep in a future builder:
     * - terrain move cost mirrors [com.ccz.core.battle.BattleMap]'s MapTile (>= 1): a cost
     *   below 1 makes movement free/negative and breaks reachability accumulation.
     * - skill range mirrors [com.ccz.core.model.RangeSpec] (min in 0..max): an inverted band
     *   can never cover any distance; a negative min is out of domain. min == 0 is valid.
     * - unit grade is the quality-tier index into [com.ccz.contentpack.assembly.GrowthBudget]'s
     *   growth-multiplier ladder (>= 0): a negative tier is malformed content. The assembler clamps it
     *   to neutral as defense-in-depth, but we reject it here so a bad pack fails at load, not silently.
     *   No upper bound is checked — the ladder length is assembly-time [GrowthConfig], so a too-high tier
     *   saturates at the top tier by design rather than being a content error.
     * - unit level mirrors [com.ccz.contentpack.assembly.GrowthBudget]'s `level-1` growth scaling (>= 1):
     *   level 0 / negative silently budgets to the base panel (levels coerced to 0), masking a data error.
     * - unit hp_max mirrors a living unit's minimum vitals (>= 1): a 0 / negative max HP is clamped by
     *   [com.ccz.contentpack.assembly.GrowthBudget.budgetHp]'s `coerceIn(0, cap)` to 0, so the assembler
     *   produces a unit with hp = hpMax = 0 — `Combatant.alive` (hp > 0) is false, so SpawnUnit would deploy
     *   a "dead on arrival" unit (instantly satisfying AnnihilateEnemies / failing ProtectAlive, and rendering
     *   a 0/0 ghost). This is the highest-consequence floor in the family, symmetric with grade/level.
     * - unit stats (atk/def/mat/res) have a non-negative floor (>= 0) as defense-in-depth: a negative stat is
     *   silently clamped to 0 by the budget, masking a data error (0 itself is legitimate — a unit with no magic
     *   attack, say — so only negatives are rejected).
     */
    private fun validateNumericBounds(tables: ContentTables): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        tables.terrain.forEachIndexed { index, terrain ->
            if (terrain.moveCost < 1) {
                issues += ValidationIssue("terrain[$index].move_cost", "move cost must be >= 1")
            }
        }
        tables.skills.forEachIndexed { index, skill ->
            val range = skill.use.range
            if (range.min < 0 || range.min > range.max) {
                issues += ValidationIssue("skills[$index].range", "min must be in 0..max")
            }
            // Effect magnitudes mirror their game-core domain bound (ADR 0008): a heal amount must be
            // >= 1 — a non-positive heal is meaningless and the resolver would no-op it (masking the data
            // error). The target band is already whitelisted at decode (decodeEffectTarget), so only the
            // numeric magnitude is checked here. Expression `when` over the sealed type → a future effect
            // variant is a compile error until its bound is decided.
            skill.effects.forEachIndexed { effectIndex, effect ->
                val effPath = "skills[$index].effects[$effectIndex]"
                val issue = when (effect) {
                    is SkillEffect.Heal -> healIssue(effect, effPath)
                    is SkillEffect.StatDelta -> statDeltaIssue(effect, effPath)
                    is SkillEffect.ApplyAilment -> ailmentIssue(effect, effPath)
                    is SkillEffect.Cleanse -> cleanseIssue(effect, effPath)
                }
                if (issue != null) issues += issue
            }
        }
        tables.units.forEachIndexed { index, unit ->
            if (unit.profile.grade < 0) {
                issues += ValidationIssue("units[$index].grade", "grade must be >= 0")
            }
            if (unit.profile.level < 1) {
                issues += ValidationIssue("units[$index].level", "level must be >= 1")
            }
            if (unit.profile.hpMax < 1) {
                issues += ValidationIssue("units[$index].hp_max", "hp_max must be >= 1")
            }
            issues += negativeStatIssues(unit.profile.stats, "units[$index].stats")
        }
        return issues
    }

    /** Non-negative floors (>= 0) on a unit's four combat stats — defense-in-depth: the budget silently
     *  clamps a negative to 0, masking a data error. 0 is legitimate (e.g. a unit with no magic), so only
     *  negatives gate. [path] is the unit's stats path. */
    private fun negativeStatIssues(stats: CombatStats, path: String): List<ValidationIssue> = buildList {
        if (stats.atk < 0) add(ValidationIssue("$path.atk", "atk must be >= 0"))
        if (stats.def < 0) add(ValidationIssue("$path.def", "def must be >= 0"))
        if (stats.mat < 0) add(ValidationIssue("$path.mat", "mat must be >= 0"))
        if (stats.res < 0) add(ValidationIssue("$path.res", "res must be >= 0"))
    }

    /**
     * A heal's amount bound (FLAT >= 1 HP; PERCENT_MAX in 1..100 percent) and band coherence (ADR 0008): a
     * heal is a friendly effect, so it must not target an [EffectTarget.ENEMY]. [path] is the effect path.
     */
    private fun healIssue(heal: SkillEffect.Heal, path: String): ValidationIssue? = when {
        heal.target == EffectTarget.ENEMY -> ValidationIssue("$path.target", "heal cannot target an enemy")
        heal.mode == HealMode.FLAT && heal.amount < 1 -> ValidationIssue("$path.amount", "flat heal amount must be >= 1")
        heal.mode == HealMode.PERCENT_MAX && heal.amount !in 1..100 ->
            ValidationIssue("$path.amount", "percent heal must be in 1..100")
        else -> null
    }

    /**
     * An ailment's coherence (ADR 0008): it is a hostile effect, so it must target an [EffectTarget.ENEMY]
     * (the mirror of a heal staying friendly), and its [SkillEffect.ApplyAilment.duration] must be >= 1 (a
     * non-positive duration is meaningless — the resolver would no-op it, masking the data error). [path] is
     * the effect path.
     */
    private fun ailmentIssue(ailment: SkillEffect.ApplyAilment, path: String): ValidationIssue? = when {
        ailment.target != EffectTarget.ENEMY -> ValidationIssue("$path.target", "ailment must target an enemy")
        ailment.duration < 1 -> ValidationIssue("$path.duration", "ailment duration must be >= 1")
        else -> null
    }

    /** A stat delta's magnitude/duration bounds (ADR 0008): a non-zero amount and a non-negative duration. */
    private fun statDeltaIssue(delta: SkillEffect.StatDelta, path: String): ValidationIssue? = when {
        delta.amount == 0 -> ValidationIssue("$path.amount", "stat delta amount must be non-zero")
        delta.duration < 0 -> ValidationIssue("$path.duration", "stat delta duration must be >= 0")
        else -> null
    }

    /** A cleanse is a friendly effect (the counterplay to an ailment) — it must not target an enemy (ADR 0008). */
    private fun cleanseIssue(cleanse: SkillEffect.Cleanse, path: String): ValidationIssue? =
        if (cleanse.target == EffectTarget.ENEMY) ValidationIssue("$path.target", "cleanse cannot target an enemy") else null

    private fun validateMaps(tables: ContentTables, terrainIds: Set<String>): List<ValidationIssue> =
        tables.maps.flatMapIndexed { index, map -> validateMap(index, map, terrainIds) }

    private fun validateCommerce(
        commerce: CommerceTables,
        itemIds: Set<String>,
        scriptIds: Set<String>,
    ): List<ValidationIssue> {
        val rewardIds = commerce.rewards.map { it.id }.toSet()
        return uniqueIds("commerce.products", commerce.products.map { it.id }) +
            uniqueIds("commerce.rewards", commerce.rewards.map { it.id }) +
            uniqueIds("commerce.stages", commerce.stages.map { it.id }) +
            validateProducts(commerce.products, rewardIds) +
            validateRewards(commerce.rewards, itemIds) +
            validateStages(commerce.stages, itemIds, scriptIds)
    }

    private fun validateProducts(products: List<ProductDef>, rewardIds: Set<String>): List<ValidationIssue> = buildList {
        products.forEachIndexed { index, product ->
            if (product.rewardId !in rewardIds) {
                add(ValidationIssue("commerce.products[$index].reward_id", "unknown reward: ${product.rewardId}"))
            }
            if (product.price.amountFen < 0) {
                add(ValidationIssue("commerce.products[$index].price.amount_fen", "amount must be >= 0"))
            }
            if (product.price.currency.isBlank()) {
                add(ValidationIssue("commerce.products[$index].price.currency", "currency is blank"))
            }
        }
    }

    private fun validateRewards(rewards: List<RewardDef>, itemIds: Set<String>): List<ValidationIssue> = buildList {
        rewards.forEachIndexed { rewardIndex, reward ->
            if (reward.itemGrants.isEmpty() && reward.entitlements.isEmpty()) {
                add(ValidationIssue("commerce.rewards[$rewardIndex]", "reward must grant an item or entitlement"))
            }
            reward.itemGrants.forEachIndexed { grantIndex, grant ->
                if (grant.itemId !in itemIds) {
                    add(ValidationIssue("commerce.rewards[$rewardIndex].item_grants[$grantIndex].item_id", "unknown item: ${grant.itemId}"))
                }
                if (grant.quantity <= 0) {
                    add(ValidationIssue("commerce.rewards[$rewardIndex].item_grants[$grantIndex].quantity", "quantity must be > 0"))
                }
            }
            reward.entitlements.forEachIndexed { entitlementIndex, entitlement ->
                if (entitlement.kind == EntitlementKind.ALL_STAGES && entitlement.target != null) {
                    add(ValidationIssue("commerce.rewards[$rewardIndex].entitlements[$entitlementIndex].target", "all_stages target must be null"))
                }
            }
        }
    }

    private fun validateStages(
        stages: List<StageDef>,
        itemIds: Set<String>,
        scriptIds: Set<String>,
    ): List<ValidationIssue> = buildList {
        stages.forEachIndexed { stageIndex, stage ->
            if (stage.entry != null && stage.entry !in scriptIds) {
                add(ValidationIssue("commerce.stages[$stageIndex].entry", "unknown entry script: ${stage.entry}"))
            }
            stage.requiredItems.forEachIndexed { itemIndex, item ->
                if (item !in itemIds) {
                    add(ValidationIssue("commerce.stages[$stageIndex].required_items[$itemIndex]", "unknown item: $item"))
                }
            }
        }
    }

    private fun uniqueIds(path: String, ids: List<String>): List<ValidationIssue> {
        val seen = mutableSetOf<String>()
        val issues = mutableListOf<ValidationIssue>()
        ids.forEachIndexed { index, id ->
            when {
                id.isBlank() -> issues += ValidationIssue("$path[$index].id", "id is blank")
                !seen.add(id) -> issues += ValidationIssue("$path[$index].id", "duplicate id: $id")
            }
        }
        return issues
    }

    private fun validateMap(index: Int, map: MapDef, terrainIds: Set<String>): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        if (map.size.width <= 0) issues += ValidationIssue("maps[$index].width", "width must be > 0")
        if (map.size.height <= 0) issues += ValidationIssue("maps[$index].height", "height must be > 0")
        if (map.tiles.size != map.size.height) {
            issues += ValidationIssue("maps[$index].tiles", "row count does not match height")
        }
        map.tiles.forEachIndexed { y, row ->
            if (row.size != map.size.width) {
                issues += ValidationIssue("maps[$index].tiles[$y]", "column count does not match width")
            }
            row.forEachIndexed { x, terrain ->
                if (terrain !in terrainIds) {
                    issues += ValidationIssue("maps[$index].tiles[$y][$x]", "unknown terrain: $terrain")
                }
            }
        }
        map.spawnPoints.forEach { (group, positions) ->
            positions.forEachIndexed { posIndex, pos ->
                if (pos.x !in 0 until map.size.width || pos.y !in 0 until map.size.height) {
                    issues += ValidationIssue("maps[$index].spawn_points.$group[$posIndex]", "spawn point out of bounds")
                }
            }
        }
        return issues
    }
}

private data class ContentIndexes(
    val classIds: Set<String>,
    val skillIds: Set<String>,
    val itemIds: Set<String>,
    val terrainIds: Set<String>,
    val unitIds: Set<String>,
) {
    companion object {
        fun from(tables: ContentTables): ContentIndexes =
            ContentIndexes(
                classIds = tables.classes.map { it.id }.toSet(),
                skillIds = tables.skills.map { it.id }.toSet(),
                itemIds = tables.items.map { it.id }.toSet(),
                terrainIds = tables.terrain.map { it.id }.toSet(),
                unitIds = tables.units.map { it.id }.toSet(),
            )
    }
}
