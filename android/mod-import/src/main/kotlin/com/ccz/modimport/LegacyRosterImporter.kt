package com.ccz.modimport

/**
 * Reads a real EEX stage script's battlefield DEPLOYMENT — the enemy ([CMD_ENEMY]) and ally ([CMD_FRIEND])
 * dispatch records that place each actor by hero id and cell (x, y). This is the "真 roster + 坐标" the faithful
 * full-stage port needs (ADR 0009 generator framework): the stage's actual units and where they stand, decoded
 * from the same binary the legacy engine parses — not a hand-designed approximation of the stage.
 *
 * Record layouts were reverse-engineered from `libMyGame.so` (Thumb disassembly of `ScriptDispatchEnemy` /
 * `ScriptDispatchFriend` and their per-slot `ScriptDispatchOne{Enemy,Friend}::initPara`, cross-checked against
 * each side's `HandleScript` actor-field copy) and verified against the real `S_00.eex_new` 大兴山 stage; the
 * full derivation lives in `docs/recon/eex-dispatch-layout.md`:
 *  - [CMD_ENEMY] `0x47` ScriptDispatchEnemy  : [ENEMY_SLOTS] fixed slots of [ENEMY_STRIDE] file bytes each
 *  - [CMD_FRIEND] `0x46` ScriptDispatchFriend: [FRIEND_SLOTS] fixed slots of [FRIEND_STRIDE] file bytes each
 * Within a slot the hero id is a signed 16-bit at [HID_OFF]; X / Y / level are signed 16-bit at side-specific
 * offsets (the legacy engine widens X/Y to int, but the low 16 bits hold the small map coordinate). A record
 * begins at a `<cmd:u8> 00 02 00` marker and its first slot starts 2 bytes past the cmd word; empty slots carry
 * a sentinel hid (<= 0). The player's OWN army is NOT in the stage file — it is deployed interactively in the
 * legacy game — so only the enemy and ally sides are recoverable here; the player roster is a battle-design
 * input (mirroring deploy level, ADR 0006), not a ported fact.
 *
 * Pure and fail-closed: malformed framing throws [EexFormatException] (via [EexCodec.parseHeader]); a
 * `<cmd> 00 02 00` byte coincidence is accepted as a real record ONLY when the WHOLE record is structurally
 * consistent — its first slot is an on-map deploy and every non-sentinel slot is a positive hid standing in
 * bounds — so the importer never fabricates a roster from binary noise. (A real stage staging units off-map
 * would trip the in-bounds gate; that is the conservative fail-closed trade — reject an ambiguous record rather
 * than admit garbage. Verified against S_00, whose every deployed slot is on-map.) Resolving a hid to a known
 * hero, and dropping unknowns, is the caller's job; this layer reports the structural deployment only.
 */
object LegacyRosterImporter {
    private const val CMD_ENEMY = 0x47
    private const val CMD_FRIEND = 0x46
    private const val ENEMY_SLOTS = 80
    private const val ENEMY_STRIDE = 0x38
    private const val FRIEND_SLOTS = 20
    private const val FRIEND_STRIDE = 0x34

    // Per-slot field offsets, relative to the slot start (= record body + slotIndex * stride).
    private const val HID_OFF = 0x2
    private const val LEVEL_OFF = 0x1a
    private const val ENEMY_X_OFF = 0xe
    private const val ENEMY_Y_OFF = 0x14
    private const val FRIEND_X_OFF = 0xa
    private const val FRIEND_Y_OFF = 0x10

    private const val TAG_LO = 0x02 // the `02 00` tag word that follows every dispatch cmd word
    private const val TAG_HI = 0x00

    /** Which side a dispatched actor fights for: the [CMD_ENEMY] roster or the [CMD_FRIEND] ally roster. */
    enum class Side { ENEMY, FRIEND }

    /** One dispatched actor: its legacy [hid], battlefield cell ([x], [y]), legacy deploy [level], and [side].
     *  [level] is the raw legacy field (the engine applies its own clamp/growth); callers coerce as needed. */
    data class RosterUnit(val hid: Int, val x: Int, val y: Int, val level: Int, val side: Side)

    /**
     * A stage's recovered deployment: the [units] of the INITIAL deploy (the first valid dispatch record of
     * each side, in file order — the deployment the stage opens with), plus [reinforcementRecords], the count
     * of FURTHER valid dispatch records found (mid-battle reinforcement waves the S-script triggers later).
     * Waves are counted so they are never silently dropped, but they are not folded into the opening [units].
     */
    data class Deployment(val units: List<RosterUnit>, val reinforcementRecords: Int)

