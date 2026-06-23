package com.ccz.modimport

/**
 * Codec for the **BGT1** resource container used by legacy Bantu (`com.bantu.*`) Cocos2d-x
 * titles, reverse-engineered from `libMyGame.so` (`SecretFile::decryptData`).
 *
 * The container wraps a payload with a dual self-mutating XOR key schedule: a primary key
 * derived from the title's *dynamic secret* and a secondary key carried (encrypted) in the
 * header. The cipher is symmetric — the keystream depends only on the key and byte position,
 * not the data — so [encrypt] and [decrypt] share the same stream application.
 *
 * The resource key is the title's runtime secret and is **never embedded here**: callers must
 * supply the key they are authorized to use for their own legally-owned export.
 */
object Bgt1Codec {
    const val MAGIC: String = "BGT1"

    private const val HEADER_LEN = 0x0e
    private const val OFF_VERSION = 0x04
    private const val OFF_TOTAL = 0x08
    private const val OFF_CHECKSUM = 0x0c
    private const val OFF_ENC_KLEN = 0x0d
    private const val OFF_SECONDARY = 0x0e
    private const val SUPPORTED_VERSION = 1
    private const val MAX_SECONDARY_KEY = 0x21
    private const val LEN_FIELD = 4
    private const val FRAME_OVERHEAD = 0x16 // header + payloadLen field + prefixLen field

    /** True when [blob] starts with the BGT1 magic. */
    fun isBgt1(blob: ByteArray): Boolean =
        blob.size >= MAGIC.length && String(blob, 0, MAGIC.length, Charsets.US_ASCII) == MAGIC

    /**
     * Decrypt a BGT1 [blob] with [key] (the title's resource secret).
     * @throws Bgt1FormatException if the container is malformed or the key does not fit.
     */
    fun decrypt(blob: ByteArray, key: ByteArray): ByteArray {
        if (blob.size <= FRAME_OVERHEAD + LEN_FIELD) throw Bgt1FormatException("blob too small: ${blob.size}")
        if (!isBgt1(blob)) throw Bgt1FormatException("missing BGT1 magic")
        verifyChecksum(blob)
        val version = u32le(blob, OFF_VERSION)
        if (version != SUPPORTED_VERSION) throw Bgt1FormatException("unsupported version $version")
        val total = u32le(blob, OFF_TOTAL)

        val key1 = SecretKey(key)
        val realKlen = recoverSecondaryKeyLength(blob, key1)
        val secondary = recoverSecondaryKey(blob, key1, realKlen)
        val key2 = SecretKey(secondary)

        val payloadLen = u32le(streamField(blob, OFF_SECONDARY + realKlen, key1, key2), 0)
        val prefixLen = u32le(streamField(blob, OFF_SECONDARY + LEN_FIELD + realKlen, key1, key2), 0)
        if (FRAME_OVERHEAD + realKlen + payloadLen != total) {
            throw Bgt1FormatException("size mismatch: $FRAME_OVERHEAD+$realKlen+$payloadLen != $total")
        }
        val start = FRAME_OVERHEAD + realKlen
        if (start + payloadLen > blob.size) throw Bgt1FormatException("payload overruns blob")
        val payload = blob.copyOfRange(start, start + payloadLen)
        key1.reset(); key2.reset()
        val encrypted = minOf(prefixLen, payloadLen)
        for (i in 0 until encrypted) {
            payload[i] = key2.step(key1.step(payload[i], mutate = true), mutate = true)
        }
        return payload
    }

    /**
     * Encrypt [payload] into a BGT1 container using [key] and [secondaryKey].
     * Provided for round-trip testing and re-packing; not used on the import path.
     */
    fun encrypt(payload: ByteArray, key: ByteArray, secondaryKey: ByteArray): ByteArray {
        require(secondaryKey.isNotEmpty() && secondaryKey.size <= MAX_SECONDARY_KEY) {
            "secondary key length must be in 1..$MAX_SECONDARY_KEY: ${secondaryKey.size}"
        }
        val realKlen = secondaryKey.size
        val total = FRAME_OVERHEAD + realKlen + payload.size
        val out = ByteArray(total)
        MAGIC.forEachIndexed { i, c -> out[i] = c.code.toByte() }
        putU32le(out, OFF_VERSION, SUPPORTED_VERSION)
        putU32le(out, OFF_TOTAL, total)

        val key1 = SecretKey(key)
        out[OFF_ENC_KLEN] = (realKlen xor foldKey(key1)).toByte()
        key1.reset()
        for (i in 0 until realKlen) {
            out[OFF_SECONDARY + i] = (key1.step(secondaryKey[i], mutate = false).toInt() xor realKlen).toByte()
        }
        val key2 = SecretKey(secondaryKey)
        putStreamField(out, OFF_SECONDARY + realKlen, payload.size, key1, key2)
        putStreamField(out, OFF_SECONDARY + LEN_FIELD + realKlen, payload.size, key1, key2)
        val start = FRAME_OVERHEAD + realKlen
        key1.reset(); key2.reset()
        for (i in payload.indices) {
            out[start + i] = key2.step(key1.step(payload[i], mutate = true), mutate = true)
        }
        out[OFF_CHECKSUM] = checksum(out).toByte()
        return out
    }

