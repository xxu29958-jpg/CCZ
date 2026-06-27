package com.ccz.modimport

import com.ccz.modimport.EexFixtures.DispatchUnit
import com.ccz.modimport.EexFixtures.dispatchRec
import com.ccz.modimport.EexFixtures.eexBlob
import com.ccz.modimport.LegacyRosterImporter.Side
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Drives [LegacyRosterImporter] on synthetic EEX dispatch records. The per-side field offsets are written here
 * literally (enemy x@+0xe/y@+0x14, friend x@+0xa/y@+0x10, hid@+0x2, level@+0x1a) so the test is an INDEPENDENT
 * encoding of the layout reverse-engineered from `libMyGame.so` — a wrong offset in the importer fails here.
 * Map dimensions mirror the real 大兴山 stage (terrainMap_1 = 23×16).
 */
class LegacyRosterImporterTest {
    // Confirmed dispatch layout (docs/recon/eex-dispatch-layout.md), encoded independently of the importer.
    private val enemyCmd = 0x47
    private val enemySlots = 80
    private val enemyStride = 0x38
    private val enemyX = 0xe
    private val enemyY = 0x14
    private val friendCmd = 0x46
    private val friendSlots = 20
    private val friendStride = 0x34
    private val friendX = 0xa
    private val friendY = 0x10
    private val mapW = 23
    private val mapH = 16

    private fun enemyRec(units: List<DispatchUnit>) =
        dispatchRec(enemyCmd, enemySlots, enemyStride, enemyX, enemyY, units)

    private fun friendRec(units: List<DispatchUnit>) =
        dispatchRec(friendCmd, friendSlots, friendStride, friendX, friendY, units)

    @Test
    fun importsEnemyAndFriendDeploymentAtRealOffsets() {
        // Two enemies (slot 0 and slot 3; slots 1-2 empty) and one ally — mirroring S_00's main 0x47/0x46 records.
        val blob = eexBlob(
            enemyRec(listOf(DispatchUnit(0, hid = 596, x = 20, y = 5, level = 2), DispatchUnit(3, 226, 19, 4, 2))),
            friendRec(listOf(DispatchUnit(0, hid = 181, x = 1, y = 3, level = 3))),
        )
        val d = LegacyRosterImporter.importDeployment(blob, mapW, mapH)
        assertEquals(
            listOf(
                LegacyRosterImporter.RosterUnit(596, 20, 5, 2, Side.ENEMY),
                LegacyRosterImporter.RosterUnit(226, 19, 4, 2, Side.ENEMY),
                LegacyRosterImporter.RosterUnit(181, 1, 3, 3, Side.FRIEND),
            ),
            d.units,
            "both enemy slots (empty slots skipped) then the ally, each at the disassembly-derived offsets",
        )
        assertEquals(0, d.reinforcementRecords)
    }

    @Test
    fun acceptsUnitsOnTheMapBoundary() {
        val blob = eexBlob(enemyRec(listOf(DispatchUnit(0, 1, x = 0, y = 0, level = 1), DispatchUnit(1, 2, mapW - 1, mapH - 1, 1))))
        val d = LegacyRosterImporter.importDeployment(blob, mapW, mapH)
        assertEquals(listOf(0 to 0, (mapW - 1) to (mapH - 1)), d.units.map { it.x to it.y })
    }

    @Test
    fun rejectsRecordWhenANonSentinelSlotIsOffMap() {
        // slot 1 carries a real (positive) hid but an off-map Y — a byte coincidence, so the WHOLE record is
        // rejected fail-closed. The valid ally record is still imported, proving rejection is record-scoped.
        val blob = eexBlob(
            enemyRec(listOf(DispatchUnit(0, 596, 20, 5, 2), DispatchUnit(1, 700, 5, y = mapH + 4, level = 2))),
            friendRec(listOf(DispatchUnit(0, 181, 1, 3, 3))),
        )
        val d = LegacyRosterImporter.importDeployment(blob, mapW, mapH)
        assertEquals(listOf(Side.FRIEND), d.units.map { it.side }, "the off-map enemy record is dropped; the ally survives")
        assertEquals(181, d.units.single().hid)
    }

    @Test
    fun rejectsRecordWhoseFirstSlotIsEmpty() {
        // A `47 00 02 00` marker whose slot 0 is a sentinel (hid 0) is not a real dispatch — a real record opens
        // with a deployed unit. Nothing is imported (and it is not even counted as a reinforcement record).
        val blob = eexBlob(enemyRec(listOf(DispatchUnit(2, 596, 20, 5, 2))))
        val d = LegacyRosterImporter.importDeployment(blob, mapW, mapH)
        assertTrue(d.units.isEmpty(), "slot-0-empty record is rejected")
        assertEquals(0, d.reinforcementRecords)
    }

    @Test
    fun countsLaterValidRecordsAsReinforcementWaves() {
        // Two valid enemy records: the first (lowest offset) is the opening deployment; the second is a wave.
        val blob = eexBlob(
            enemyRec(listOf(DispatchUnit(0, 596, 20, 5, 2), DispatchUnit(1, 594, 19, 5, 2))),
            enemyRec(listOf(DispatchUnit(0, 839, 19, 3, 2))),
        )
        val d = LegacyRosterImporter.importDeployment(blob, mapW, mapH)
        assertEquals(listOf(596, 594), d.units.map { it.hid }, "only the opening record's units are in the deployment")
        assertEquals(1, d.reinforcementRecords, "the second valid record is surfaced as a wave, not dropped")
    }

    @Test
    fun failsClosedOnMalformedFraming() {
        assertFailsWith<EexFormatException> {
            LegacyRosterImporter.importDeployment(ByteArray(32) { 0x7a }, mapW, mapH)
        }
    }

    @Test
    fun rejectsNonPositiveMapSize() {
        val blob = eexBlob(enemyRec(listOf(DispatchUnit(0, 1, 0, 0, 1))))
        assertFailsWith<IllegalArgumentException> { LegacyRosterImporter.importDeployment(blob, 0, mapH) }
        assertFailsWith<IllegalArgumentException> { LegacyRosterImporter.importDeployment(blob, mapW, -1) }
    }
}
