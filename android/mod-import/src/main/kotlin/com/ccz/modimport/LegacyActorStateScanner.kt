package com.ccz.modimport

/**
 * Offline probe for legacy actor state records that influence whether a dispatched actor is visible/available.
 * This scanner only records byte-structured references for migration reports; it does not decide battle
 * semantics and must not be used as runtime content.
 */
object LegacyActorStateScanner {
    data class ActorStateRefs(
        val visible: List<ActorVisibleRecord>,
        val armyChanges: List<ArmyChangeRecord>,
    ) {
        fun forActor(actor: Int): List<ActorStateRef> =
            visible.filter { it.actor == actor }.map { ActorStateRef.Visible(it) } +
                armyChanges.filter { it.actor == actor }.map { ActorStateRef.ArmyChange(it) }
    }

    sealed interface ActorStateRef {
        data class Visible(val record: ActorVisibleRecord) : ActorStateRef
        data class ArmyChange(val record: ArmyChangeRecord) : ActorStateRef
    }

    data class ActorVisibleRecord(val offset: Int, val actor: Int, val mode: Int, val argument: Int)

    data class ArmyChangeRecord(val offset: Int, val actor: Int, val state: Int, val mode: Int)

    fun scan(
        blob: ByteArray,
        profile: LegacyEexOpcodeProfile = LegacyEexOpcodeProfile.LEGACY_DECODED,
    ): ActorStateRefs {
        EexCodec.parseHeader(blob)
        return ActorStateRefs(
            visible = scanActorVisible(blob, profile.actorVisibleCommand),
            armyChanges = scanArmyChange(blob, profile.armyChangeCommand),
        )
    }

    private fun scanActorVisible(blob: ByteArray, cmd: Int): List<ActorVisibleRecord> {
        val out = ArrayList<ActorVisibleRecord>()
        val limit = blob.size - ACTOR_VISIBLE_BYTES
        for (i in 0..limit) {
            if (u16(blob, i) == cmd && actorVisibleTagsMatch(blob, i)) {
                out.add(
                    ActorVisibleRecord(
                        offset = i,
                        actor = s16(blob, i + 0x08),
                        mode = s16(blob, i + 0x04),
                        argument = EexCodec.u32le(blob, i + 0x0c),
                    ),
                )
            }
        }
        return out
    }

    private fun scanArmyChange(blob: ByteArray, cmd: Int): List<ArmyChangeRecord> {
        val out = ArrayList<ArmyChangeRecord>()
        val limit = blob.size - ARMY_CHANGE_BYTES
        for (i in 0..limit) {
            if (u16(blob, i) == cmd && armyChangeTagsMatch(blob, i)) {
                out.add(
                    ArmyChangeRecord(
                        offset = i,
                        actor = s16(blob, i + 0x04),
                        state = s16(blob, i + 0x08),
                        mode = s16(blob, i + 0x0c),
                    ),
                )
            }
        }
        return out
    }

    private fun actorVisibleTagsMatch(blob: ByteArray, offset: Int): Boolean =
        s16(blob, offset + 0x02) == 0x40 &&
            s16(blob, offset + 0x06) == 0x02 &&
            s16(blob, offset + 0x0a) == 0x04

    private fun armyChangeTagsMatch(blob: ByteArray, offset: Int): Boolean =
        s16(blob, offset + 0x02) == 0x02 &&
            s16(blob, offset + 0x06) == 0x0e &&
            s16(blob, offset + 0x0a) == 0x3e

    private fun u16(blob: ByteArray, offset: Int): Int = EexCodec.u16le(blob, offset)

    private fun s16(blob: ByteArray, offset: Int): Int {
        val v = EexCodec.u16le(blob, offset)
        return if (v >= 0x8000) v - 0x10000 else v
    }

    private const val ACTOR_VISIBLE_BYTES = 0x10
    private const val ARMY_CHANGE_BYTES = 0x0e
}
