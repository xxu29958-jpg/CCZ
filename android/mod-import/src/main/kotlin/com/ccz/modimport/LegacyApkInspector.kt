package com.ccz.modimport

import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

private const val FUNCTION_ENTRY = "assets/GameResources/function.txt"
private const val GAME_RESOURCE_PREFIX = "assets/GameResources/"
private const val BGT1_MAGIC = "BGT1"

/** Entry tallies from a legacy-APK scan (grouped to keep [LegacyApkReport] within the parameter gate). */
data class LegacyEntryCounts(
    val totalEntries: Int,
    val gameResourceEntries: Int,
    val directoryCounts: Map<String, Int>,
    val extensionCounts: Map<String, Int>,
)

data class LegacyApkReport(
    val gameName: String,
    val gamePath: String,
    val encryptResource: Boolean,
    val counts: LegacyEntryCounts,
    val encryptedResources: List<LegacyEncryptedResource>,
) {
    val gameRoot: String = GAME_RESOURCE_PREFIX + gamePath + "/"

    val hasEncryptedResources: Boolean
        get() = encryptResource || encryptedResources.isNotEmpty()

    fun requirePlainContent() {
        if (hasEncryptedResources) {
            throw LegacyImportRejected(
                "legacy APK '$gameName' declares or contains encrypted resources; " +
                    "provide a plain authorized export before content conversion",
            )
        }
    }
}

data class LegacyEncryptedResource(
    val path: String,
    val length: Long,
    val kind: LegacyResourceKind,
)

enum class LegacyResourceKind {
    TABLE_JSON,
    TERRAIN_JSON,
    SCENE_SCRIPT,
    MAP_BUNDLE,
    OTHER_BGT1,
}

class LegacyApkInspectionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class LegacyImportRejected(message: String) : RuntimeException(message)

object LegacyApkInspector {
    fun inspect(apk: Path, maxEncryptedSamples: Int = 64): LegacyApkReport {
        require(maxEncryptedSamples > 0) { "maxEncryptedSamples must be positive: $maxEncryptedSamples" }
        return try {
            ZipFile(apk.toFile()).use { zip -> inspectZip(zip, maxEncryptedSamples) }
        } catch (e: LegacyApkInspectionException) {
            throw e
        } catch (e: java.util.zip.ZipException) {
            throw LegacyApkInspectionException("not a readable APK/ZIP: $apk", e)
        }
    }

    private fun inspectZip(zip: ZipFile, maxEncryptedSamples: Int): LegacyApkReport {
        val function = zip.getEntry(FUNCTION_ENTRY)
            ?: throw LegacyApkInspectionException("missing $FUNCTION_ENTRY")
        val config = parseFunctionConfig(zip.readText(function))
        val gameRoot = GAME_RESOURCE_PREFIX + config.gamePath + "/"
        val entries = zip.entries().asSequence().filter { !it.isDirectory }.toList()
        val gameEntries = entries.filter { it.name.startsWith(gameRoot) }
        return LegacyApkReport(
            gameName = config.gameName,
            gamePath = config.gamePath,
            encryptResource = config.encryptResource,
            counts = LegacyEntryCounts(
                totalEntries = entries.size,
                gameResourceEntries = gameEntries.size,
                directoryCounts = countGameDirectories(gameEntries, gameRoot),
                extensionCounts = countExtensions(gameEntries),
            ),
            encryptedResources = encryptedSamples(zip, gameEntries, maxEncryptedSamples),
        )
    }

    private fun parseFunctionConfig(text: String): FunctionConfig =
        FunctionConfig(
            gameName = requiredString(text, "GameName"),
            gamePath = requiredString(text, "GamePath").also(::requireSafeGamePath),
            encryptResource = optionalBoolean(text, "EncryptResource") ?: false,
        )

    private fun requiredString(text: String, key: String): String {
        val value = Regex(""""$key"\s*:\s*"([^"]*)"""").find(text)?.groupValues?.get(1)
        if (value.isNullOrBlank()) throw LegacyApkInspectionException("missing or blank function field '$key'")
        return value
    }

    private fun optionalBoolean(text: String, key: String): Boolean? =
        Regex(""""$key"\s*:\s*(true|false)""").find(text)?.groupValues?.get(1)?.toBooleanStrict()

    private fun requireSafeGamePath(gamePath: String) {
        if (gamePath.contains('/') || gamePath.contains('\\') || gamePath == "." || gamePath == "..") {
            throw LegacyApkInspectionException("unsafe GamePath in $FUNCTION_ENTRY: $gamePath")
        }
    }

    private fun countGameDirectories(entries: List<ZipEntry>, gameRoot: String): Map<String, Int> =
        entries.groupingBy { entry ->
            val relative = entry.name.removePrefix(gameRoot)
            relative.substringBefore('/', missingDelimiterValue = "<root>")
        }.eachCount().toSortedMap()

    private fun countExtensions(entries: List<ZipEntry>): Map<String, Int> =
        entries.groupingBy { entry -> entry.name.extensionOrNone() }.eachCount().toSortedMap()

    private fun encryptedSamples(
        zip: ZipFile,
        entries: List<ZipEntry>,
        maxEncryptedSamples: Int,
    ): List<LegacyEncryptedResource> =
        entries.asSequence()
            .filter { it.name.hasPotentialBgt1Payload() }
            .filter { zip.hasBgt1Magic(it) }
            .take(maxEncryptedSamples)
            .map { LegacyEncryptedResource(path = it.name, length = it.size, kind = it.name.resourceKind()) }
            .toList()

    private fun ZipFile.readText(entry: ZipEntry): String =
        getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText().removePrefix("\uFEFF") }

    private fun ZipFile.hasBgt1Magic(entry: ZipEntry): Boolean =
        getInputStream(entry).use { stream ->
            val magic = ByteArray(BGT1_MAGIC.length)
            stream.read(magic) == magic.size && magic.toString(Charsets.US_ASCII) == BGT1_MAGIC
        }

    private data class FunctionConfig(
        val gameName: String,
        val gamePath: String,
        val encryptResource: Boolean,
    )
}

private fun String.extensionOrNone(): String {
    val name = substringAfterLast('/', this)
    val dot = name.lastIndexOf('.')
    return if (dot < 0) "<none>" else name.substring(dot).lowercase()
}

private fun String.hasPotentialBgt1Payload(): Boolean =
    endsWith(".json", ignoreCase = true) ||
        endsWith(".eex_new", ignoreCase = true) ||
        endsWith(".e5", ignoreCase = true)

internal fun String.resourceKind(): LegacyResourceKind =
    when {
        contains("/terrainJson/", ignoreCase = true) -> LegacyResourceKind.TERRAIN_JSON
        contains("/json/", ignoreCase = true) -> LegacyResourceKind.TABLE_JSON
        contains("/Scenes/", ignoreCase = true) -> LegacyResourceKind.SCENE_SCRIPT
        endsWith(".e5", ignoreCase = true) -> LegacyResourceKind.MAP_BUNDLE
        else -> LegacyResourceKind.OTHER_BGT1
    }
