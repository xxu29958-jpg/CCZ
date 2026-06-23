package com.ccz.modimport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Bgt1CodecTest {
    private val key = "Dgm5Y54yp9p5nMzU".toByteArray(Charsets.US_ASCII) // 16 bytes, mirrors a real resource key

    @Test
    fun roundTripsAcrossAlignedAndCoprimeSecondaryKeyLengths() {
        val payload = sampleJson(rows = 40)
        // secondary-key lengths vs the 16-byte primary: aligned (16,32), gcd>1 (24), and coprime (19,21,23,31)
        for (klen in intArrayOf(16, 32, 24, 19, 21, 23, 31)) {
            val blob = Bgt1Codec.encrypt(payload, key, secondaryKey(klen))
            assertTrue(Bgt1Codec.isBgt1(blob), "encrypted blob must carry BGT1 magic (klen=$klen)")
            assertEquals(
                payload.toList(),
                Bgt1Codec.decrypt(blob, key).toList(),
                "round-trip must hold for secondary key length $klen (mutation desync guard)",
            )
        }
    }

    @Test
    fun roundTripsPayloadsSpanningManyMutationCycles() {
        for (size in intArrayOf(1, 15, 16, 17, 256, 4097)) {
            val payload = ByteArray(size) { (it * 31 + 7).toByte() }
            val blob = Bgt1Codec.encrypt(payload, key, secondaryKey(19))
            assertEquals(payload.toList(), Bgt1Codec.decrypt(blob, key).toList(), "size=$size")
        }
    }

    @Test
    fun wrongKeyIsRejectedNotSilentlyMisdecoded() {
        val blob = Bgt1Codec.encrypt(sampleJson(rows = 8), key, secondaryKey(19))
        val wrong = "WRONGKEYwrongkey".toByteArray(Charsets.US_ASCII)
        assertFailsWith<Bgt1FormatException> { Bgt1Codec.decrypt(blob, wrong) }
    }

    @Test
    fun corruptedChecksumFailsClosed() {
        val blob = Bgt1Codec.encrypt(sampleJson(rows = 4), key, secondaryKey(16))
        blob[0x0c] = (blob[0x0c] + 1).toByte()
        assertFailsWith<Bgt1FormatException> { Bgt1Codec.decrypt(blob, key) }
    }

    @Test
    fun nonBgt1InputIsRejected() {
        assertFalse(Bgt1Codec.isBgt1("{}".toByteArray()))
        assertFailsWith<Bgt1FormatException> { Bgt1Codec.decrypt("not a bgt1 blob at all".toByteArray(), key) }
    }

    private fun sampleJson(rows: Int): ByteArray {
        val sb = StringBuilder("﻿[\r\n")
        for (i in 1..rows) {
            sb.append("  {\r\n    \"id\": ").append(i).append(",\r\n    \"name\": \"unit_").append(i).append("\"\r\n  }")
            sb.append(if (i < rows) ",\r\n" else "\r\n")
        }
        sb.append("]\r\n")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun secondaryKey(len: Int): ByteArray = ByteArray(len) { (it * 17 + 3).toByte() }
}
