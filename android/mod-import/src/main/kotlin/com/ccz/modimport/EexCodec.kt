package com.ccz.modimport

/**
 * Byte-level reader for the legacy **EEX** script container (`.eex_new scripts under Scenes/`) — the lowest layer of the
 * EEX legacy-script generator, the first plugin in the content-generator framework (ADR 0009). It answers
 * ONLY "what is literally in the file" — framing, section offsets, embedded command-tagged strings — and
 * makes NO engine-design judgment (mapping legacy concepts to engine ops/objectives is the
 * `LegacyScriptDecoder` / `LegacySemanticMapper` layers' job, never this one).
 *
 * Container facts, reverse-engineered from `libMyGame.so` (`ScriptHandler::DecodeScenes`) and independently
 * verified against the whole 316-file corpus (`docs/recon/eex_decode.py`):
 * - magic `45 45 58 00` (`EEX\0`); little-endian version `0x00000201`.
 * - `u32@0x0a` is BOTH the header size AND the first section offset.
 * - the section-offset table is `(headerSize - 0x0a) / 4` little-endian `u32` entries read from `0x0a`,
 *   strictly ascending and within the file; sections partition the file, the last running to EOF.
 * - text operands are encoded as `<cmd:u16> 05 00 <NUL-terminated UTF-8> 00`; the `cmd` word before the
 *   `05 00` marker is the legacy command id (e.g. `0x14` ActorTalk dialogue, `0x16..0x1a` CommonInfo).
 *
 * Pure and fail-closed: malformed framing throws [EexFormatException]; a `05 00` run that is not clean UTF-8
 * is a binary coincidence and is skipped (not a string), so [strings] never fabricates text.
 */
object EexCodec {
    /** `EEX\0` — the four magic bytes every Scenes script starts with. */
    val MAGIC: ByteArray = byteArrayOf(0x45, 0x45, 0x58, 0x00)

    private const val OFF_VERSION = 0x04
    private const val OFF_HEADER = 0x0a
    private const val SUPPORTED_VERSION = 0x00000201
    private const val STR_MARK0 = 0x05 // a NUL-terminated UTF-8 string operand is introduced by `05 00`
    private const val STR_MARK1 = 0x00

    /** The EEX header: version, header size, the parsed section-offset table, and the total file size. */
    data class EexHeader(
        val version: Int,
        val headerSize: Int,
        val sectionOffsets: List<Int>,
        val size: Int,
    )

    /** A command-tagged UTF-8 string operand: the legacy op word [cmd] preceding a `05 00 <utf8> 00` payload,
     *  with [offset] = the byte position of the `05 00` marker (a stable key for ordering within the file). */
    data class EexString(val offset: Int, val cmd: Int, val text: String)

    /** True when [blob] starts with the EEX magic. */
    fun isEex(blob: ByteArray): Boolean =
        blob.size >= MAGIC.size && (0 until MAGIC.size).all { blob[it] == MAGIC[it] }

    /**
     * Parse and validate the EEX framing (fail-closed). The first section offset MUST equal the header size
     * and the offsets MUST be strictly ascending and within the file, so a corrupt or non-EEX blob is rejected
     * here rather than mis-read downstream.
     * @throws EexFormatException on bad magic / unsupported version / out-of-range or non-ascending offsets.
     */
    fun parseHeader(blob: ByteArray): EexHeader {
        if (!isEex(blob)) throw EexFormatException("missing EEX magic")
        if (blob.size < OFF_HEADER + 4) throw EexFormatException("blob too small: ${blob.size}")
        val version = u32le(blob, OFF_VERSION)
        if (version != SUPPORTED_VERSION) {
            throw EexFormatException("unsupported EEX version 0x%08x".format(version))
        }
        val headerSize = u32le(blob, OFF_HEADER)
        if (headerSize < OFF_HEADER || headerSize > blob.size) {
            throw EexFormatException("header size $headerSize out of range (size ${blob.size})")
        }
        val count = (headerSize - OFF_HEADER) / 4
        if (count < 1) throw EexFormatException("no section offsets")
        val offsets = ArrayList<Int>(count)
        var prev = -1
        for (i in 0 until count) {
            val o = u32le(blob, OFF_HEADER + i * 4)
            if (i == 0 && o != headerSize) throw EexFormatException("first offset $o != header size $headerSize")
            if (o > blob.size) throw EexFormatException("section offset $o past EOF (size ${blob.size})")
            if (o <= prev) throw EexFormatException("section offsets not strictly ascending")
            offsets.add(o)
            prev = o
        }
        return EexHeader(version, headerSize, offsets, blob.size)
    }

    /**
     * Every command-tagged UTF-8 string operand in [blob], in file order. Scans for the `05 00` string marker
     * and decodes the following bytes up to the next NUL as STRICT UTF-8; a run that is not clean UTF-8, or is
     * empty / all-whitespace, is a binary coincidence and is skipped (never emitted). The legacy command word
     * (the `u16` before the marker) is reported as [EexString.cmd] for the decoder layer to interpret — this
     * layer assigns NO meaning to it. Requires a valid header first (fail-closed).
     */
    fun strings(blob: ByteArray): List<EexString> {
        parseHeader(blob) // reject non-EEX/corrupt before scanning
        val out = ArrayList<EexString>()
        var i = 0
        val limit = blob.size - 2
        while (i < limit) {
            val hit = stringAt(blob, i)
            if (hit == null) {
                i += 1
            } else {
                out.add(hit.first)
                i = hit.second // resume just past the NUL terminator
            }
        }
        return out
    }

    /** A `05 00 <utf8> 00` string operand starting at [i] paired with the index just past its NUL, or null if
     *  [i] is not a clean marker (wrong bytes, no terminator, malformed UTF-8, or empty/whitespace-only). */
    private fun stringAt(blob: ByteArray, i: Int): Pair<EexString, Int>? {
        if ((blob[i].toInt() and 0xff) != STR_MARK0 || (blob[i + 1].toInt() and 0xff) != STR_MARK1) return null
        val end = indexOfZero(blob, i + 2)
        if (end <= i + 2) return null
        val text = decodeUtf8OrNull(blob, i + 2, end) ?: return null
        if (text.none { it.code > 0x20 }) return null
        val cmd = if (i >= 2) u16le(blob, i - 2) else -1
        return EexString(offset = i, cmd = cmd, text = text) to (end + 1)
    }

    private fun indexOfZero(blob: ByteArray, from: Int): Int {
        var j = from
        while (j < blob.size) {
            if (blob[j].toInt() == 0) return j
            j += 1
        }
        return -1
    }

    /** Decode [start, end) as strict UTF-8, or null if it contains any malformed sequence (skip = not a string). */
    private fun decodeUtf8OrNull(blob: ByteArray, start: Int, end: Int): String? {
        return try {
            java.nio.charset.Charset.forName("UTF-8").newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(blob, start, end - start))
                .toString()
        } catch (e: java.nio.charset.CharacterCodingException) {
            null // malformed UTF-8 -> a binary coincidence, not a string
        }
    }

    internal fun u16le(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8)

    internal fun u32le(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xff) or
            ((b[o + 1].toInt() and 0xff) shl 8) or
            ((b[o + 2].toInt() and 0xff) shl 16) or
            ((b[o + 3].toInt() and 0xff) shl 24)
}

/** Thrown when an EEX blob is malformed (bad magic, unsupported version, or invalid framing). */
class EexFormatException(message: String) : RuntimeException(message)
