package com.ccz.contentpack

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
        issues += validateUnits(content.tables, indexes)
        issues += validateClasses(content.tables, indexes)
        issues += validateMaps(content.tables, indexes.terrainIds)
        issues += ContentEventValidator.validate(content.events, indexes.unitIds, indexes.itemIds)

        return issues
    }

    private fun validateUniqueIds(tables: ContentTables): List<ValidationIssue> =
        uniqueIds("classes", tables.classes.map { it.id }) +
            uniqueIds("units", tables.units.map { it.id }) +
            uniqueIds("terrain", tables.terrain.map { it.id }) +
            uniqueIds("skills", tables.skills.map { it.id }) +
            uniqueIds("items", tables.items.map { it.id }) +
            uniqueIds("maps", tables.maps.map { it.id })

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
        }
        return issues
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
