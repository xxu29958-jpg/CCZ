package com.ccz.modimport

import com.ccz.modimport.EexFixtures.eexBlob
import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyActorStateScannerTest {
    @Test
    fun scansLegacyActorVisibleAndArmyChangeRecords() {
        val refs = LegacyActorStateScanner.scan(
            eexBlob(
                actorVisibleRec(cmd = 0x4c, actor = 603, mode = 0, argument = 4),
                armyChangeRec(cmd = 0x3b, actor = 603, state = 255, mode = 0),
            ),
            LegacyEexOpcodeProfile.LEGACY_DECODED,
        )

        assertEquals(listOf(603), refs.visible.map { it.actor })
        assertEquals(listOf(0 to 4), refs.visible.map { it.mode to it.argument })
        assertEquals(listOf(603), refs.armyChanges.map { it.actor })
        assertEquals(listOf(255 to 0), refs.armyChanges.map { it.state to it.mode })
        assertEquals(2, refs.forActor(603).size)
    }

    @Test
    fun scansCurrentApkRemappedActorStateRecords() {
        val refs = LegacyActorStateScanner.scan(
            eexBlob(
                actorVisibleRec(cmd = 0xed, actor = 650, mode = 0, argument = 1),
                armyChangeRec(cmd = 0xba, actor = 650, state = 1, mode = 0),
            ),
            LegacyEexOpcodeProfile.TRSSGSHZ_CURRENT_APK,
        )

        assertEquals(listOf(650), refs.visible.map { it.actor })
        assertEquals(listOf(650), refs.armyChanges.map { it.actor })
    }

    private fun actorVisibleRec(cmd: Int, actor: Int, mode: Int, argument: Int): ByteArray =
        ByteArray(0x10).also {
            putS16(it, 0x00, cmd)
            putS16(it, 0x02, 0x40)
            putS16(it, 0x04, mode)
            putS16(it, 0x06, 0x02)
            putS16(it, 0x08, actor)
            putS16(it, 0x0a, 0x04)
            EexFixtures.putU32(it, 0x0c, argument)
        }

    private fun armyChangeRec(cmd: Int, actor: Int, state: Int, mode: Int): ByteArray =
        ByteArray(0x0e).also {
            putS16(it, 0x00, cmd)
            putS16(it, 0x02, 0x02)
            putS16(it, 0x04, actor)
            putS16(it, 0x06, 0x0e)
            putS16(it, 0x08, state)
            putS16(it, 0x0a, 0x3e)
            putS16(it, 0x0c, mode)
        }

    private fun putS16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xff).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xff).toByte()
    }
}
