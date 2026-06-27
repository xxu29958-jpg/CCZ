package com.ccz.modimport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Drives [EexCodec] on synthetic EEX containers (no dependency on the user's uncommitted legacy files,
 * mirroring [Bgt1CodecTest]). Proves the byte-level layer reads framing + command-tagged strings exactly as
 * the verified real-file model (`docs/recon/`) and fails closed on malformed input.
 */
class EexCodecTest {
    // ---- synthetic EEX builders ----

    /** A valid EEX container wrapping [sections]: header (magic/version/offset table) then the section bytes. */
    private fun eexBlob(vararg sections: ByteArray): ByteArray {
        val n = sections.size
        val headerSize = 0x0a + 4 * n // header IS the offset table; first offset == header size
        val offsets = IntArray(n)
        var cur = headerSize
        for (i in 0 until n) { offsets[i] = cur; cur += sections[i].size }
        val out = ByteArray(cur)
        EexCodec.MAGIC.copyInto(out, 0)
        putU32(out, 0x04, 0x00000201)            // version
        for (i in 0 until n) putU32(out, 0x0a + 4 * i, offsets[i])
        var p = headerSize
        for (s in sections) { s.copyInto(out, p); p += s.size }
        return out
    }

    /** A `<cmd:u16> 05 00 <utf8> 00` string operand record. */
    private fun strRec(cmd: Int, text: String): ByteArray {
        val t = text.toByteArray(Charsets.UTF_8)
        val b = ByteArray(4 + t.size + 1)
        b[0] = (cmd and 0xff).toByte(); b[1] = ((cmd ushr 8) and 0xff).toByte()
        b[2] = 0x05; b[3] = 0x00
        t.copyInto(b, 4)
        return b // trailing byte already 0x00 (NUL terminator)
    }

    private fun putU32(b: ByteArray, o: Int, v: Int) {
        b[o] = (v and 0xff).toByte()
        b[o + 1] = ((v ushr 8) and 0xff).toByte()
        b[o + 2] = ((v ushr 16) and 0xff).toByte()
        b[o + 3] = ((v ushr 24) and 0xff).toByte()
    }

    // ---- tests ----

    @Test
    fun detectsMagicAndParsesFraming() {
        val blob = eexBlob(strRec(0x14, "甲"), strRec(0x18, "乙"))
        assertTrue(EexCodec.isEex(blob))
        val h = EexCodec.parseHeader(blob)
        assertEquals(0x00000201, h.version)
        assertEquals(0x0a + 8, h.headerSize, "header size = 0x0a + 4*sectionCount")
        assertEquals(2, h.sectionOffsets.size)
        assertEquals(h.headerSize, h.sectionOffsets.first(), "first offset equals header size")
        assertEquals(blob.size, h.size)
        // offsets strictly ascending and in-bounds (the framing invariant)
        assertTrue(h.sectionOffsets.zipWithNext().all { (a, b) -> a < b })
        assertTrue(h.sectionOffsets.all { it <= h.size })
    }

    @Test
    fun extractsCommandTaggedStringsInOrder() {
        val blob = eexBlob(
            strRec(0x14, "邓将军，今日借粮重任，托付于你。"),
            strRec(0x19, "胜利条件\n★·全灭敌军。"),
        )
        val s = EexCodec.strings(blob)
        assertEquals(2, s.size)
        assertEquals(0x14, s[0].cmd)
        assertEquals("邓将军，今日借粮重任，托付于你。", s[0].text)
        assertEquals(0x19, s[1].cmd)
        assertTrue("全灭敌军" in s[1].text)
        assertTrue(s[0].offset < s[1].offset, "strings come back in file order")
    }

    @Test
    fun skipsBinaryCoincidenceThatIsNotCleanUtf8() {
        // a `05 00` followed by an invalid UTF-8 lead byte (0xFF) then NUL is binary, not a string -> skipped
        val binary = byteArrayOf(0x99.toByte(), 0x00, 0x05, 0x00, 0xFF.toByte(), 0xFE.toByte(), 0x00)
        val blob = eexBlob(strRec(0x14, "真台词"), binary)
        val s = EexCodec.strings(blob)
        assertEquals(1, s.size, "only the clean-UTF-8 string is emitted, the binary run is skipped")
        assertEquals("真台词", s[0].text)
    }

    @Test
    fun rejectsBadMagicFailClosed() {
        val blob = eexBlob(strRec(0x14, "x")).copyOf().also { it[0] = 'Z'.code.toByte() }
        assertFalse(EexCodec.isEex(blob))
        assertFailsWith<EexFormatException> { EexCodec.parseHeader(blob) }
    }

    @Test
    fun rejectsUnsupportedVersionFailClosed() {
        val blob = eexBlob(strRec(0x14, "x"))
        putU32(blob, 0x04, 0x00000999) // tamper the version
        assertFailsWith<EexFormatException> { EexCodec.parseHeader(blob) }
    }

    @Test
    fun rejectsCorruptHeaderSizeFailClosed() {
        // 0x0a holds the header size (which is also the first section offset); a huge value is out of range.
        val blob = eexBlob(strRec(0x14, "x"), strRec(0x18, "y"))
        putU32(blob, 0x0a, 0x7fffffff)
        assertFailsWith<EexFormatException> { EexCodec.parseHeader(blob) }
    }

    @Test
    fun rejectsOutOfBoundsSectionOffsetFailClosed() {
        // tamper the SECOND offset (0x0e — NOT aliased with the header size) to point past EOF
        val blob = eexBlob(strRec(0x14, "x"), strRec(0x18, "y"))
        putU32(blob, 0x0e, blob.size + 50)
        assertFailsWith<EexFormatException> { EexCodec.parseHeader(blob) }
    }

    @Test
    fun rejectsNonAscendingSectionOffsetsFailClosed() {
        // drop the second offset below the first (header size) so the table is not strictly ascending
        val blob = eexBlob(strRec(0x14, "x"), strRec(0x18, "y"))
        putU32(blob, 0x0e, 1)
        assertFailsWith<EexFormatException> { EexCodec.parseHeader(blob) }
    }

    @Test
    fun stringWithoutTerminatorIsSkipped() {
        // a `05 00` marker with no following NUL is not a string (no terminator) -> skipped, not fabricated
        val blob = eexBlob(strRec(0x14, "真话"), byteArrayOf(0x99.toByte(), 0x00, 0x05, 0x00, 0x41, 0x42))
        val s = EexCodec.strings(blob)
        assertEquals(1, s.size)
        assertEquals("真话", s[0].text)
    }
}
