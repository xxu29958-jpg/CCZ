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

    fun putU32(b: ByteArray, o: Int, v: Int) {
        b[o] = (v and 0xff).toByte()
        b[o + 1] = ((v ushr 8) and 0xff).toByte()
        b[o + 2] = ((v ushr 16) and 0xff).toByte()
        b[o + 3] = ((v ushr 24) and 0xff).toByte()
    }
}
