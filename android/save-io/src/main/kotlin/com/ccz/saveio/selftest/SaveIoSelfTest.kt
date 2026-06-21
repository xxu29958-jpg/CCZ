package com.ccz.saveio.selftest

import com.ccz.core.battle.BattleState
import com.ccz.core.model.Faction
import com.ccz.core.save.SaveEnvelope
import com.ccz.core.save.SaveVersions
import com.ccz.saveio.SaveFileStore
import java.nio.file.Files

fun main() {
    val dir = Files.createTempDirectory("ccz-save-io-selftest")
    val target = dir.resolve("slot.save")
    val envelope = sampleEnvelope()

    SaveFileStore.save(target, envelope)
    check(SaveFileStore.load(target) == envelope) {
        "save-io round-trip produced a different envelope"
    }

    // an overwrite must stay atomic and leave no temp file behind
    SaveFileStore.save(target, envelope)
    val strays = Files.list(dir).use { stream ->
        stream.filter { it.fileName.toString().endsWith(".tmp") }.count()
    }
    check(strays == 0L) { "save-io left $strays stray temp file(s) behind" }

    Files.deleteIfExists(target)
    Files.deleteIfExists(dir)
    println("OK save-io atomic write/read self-test passed")
}

private fun sampleEnvelope(): SaveEnvelope =
    SaveEnvelope(
        versions = SaveVersions(
            saveSchemaVersion = SaveVersions.SUPPORTED_SAVE_SCHEMA_VERSION,
            rulesVersion = 1,
            engineVersion = "0.1.0",
            nativeFormatVersion = "1",
            contentVersion = "1.0.0",
        ),
        initialState = BattleState(units = emptyMap(), turn = 1, active = Faction.PLAYER, rngState = 0L),
        commands = emptyList(),
    )
