package com.ccz.contentpack

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
        issues += validateUniqueIds(content.tables)
        // Event scripts are addressed by id (manifest.entry, scenario-replay lookup) and the
        // event validator builds diagnostic paths keyed on script id, so ids must be unique.
        // They live on content.events (not ContentTables), so dedup them here directly.
        issues += uniqueIds("events.sScripts", content.events.sScripts.map { it.id })
        issues += uniqueIds("events.rScripts", content.events.rScripts.map { it.id })
        issues += uniqueIds("events.portrait_subjects", content.events.portraitSubjects.map { it.id })
        issues += validateManifestEntry(content.manifest, content.events)
        issues += validatePortraitSubjectIds(content.tables, content.events.portraitSubjects)
        issues += validateUnits(content.tables, indexes)
        issues += validateClasses(content.tables, indexes)
        issues += validateItems(content.tables, indexes)
        issues += validateNumericBounds(content.tables)
        issues += validateMaps(content.tables, indexes.terrainIds)
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

    private fun validateManifestEntry(manifest: ContentManifest, events: EventTables): List<ValidationIssue> {
        val entry = manifest.entry
        val scriptIds = events.sScripts.map { it.id }.toSet() + events.rScripts.map { it.id }
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
                val issue = when (effect) {
                    is SkillEffect.Heal -> healAmountIssue(effect, "skills[$index].effects[$effectIndex].amount")
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
        }
        return issues
    }

    /** Mode-specific bound on a heal's amount (ADR 0008): FLAT >= 1 HP; PERCENT_MAX in 1..100 percent. */
    private fun healAmountIssue(heal: SkillEffect.Heal, path: String): ValidationIssue? = when (heal.mode) {
        HealMode.FLAT -> if (heal.amount < 1) ValidationIssue(path, "flat heal amount must be >= 1") else null
        HealMode.PERCENT_MAX -> if (heal.amount !in 1..100) ValidationIssue(path, "percent heal must be in 1..100") else null
    }

    private fun validateMaps(tables: ContentTables, terrainIds: Set<String>): List<ValidationIssue> =
        tables.maps.flatMapIndexed { index, map -> validateMap(index, map, terrainIds) }

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
