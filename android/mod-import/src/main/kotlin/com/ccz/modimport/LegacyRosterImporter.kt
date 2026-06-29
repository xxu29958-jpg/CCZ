package com.ccz.modimport

/**
 * Reads a real EEX stage script's battlefield DEPLOYMENT — the enemy and ally
 * dispatch records that place each actor by hero id and cell (x, y). This is the "真 roster + 坐标" the faithful
 * full-stage port needs (ADR 0009 generator framework): the stage's actual units and where they stand, decoded
 * from the same binary the legacy engine parses — not a hand-designed approximation of the stage.
 *
 * Record layouts were reverse-engineered from `libMyGame.so` (Thumb disassembly of `ScriptDispatchEnemy` /
 * `ScriptDispatchFriend` and their per-slot `ScriptDispatchOne{Enemy,Friend}::initPara`, cross-checked against
 * each side's `HandleScript` actor-field copy) and verified against the real `S_00.eex_new` 大兴山 stage; the
 * full derivation lives in `docs/recon/eex-dispatch-layout.md`. The payload layout is stable, but command words
 * are selected by [LegacyEexOpcodeProfile]: old decoded references use `0x46/0x47`, while the current E-drive
 * APK pack remaps the same friend/enemy dispatch payloads to `0xdb/0xde`.
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

    /** Which side a dispatched actor fights for: the enemy roster or the ally roster. */
    enum class Side { ENEMY, FRIEND }

    /** One dispatched actor: its legacy [hid], battlefield cell ([x], [y]), legacy deploy [level], and [side].
     *  [level] is the raw legacy field (the engine applies its own clamp/growth); callers coerce as needed. */
    data class RosterUnit(val hid: Int, val x: Int, val y: Int, val level: Int, val side: Side)

    /** Offline-only trace for reverse-engineering import decisions; runtime content should consume [RosterUnit]. */
    data class RosterTrace(val unit: RosterUnit, val recordOffset: Int, val slot: Int, val rawWords: List<Int>)

    /**
     * A stage's recovered deployment: the [units] of the INITIAL deploy (the first valid dispatch record of
     * each side, in file order — the deployment the stage opens with), plus [reinforcementRecords], the count
     * of FURTHER valid dispatch records found (mid-battle reinforcement waves the S-script triggers later).
     * Waves are counted so they are never silently dropped, but they are not folded into the opening [units].
     */
    data class Deployment(
        val units: List<RosterUnit>,
        val reinforcementRecords: Int,
        val traces: List<RosterTrace> = emptyList(),
    )

    /** The fixed shape of one side's dispatch record, single-sourced so the scanner stays side-agnostic. */
    private data class Layout(
        val cmd: Int,
        val side: Side,
        val shape: SlotShape,
    )

    private data class SlotShape(
        val slots: Int,
        val stride: Int,
        val xOff: Int,
        val yOff: Int,
        val tagWords: List<TagWord>,
    )

    private data class TagWord(val offset: Int, val value: Int)

    private data class DispatchRecord(val units: List<RosterUnit>, val traces: List<RosterTrace>)

    private val ENEMY_TAGS = listOf(
        TagWord(0x00, 0x02),
        TagWord(0x04, 0x26),
        TagWord(0x08, 0x26),
        TagWord(0x0c, 0x04),
        TagWord(0x12, 0x04),
        TagWord(0x18, 0x2b),
        TagWord(0x1c, 0x3e),
        TagWord(0x20, 0x45),
        TagWord(0x24, 0x07),
        TagWord(0x28, 0x02),
        TagWord(0x2c, 0x04),
        TagWord(0x32, 0x04),
    )

    private val FRIEND_TAGS = listOf(
        TagWord(0x00, 0x02),
        TagWord(0x04, 0x26),
        TagWord(0x08, 0x04),
        TagWord(0x0e, 0x04),
        TagWord(0x14, 0x2b),
        TagWord(0x18, 0x3e),
        TagWord(0x1c, 0x45),
        TagWord(0x20, 0x07),
        TagWord(0x24, 0x02),
        TagWord(0x28, 0x04),
        TagWord(0x2e, 0x04),
    )

    private val ENEMY_SHAPE = SlotShape(ENEMY_SLOTS, ENEMY_STRIDE, ENEMY_X_OFF, ENEMY_Y_OFF, ENEMY_TAGS)
    private val FRIEND_SHAPE = SlotShape(FRIEND_SLOTS, FRIEND_STRIDE, FRIEND_X_OFF, FRIEND_Y_OFF, FRIEND_TAGS)

    /**
     * Import the opening deployment from [blob] for a [mapWidth]×[mapHeight] battlefield. Enemy and ally sides
     * are each taken from their first structurally-valid dispatch record (lowest file offset); any later valid
     * records are reported as [Deployment.reinforcementRecords]. Throws [EexFormatException] on malformed EEX
     * framing (fail-closed).
     */
    fun importDeployment(blob: ByteArray, mapWidth: Int, mapHeight: Int): Deployment {
        val profile = detectOpcodeProfile(blob, mapWidth, mapHeight)
        return importDeployment(blob, mapWidth, mapHeight, profile)
    }

    /**
     * Same as [importDeployment], but pinned to an already chosen [profile]. Batch planners should prefer this
     * overload after detecting the package profile once from an anchor stage; it prevents stale-profile command
     * words from being scanned as real records in later files.
     */
    fun importDeployment(
        blob: ByteArray,
        mapWidth: Int,
        mapHeight: Int,
        profile: LegacyEexOpcodeProfile,
    ): Deployment {
        require(mapWidth > 0 && mapHeight > 0) { "map size must be positive: ${mapWidth}x$mapHeight" }
        EexCodec.parseHeader(blob) // reject non-EEX / corrupt framing before scanning
        val opening = ArrayList<RosterUnit>()
        val traces = ArrayList<RosterTrace>()
        var waves = 0
        for (layout in layouts(profile)) {
            val records = validRecords(blob, layout, mapWidth, mapHeight)
            records.firstOrNull()?.let {
                opening.addAll(it.units)
                traces.addAll(it.traces)
            }
            if (records.size > 1) waves += records.size - 1
        }
        return Deployment(opening, waves, traces)
    }

    /**
     * Detect which known opcode profile is present in a single script by looking for structurally valid dispatch
     * payloads. Current APK remaps are tried before the old decoded ids so an old `0x47` byte coincidence inside
     * the new profile cannot win just because it happens to parse as an on-map record.
     */
    fun detectOpcodeProfile(blob: ByteArray, mapWidth: Int, mapHeight: Int): LegacyEexOpcodeProfile {
        require(mapWidth > 0 && mapHeight > 0) { "map size must be positive: ${mapWidth}x$mapHeight" }
        EexCodec.parseHeader(blob)
        return LegacyEexOpcodeProfile.detectionOrder.firstOrNull { profile ->
            layouts(profile).any { validRecords(blob, it, mapWidth, mapHeight).isNotEmpty() }
        } ?: LegacyEexOpcodeProfile.LEGACY_DECODED
    }

    private fun layouts(profile: LegacyEexOpcodeProfile): List<Layout> = listOf(
        Layout(profile.enemyDispatchCommand, Side.ENEMY, ENEMY_SHAPE),
        Layout(profile.friendDispatchCommand, Side.FRIEND, FRIEND_SHAPE),
    )

    /** Every structurally-valid dispatch record of [layout]'s side, in file order (lowest offset first). On a
     *  hit the scan skips PAST the consumed record (not just +1), so a record's own in-bounds body bytes cannot
     *  spawn a nested same-side marker that would inflate the wave count; gaps between records are still scanned
     *  byte-by-byte (records are byte-aligned, not 2-byte aligned). */
    private fun validRecords(blob: ByteArray, layout: Layout, width: Int, height: Int): List<DispatchRecord> {
        val out = ArrayList<DispatchRecord>()
        val limit = blob.size - 4
        var i = 0
        while (i <= limit) {
            val record = if (isMarker(blob, i, layout.cmd)) readRecord(blob, i + 2, layout, width, height) else null
            if (record != null) {
                out.add(record)
                i += 2 + layout.shape.slots * layout.shape.stride // 2-byte cmd word + the record body consumed
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
    private fun readRecord(blob: ByteArray, body: Int, layout: Layout, width: Int, height: Int): DispatchRecord? {
        val shape = layout.shape
        if (body + shape.slots * shape.stride > blob.size) return null
        if (s16(blob, body + HID_OFF) <= 0) return null // a real record's slot 0 is a deployed unit
        val units = ArrayList<RosterUnit>()
        val traces = ArrayList<RosterTrace>()
        for (k in 0 until shape.slots) {
            val slot = body + k * shape.stride
            val hid = s16(blob, slot + HID_OFF)
            if (hid <= 0) continue // sentinel / empty slot
            if (!slotTagsMatch(blob, slot, shape.tagWords)) return null
            val x = s16(blob, slot + shape.xOff)
            val y = s16(blob, slot + shape.yOff)
            if (x !in 0 until width || y !in 0 until height) return null // non-sentinel off-map -> coincidence
            val unit = RosterUnit(hid, x, y, s16(blob, slot + LEVEL_OFF), layout.side)
            units.add(unit)
            traces.add(
                RosterTrace(unit, recordOffset = body - 2, slot = k, rawWords = slotWords(blob, slot, shape.stride)),
            )
        }
        return if (units.isEmpty()) null else DispatchRecord(units, traces)
    }

    private fun slotTagsMatch(blob: ByteArray, slot: Int, tags: List<TagWord>): Boolean =
        tags.all { tag -> s16(blob, slot + tag.offset) == tag.value }

    private fun slotWords(blob: ByteArray, slot: Int, stride: Int): List<Int> =
        (0 until stride step 2).map { offset -> s16(blob, slot + offset) }

    private fun u8(blob: ByteArray, o: Int): Int = blob[o].toInt() and 0xff

    /** The signed little-endian 16-bit value at [o] (legacy `bytesToShort`: sign-extended low/high bytes). */
    private fun s16(blob: ByteArray, o: Int): Int {
        val v = EexCodec.u16le(blob, o)
        return if (v >= 0x8000) v - 0x10000 else v
    }
}
