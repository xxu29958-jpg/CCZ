package com.ccz.saveio

import com.ccz.core.battle.BattleState
import com.ccz.core.model.Faction
import com.ccz.core.save.SaveDecodeException
import com.ccz.core.save.SaveEnvelope
import com.ccz.core.save.SaveVersions
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SaveFileStoreTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun saveThenLoadRoundTripsEnvelope() {
        val target = dir.resolve("slot.save")
        val envelope = envelopeAtTurn(3)
        SaveFileStore.save(target, envelope)
        assertEquals(envelope, SaveFileStore.load(target))
    }

    @Test
    fun saveOverwritesExistingFileWithCompleteContent() {
        val target = dir.resolve("slot.save")
        SaveFileStore.save(target, envelopeAtTurn(1))
        val second = envelopeAtTurn(7)
        SaveFileStore.save(target, second)
        // the whole envelope is replaced (not a partial/merged write) and no temp lingers
        assertEquals(second, SaveFileStore.load(target))
        assertEquals(0L, tempFileCount())
    }

    @Test
    fun saveFailingToPlaceFailsClosedAndLeavesNoTemp() {
        // target is an existing directory, so the atomic move cannot place the file over it
        val target = dir.resolve("occupied")
        Files.createDirectory(target)
        assertFailsWith<SaveIoException> { SaveFileStore.save(target, envelopeAtTurn(1)) }
        assertEquals(0L, tempFileCount()) // the failed write left no stray temp behind
    }

    @Test
    fun saveLeavesNoTempFileBehind() {
        SaveFileStore.save(dir.resolve("slot.save"), envelopeAtTurn(1))
        assertEquals(0L, tempFileCount())
    }

    @Test
    fun saveCreatesMissingParentDirectories() {
        val target = dir.resolve("nested/deeper/slot.save")
        SaveFileStore.save(target, envelopeAtTurn(2))
        assertTrue(Files.exists(target))
        assertEquals(2, SaveFileStore.load(target).initialState.turn)
    }

    @Test
    fun loadMissingFileFailsClosed() {
        assertFailsWith<SaveIoException> { SaveFileStore.load(dir.resolve("nope.save")) }
    }

    @Test
    fun loadCorruptContentFailsClosed() {
        val target = dir.resolve("corrupt.save")
        Files.writeString(target, "not a save {{{")
        assertFailsWith<SaveDecodeException> { SaveFileStore.load(target) }
    }

    private fun tempFileCount(): Long =
        Files.list(dir).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".tmp") }.count()
        }

    private fun envelopeAtTurn(turn: Int): SaveEnvelope =
        SaveEnvelope(
            versions = SaveVersions(
                saveSchemaVersion = SaveVersions.SUPPORTED_SAVE_SCHEMA_VERSION,
                rulesVersion = 1,
                engineVersion = "0.1.0",
                nativeFormatVersion = "1",
                contentVersion = "1.0.0",
            ),
            initialState = BattleState(units = emptyMap(), turn = turn, active = Faction.PLAYER, rngState = 0L),
            commands = emptyList(),
        )
}
