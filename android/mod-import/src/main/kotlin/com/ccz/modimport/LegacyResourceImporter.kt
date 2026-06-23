package com.ccz.modimport

import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** A decrypted legacy resource: its in-APK path, classified kind, and plaintext bytes. */
data class LegacyResource(
    val path: String,
    val kind: LegacyResourceKind,
    val bytes: ByteArray,
) {
    /** Plaintext as UTF-8 text with any leading BOM stripped (legacy tables are BOM-prefixed JSON). */
    fun text(): String = String(bytes, Charsets.UTF_8).removePrefix("﻿")

    override fun equals(other: Any?): Boolean =
        this === other || (other is LegacyResource && path == other.path && kind == other.kind && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = (path.hashCode() * 31 + kind.hashCode()) * 31 + bytes.contentHashCode()
}

/**
 * Decrypts BGT1-encrypted resources from a legally-owned legacy APK using a caller-supplied key.
 *
 * This is the authorized counterpart to [LegacyApkInspector.requirePlainContent]: the inspector
 * fails closed on encrypted content, while the importer proceeds only when the caller presents the
 * resource key they are entitled to use for their own export.
 */
object LegacyResourceImporter {
    /** Decrypt every BGT1 resource whose kind is in [kinds]; returns results keyed by APK path. */
    fun importResources(
        apk: Path,
        key: ByteArray,
        kinds: Set<LegacyResourceKind> = LegacyResourceKind.values().toSet(),
        maxResources: Int = Int.MAX_VALUE,
    ): List<LegacyResource> {
        require(key.isNotEmpty()) { "resource key must not be empty" }
        require(maxResources > 0) { "maxResources must be positive: $maxResources" }
        val report = LegacyApkInspector.inspect(apk, maxEncryptedSamples = Int.MAX_VALUE)
        val wanted = report.encryptedResources.asSequence()
            .filter { it.kind in kinds }
            .take(maxResources)
            .map { it.path to it.kind }
            .toList()
        return try {
            ZipFile(apk.toFile()).use { zip ->
                wanted.map { (path, kind) ->
                    val entry = zip.getEntry(path)
                        ?: throw LegacyApkInspectionException("resource vanished between scan and read: $path")
                    LegacyResource(path, kind, decryptEntry(zip, entry, key, path))
                }
            }
        } catch (e: ZipException) {
            throw LegacyApkInspectionException("not a readable APK/ZIP: $apk", e)
        }
    }

    /** Decrypt a single BGT1 resource entry by [entryName]. */
    fun importResource(apk: Path, entryName: String, key: ByteArray): LegacyResource {
        require(key.isNotEmpty()) { "resource key must not be empty" }
        return try {
            ZipFile(apk.toFile()).use { zip ->
                val entry = zip.getEntry(entryName)
                    ?: throw LegacyApkInspectionException("missing entry: $entryName")
                LegacyResource(entryName, entryName.resourceKind(), decryptEntry(zip, entry, key, entryName))
            }
        } catch (e: ZipException) {
            throw LegacyApkInspectionException("not a readable APK/ZIP: $apk", e)
        }
    }

    private fun decryptEntry(zip: ZipFile, entry: ZipEntry, key: ByteArray, path: String): ByteArray {
        val raw = zip.getInputStream(entry).use { it.readBytes() }
        if (!Bgt1Codec.isBgt1(raw)) {
            throw LegacyApkInspectionException("not BGT1-encrypted (no decryption needed): $path")
        }
        return try {
            Bgt1Codec.decrypt(raw, key)
        } catch (e: Bgt1FormatException) {
            throw LegacyApkInspectionException("failed to decrypt $path (wrong key?): ${e.message}", e)
        }
    }
}