    private fun recoverSecondaryKeyLength(blob: ByteArray, key1: SecretKey): Int {
        key1.reset()
        var scratch = blob[OFF_ENC_KLEN]
        repeat(key1.length) { scratch = key1.step(scratch, mutate = false) }
        val realKlen = scratch.toInt() and 0xff
        if (realKlen == 0 || realKlen > MAX_SECONDARY_KEY) {
            throw Bgt1FormatException("implausible secondary key length $realKlen (wrong key?)")
        }
        if (OFF_SECONDARY + realKlen + 2 * LEN_FIELD > blob.size) {
            throw Bgt1FormatException("secondary key overruns blob")
        }
        return realKlen
    }

    private fun recoverSecondaryKey(blob: ByteArray, key1: SecretKey, realKlen: Int): ByteArray {
        key1.reset()
        val secondary = blob.copyOfRange(OFF_SECONDARY, OFF_SECONDARY + realKlen)
        for (i in 0 until realKlen) {
            secondary[i] = key1.step(((secondary[i].toInt() xor realKlen) and 0xff).toByte(), mutate = false)
        }
        return secondary
    }

    private fun streamField(blob: ByteArray, offset: Int, key1: SecretKey, key2: SecretKey): ByteArray {
        key1.reset(); key2.reset()
        val field = blob.copyOfRange(offset, offset + LEN_FIELD)
        for (i in field.indices) field[i] = key2.step(key1.step(field[i], mutate = false), mutate = false)
        return field
    }

    private fun putStreamField(out: ByteArray, offset: Int, value: Int, key1: SecretKey, key2: SecretKey) {
        key1.reset(); key2.reset()
        val field = ByteArray(LEN_FIELD)
        putU32le(field, 0, value)
        for (i in field.indices) out[offset + i] = key2.step(key1.step(field[i], mutate = false), mutate = false)
    }

    private fun verifyChecksum(blob: ByteArray) {
        val expected = blob[OFF_CHECKSUM].toInt() and 0xff
        val actual = checksum(blob)
        if (expected != actual) throw Bgt1FormatException("checksum 0x%02x != 0x%02x".format(actual, expected))
    }

    private fun checksum(blob: ByteArray): Int {
        var x = 0
        for (i in blob.indices) if (i != OFF_CHECKSUM) x = x xor (blob[i].toInt() and 0xff)
        return x and 0xff
    }

    private fun foldKey(key: SecretKey): Int = key.fold()

    private fun u32le(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xff) or
            ((b[o + 1].toInt() and 0xff) shl 8) or
            ((b[o + 2].toInt() and 0xff) shl 16) or
            ((b[o + 3].toInt() and 0xff) shl 24)

    private fun putU32le(b: ByteArray, o: Int, v: Int) {
        b[o] = (v and 0xff).toByte()
        b[o + 1] = ((v ushr 8) and 0xff).toByte()
        b[o + 2] = ((v ushr 16) and 0xff).toByte()
        b[o + 3] = ((v ushr 24) and 0xff).toByte()
    }
}

/** Thrown when a BGT1 blob is malformed or the supplied key does not decode it. */
class Bgt1FormatException(message: String) : RuntimeException(message)

/**
 * The self-mutating XOR key schedule behind BGT1. [step] consumes one keystream byte; when the
 * index wraps and [mutate][SecretKey.step] is set, one key byte is advanced by `7 * cycle`.
 */
private class SecretKey(secret: ByteArray) {
    private val pristine = secret.copyOf()
    private val key = secret.copyOf()
    val length: Int = secret.size
    private var index = 0
    private var cycle = 0

    init {
        require(length in 1..0x7f) { "key length must be 1..127: $length" }
    }

    fun reset() {
        index = 0
        cycle = 0
        pristine.copyInto(key)
    }

    fun step(b: Byte, mutate: Boolean): Byte {
        val out = (b.toInt() xor (key[index].toInt() and 0xff)) and 0xff
        index += 1
        if (index == length) {
            index = 0
            if (mutate) {
                cycle += 1
                val i = cycle % length
                key[i] = ((key[i].toInt() and 0xff) + 7 * cycle).toByte()
            }
        }
        return out.toByte()
    }

    fun fold(): Int {
        var x = 0
        for (b in pristine) x = x xor (b.toInt() and 0xff)
        return x and 0xff
    }
}
