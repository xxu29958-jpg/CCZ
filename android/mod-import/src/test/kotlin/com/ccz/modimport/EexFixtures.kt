package com.ccz.modimport

/**
 * Shared builders for synthetic EEX containers used by the EEX-generator tests (no dependency on the user's
 * uncommitted legacy files). Single-sourced so [EexCodecTest] and [LegacyScriptDecoderTest] frame blobs the
 * same way (cohesion gate: one definition of the on-disk shape).
 */
internal object EexFixtures {
    /** A valid EEX container wrapping [sections]: header (magic/version/offset table) then the section bytes. */
    fun eexBlob(vararg sections: ByteArray): ByteArray {
        val n = sections.size
        val headerSize = 0x0a + 4 * n // the header IS the offset table; first offset == header size
        val offsets = IntArray(n)
        var cur = headerSize
        for (i in 0 until n) { offsets[i] = cur; cur += sections[i].size }
        val out = ByteArray(cur)
        EexCodec.MAGIC.copyInto(out, 0)
        putU32(out, 0x04, 0x00000201) // version
        for (i in 0 until n) putU32(out, 0x0a + 4 * i, offsets[i])
        var p = headerSize
        for (s in sections) { s.copyInto(out, p); p += s.size }
        return out
    }

    /** A `<cmd:u16> 05 00 <utf8> 00` string operand record (the trailing byte is the NUL terminator). */
    fun strRec(cmd: Int, text: String): ByteArray {
        val t = text.toByteArray(Charsets.UTF_8)
        val b = ByteArray(4 + t.size + 1)
        b[0] = (cmd and 0xff).toByte(); b[1] = ((cmd ushr 8) and 0xff).toByte()
        b[2] = 0x05; b[3] = 0x00
        t.copyInto(b, 4)
        return b
    }

    /** One actor written into a [dispatchRec] slot: its [slot] index plus the fields the importer reads. */
    data class DispatchUnit(val slot: Int, val hid: Int, val x: Int, val y: Int, val level: Int)

    /** Per-side dispatch record layout decoded from the legacy VM. */
    data class DispatchLayout(val slots: Int, val stride: Int, val xOff: Int, val yOff: Int)

    /**
     * A dispatch record: `<cmd:u8> 00 02 00` then [slots] fixed slots of [stride] bytes. Each [units] entry
     * writes its actor (hid @+0x2, x @[xOff], y @[yOff], level @+0x1a, all signed little-endian) into its slot;
     * every other slot stays zero (hid 0 = an empty sentinel). Mirrors the on-disk shape [LegacyRosterImporter]
     * reads — the per-side field offsets are passed in by the test so they stay an independent spec cross-check.
     */
    fun dispatchRec(cmd: Int, layout: DispatchLayout, units: List<DispatchUnit>): ByteArray {
        val body = ByteArray(layout.slots * layout.stride)
        body[0] = 0x02; body[1] = 0x00 // the `02 00` tag word after the cmd (slot 0's +0x0 field)
        for (u in units) {
            val base = u.slot * layout.stride
            writeDispatchSlotTags(body, base, layout)
            putS16(body, base + 0x2, u.hid)
            putS16(body, base + layout.xOff, u.x)
            putS16(body, base + layout.yOff, u.y)
            putS16(body, base + 0x1a, u.level)
        }
        return byteArrayOf((cmd and 0xff).toByte(), 0x00) + body
    }

    private fun writeDispatchSlotTags(body: ByteArray, base: Int, layout: DispatchLayout) {
        val tags = when (layout.stride) {
            0x38 -> enemyDispatchTags
            0x34 -> friendDispatchTags
            else -> emptyList()
        }
        tags.forEach { (offset, value) -> putS16(body, base + offset, value) }
    }

    private val enemyDispatchTags = listOf(
        0x00 to 0x02,
        0x04 to 0x26,
        0x08 to 0x26,
        0x0c to 0x04,
        0x12 to 0x04,
        0x18 to 0x2b,
        0x1c to 0x3e,
        0x20 to 0x45,
        0x24 to 0x07,
        0x28 to 0x02,
        0x2c to 0x04,
        0x32 to 0x04,
    )

    private val friendDispatchTags = listOf(
        0x00 to 0x02,
        0x04 to 0x26,
        0x08 to 0x04,
        0x0e to 0x04,
        0x14 to 0x2b,
        0x18 to 0x3e,
        0x1c to 0x45,
        0x20 to 0x07,
        0x24 to 0x02,
        0x28 to 0x04,
        0x2e to 0x04,
    )

    private fun putS16(b: ByteArray, o: Int, v: Int) {
        b[o] = (v and 0xff).toByte()
        b[o + 1] = ((v ushr 8) and 0xff).toByte()
    }

    fun putU32(b: ByteArray, o: Int, v: Int) {
        b[o] = (v and 0xff).toByte()
        b[o + 1] = ((v ushr 8) and 0xff).toByte()
        b[o + 2] = ((v ushr 16) and 0xff).toByte()
        b[o + 3] = ((v ushr 24) and 0xff).toByte()
    }
}
