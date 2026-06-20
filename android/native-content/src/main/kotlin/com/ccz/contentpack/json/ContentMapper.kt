package com.ccz.contentpack.json

import com.ccz.contentpack.ContentManifest
import com.ccz.contentpack.ContentTables
import com.ccz.contentpack.NativeContent
import com.ccz.contentpack.SourceInfo

/** Maps decoded wire DTOs into the pure domain [NativeContent]; enum whitelisting happens in the table mappers. */
internal object ContentMapper {
    fun toContent(dto: ContentDto): NativeContent =
        NativeContent(
            manifest = toManifest(dto.manifest),
            tables = toTables(dto.tables),
            events = toEvents(dto.events),
        )

    private fun toManifest(dto: ManifestDto): ContentManifest =
        ContentManifest(
            nativeFormatVersion = dto.nativeFormatVersion,
            contentId = dto.contentId,
            contentVersion = dto.contentVersion,
            source = SourceInfo(dto.source.mod, dto.source.engine),
            entry = dto.entry,
        )

    private fun toTables(dto: TablesDto): ContentTables =
        ContentTables(
            classes = dto.classes.mapIndexed { index, value -> toClass(index, value) },
            units = dto.units.mapIndexed { index, value -> toUnit(index, value) },
            terrain = dto.terrain.map { toTerrain(it) },
            skills = dto.skills.mapIndexed { index, value -> toSkill(index, value) },
            items = dto.items.map { toItem(it) },
            maps = dto.maps.map { toMap(it) },
        )
}
