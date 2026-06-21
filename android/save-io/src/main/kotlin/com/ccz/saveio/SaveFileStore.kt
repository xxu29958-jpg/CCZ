package com.ccz.saveio

import com.ccz.core.save.SaveCodec
import com.ccz.core.save.SaveEnvelope
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Thrown when a save file cannot be read or written safely (fail-closed). Distinct
 * from [SaveCodec]'s `SaveDecodeException`, which signals corrupt *content*; this
 * signals a filesystem-level failure (missing file, unwritable directory, no atomic
 * move support), so callers can tell IO failure apart from decode failure.
 */
class SaveIoException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * The IO boundary that persists a [SaveEnvelope] to disk. game-core is the pure
 * battle authority and never touches the filesystem (CCZ_ENGINE_RULES), so the
 * codec's text meets the disk here. [save] writes the full encoded text to a sibling
 * temp file then atomically renames it over the target, so a concurrent reader — or a
 * crash mid-write — never observes a half-written save and no partial product is left
 * in place (CCZ_ENGINE_RULES §Write & Convert Safety). [load] reads and delegates
 * shape validation to [SaveCodec.decode], which fails closed on corruption.
 *
 * Note: atomicity here is rename-visibility (a reader sees the whole old or whole new
 * file), not crash durability — an explicit fsync of the file/dir before rename would
 * be a further hardening, deliberately out of scope until a durability need arises.
 */
object SaveFileStore {
    private const val TEMP_PREFIX = ".save-"
    private const val TEMP_SUFFIX = ".tmp"

    /**
     * Atomically writes [envelope] to [path]: encode → write to a temp file in the
     * same directory (same filesystem, so the rename can be atomic) → ATOMIC_MOVE over
     * [path], replacing any existing save. The temp file is deleted on any failure, so
     * a botched write leaves neither a half-file at [path] nor a stray temp file.
     */
    fun save(path: Path, envelope: SaveEnvelope) {
        val text = SaveCodec.encode(envelope)
        val target = path.toAbsolutePath()
        val dir = target.parent ?: throw SaveIoException("save path has no parent directory: $path")
        try {
            Files.createDirectories(dir)
        } catch (e: IOException) {
            throw SaveIoException("cannot create save directory: $dir", e)
        }
        val temp = try {
            Files.createTempFile(dir, TEMP_PREFIX, TEMP_SUFFIX)
        } catch (e: IOException) {
            throw SaveIoException("cannot create temp file in: $dir", e)
        }
        var moved = false
        try {
            Files.writeString(temp, text)
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE)
            moved = true
        } catch (e: IOException) {
            throw SaveIoException("failed to write save atomically: $target", e)
        } finally {
            // any non-success exit (IOException, unchecked exception, Error) leaves no stray temp;
            // best-effort so cleanup never masks the original failure
            if (!moved) runCatching { Files.deleteIfExists(temp) }
        }
    }

    /**
     * Reads the save at [path] and decodes it. A missing file fails closed with a
     * [SaveIoException]; corrupt content fails closed with [SaveCodec]'s
     * SaveDecodeException, propagated unwrapped so callers can distinguish IO from
     * decode failure.
     */
    fun load(path: Path): SaveEnvelope {
        val text = try {
            Files.readString(path)
        } catch (e: NoSuchFileException) {
            throw SaveIoException("save file not found: $path", e)
        } catch (e: IOException) {
            throw SaveIoException("failed to read save: $path", e)
        }
        return SaveCodec.decode(text)
    }
}