    /** The fixed shape of one side's dispatch record, single-sourced so the scanner stays side-agnostic. */
    private data class Layout(
        val cmd: Int,
        val side: Side,
        val slots: Int,
        val stride: Int,
        val xOff: Int,
        val yOff: Int,
    )

    private val ENEMY_LAYOUT = Layout(CMD_ENEMY, Side.ENEMY, ENEMY_SLOTS, ENEMY_STRIDE, ENEMY_X_OFF, ENEMY_Y_OFF)
    private val FRIEND_LAYOUT = Layout(CMD_FRIEND, Side.FRIEND, FRIEND_SLOTS, FRIEND_STRIDE, FRIEND_X_OFF, FRIEND_Y_OFF)

    /**
     * Import the opening deployment from [blob] for a [mapWidth]×[mapHeight] battlefield. Enemy and ally sides
     * are each taken from their first structurally-valid dispatch record (lowest file offset); any later valid
     * records are reported as [Deployment.reinforcementRecords]. Throws [EexFormatException] on malformed EEX
     * framing (fail-closed).
     */
    fun importDeployment(blob: ByteArray, mapWidth: Int, mapHeight: Int): Deployment {
        require(mapWidth > 0 && mapHeight > 0) { "map size must be positive: ${mapWidth}x$mapHeight" }
        EexCodec.parseHeader(blob) // reject non-EEX / corrupt framing before scanning
        val opening = ArrayList<RosterUnit>()
        var waves = 0
        for (layout in listOf(ENEMY_LAYOUT, FRIEND_LAYOUT)) {
            val records = validRecords(blob, layout, mapWidth, mapHeight)
            records.firstOrNull()?.let { opening.addAll(it) }
            if (records.size > 1) waves += records.size - 1
        }
        return Deployment(opening, waves)
    }

    /** Every structurally-valid dispatch record of [layout]'s side, in file order (lowest offset first). On a
     *  hit the scan skips PAST the consumed record (not just +1), so a record's own in-bounds body bytes cannot
     *  spawn a nested same-side marker that would inflate the wave count; gaps between records are still scanned
     *  byte-by-byte (records are byte-aligned, not 2-byte aligned). */
    private fun validRecords(blob: ByteArray, layout: Layout, width: Int, height: Int): List<List<RosterUnit>> {
        val out = ArrayList<List<RosterUnit>>()
        val limit = blob.size - 4
        var i = 0
        while (i <= limit) {
            val record = if (isMarker(blob, i, layout.cmd)) readRecord(blob, i + 2, layout, width, height) else null
            if (record != null) {
                out.add(record)
                i += 2 + layout.slots * layout.stride // 2-byte cmd word + the record body just consumed
            } else {
                i += 1
            }
        }
        return out
    }

    /** True when [i] begins a `<cmd:u8> 00 02 00` dispatch-record marker (the cmd word then its `02 00` tag). */
    private fun isMarker(blob: ByteArray, i: Int, cmd: Int): Boolean =
        u8(blob, i) == cmd && u8(blob, i + 1) == 0x00 && u8(blob, i + 2) == TAG_LO && u8(blob, i + 3) == TAG_HI

    /**
     * Parse the [layout] record whose first slot starts at [body], or null when it is not a real record. Valid
     * iff: it fits in the blob, its FIRST slot is an on-map deploy (a real dispatch opens with a unit), and every
     * non-sentinel slot is a positive hid in bounds — a single non-sentinel off-map slot means the marker was a
     * byte coincidence, so the whole record is rejected (fail-closed).
     */
    private fun readRecord(blob: ByteArray, body: Int, layout: Layout, width: Int, height: Int): List<RosterUnit>? {
        if (body + layout.slots * layout.stride > blob.size) return null
        if (s16(blob, body + HID_OFF) <= 0) return null // a real record's slot 0 is a deployed unit
        val units = ArrayList<RosterUnit>()
        for (k in 0 until layout.slots) {
            val slot = body + k * layout.stride
            val hid = s16(blob, slot + HID_OFF)
            if (hid <= 0) continue // sentinel / empty slot
            val x = s16(blob, slot + layout.xOff)
            val y = s16(blob, slot + layout.yOff)
            if (x !in 0 until width || y !in 0 until height) return null // non-sentinel off-map -> coincidence
            units.add(RosterUnit(hid, x, y, s16(blob, slot + LEVEL_OFF), layout.side))
        }
        return units.ifEmpty { null }
    }

    private fun u8(blob: ByteArray, o: Int): Int = blob[o].toInt() and 0xff

    /** The signed little-endian 16-bit value at [o] (legacy `bytesToShort`: sign-extended low/high bytes). */
    private fun s16(blob: ByteArray, o: Int): Int {
        val v = EexCodec.u16le(blob, o)
        return if (v >= 0x8000) v - 0x10000 else v
    }
}
