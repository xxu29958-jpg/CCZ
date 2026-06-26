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

    private val frameKlen = 16

    @Test
    fun frameWithNegativePayloadLengthFailsClosedInsteadOfCrashing() {
        // A crafted/corrupt blob whose length field decodes negative previously reached
        // copyOfRange(start, start + payloadLen) with start+payloadLen < start → IllegalArgumentException,
        // escaping the codec's Bgt1FormatException contract and aborting the import. total is set to MATCH the
        // framed length so only the negative guard can throw (this test fails if that guard is deleted).
        val total = Bgt1Codec.FRAME_OVERHEAD + frameKlen + (-1)
        assertFailsWith<Bgt1FormatException> { Bgt1Codec.validateFrame(payloadLen = -1, realKlen = frameKlen, total = total, blobSize = 4096) }
    }

    @Test
    fun frameWithImplausiblyLargePayloadLengthFailsClosedOverflowProof() {
        // A near-Int.MAX payloadLen would overflow FRAME_OVERHEAD+realKlen+payloadLen to a negative wrap and slip
        // through the mismatch/overrun checks → the SAME copyOfRange escape. Rejecting payloadLen > blobSize
        // before any addition closes that band. Here a payload larger than the whole blob is malformed.
        assertFailsWith<Bgt1FormatException> { Bgt1Codec.validateFrame(payloadLen = 4096, realKlen = frameKlen, total = 0, blobSize = 100) }
    }

    @Test
    fun frameOverrunningTheBlobFailsClosed() {
        // payloadLen fits the blob (<= blobSize) and matches the declared total, but header+payload exceeds the
        // blob → fail closed (no over-read). Exercises the overrun check specifically, past the length guard.
        val payloadLen = 100
        val total = Bgt1Codec.FRAME_OVERHEAD + frameKlen + payloadLen
        assertFailsWith<Bgt1FormatException> { Bgt1Codec.validateFrame(payloadLen, realKlen = frameKlen, total = total, blobSize = payloadLen) }
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
