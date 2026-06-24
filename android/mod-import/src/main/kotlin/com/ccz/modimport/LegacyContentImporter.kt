package com.ccz.modimport

import com.ccz.contentpack.NativeContent
import com.ccz.contentpack.json.ContentJsonLoader
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Full native content-pack document (manifest + tables) in wire format; events default to empty. */
@Serializable
data class PackContent(
    val manifest: PackManifest,
    val tables: PackTables,
    val events: PackEvents? = null,
)

@Serializable
data class PackManifest(
    @SerialName("native_format_version") val nativeFormatVersion: String,
    @SerialName("content_id") val contentId: String,
    @SerialName("content_version") val contentVersion: String,
    val source: PackSource,
    val entry: String,
)

@Serializable
data class PackSource(val mod: String)

@Serializable
data class PackTables(
    val classes: List<PackClass> = emptyList(),
    val units: List<PackUnit> = emptyList(),
    val skills: List<PackSkill> = emptyList(),
    val terrain: List<PackTerrain> = emptyList(),
    val maps: List<PackMap> = emptyList(),
)

/** Pack identity supplied by the caller (grouped to keep the importer API within the parameter gate). */
data class PackMeta(
    val contentId: String,
    val contentVersion: String,
    val mod: String,
    val entry: String,
)

/** Decrypted legacy table JSON sources (as returned by [LegacyResourceImporter]). */
data class LegacyTableSources(
    val dicJob: String,
    val dicSkill: String,
    val dicHero: String,
    val mapTerrain: String,
)

/**
 * Assembles decrypted legacy tables into a native content pack and loads it through the engine's own
 * [ContentJsonLoader] — the end-to-end seam proving legacy data feeds the engine's content model.
 *
 * mod-import stays a *generator*: it emits the documented wire JSON and hands it to native-content's
 * public loader (no reach into its internal DTOs). Cross-reference validation (units→classes, etc.)
 * and battle assembly run in the existing native-content layers on the loaded [NativeContent].
 */
object LegacyContentImporter {
    const val NATIVE_FORMAT_VERSION: String = "1"

    private val writer = Json { prettyPrint = true }

    /** Map the four legacy tables into a full content-pack document. */
    fun buildPack(meta: PackMeta, sources: LegacyTableSources): PackContent =
        PackContent(
            manifest = PackManifest(
                nativeFormatVersion = NATIVE_FORMAT_VERSION,
                contentId = meta.contentId,
                contentVersion = meta.contentVersion,
                source = PackSource(meta.mod),
                entry = meta.entry,
            ),
            tables = PackTables(
                classes = LegacyClassMapper.mapClasses(sources.dicJob),
                units = LegacyUnitMapper.mapUnits(sources.dicHero),
                skills = LegacySkillMapper.mapSkills(sources.dicSkill),
                terrain = LegacyTerrainMapper.mapTerrain(sources.mapTerrain),
            ),
        )

    /** Serialize the assembled pack as native content-pack JSON. */
    fun buildPackJson(meta: PackMeta, sources: LegacyTableSources): String =
        writer.encodeToString(PackContent.serializer(), buildPack(meta, sources))

    /** Build the pack and load it through the engine's content loader into the domain model. */
    fun load(meta: PackMeta, sources: LegacyTableSources): NativeContent =
        ContentJsonLoader.load(buildPackJson(meta, sources))
}
