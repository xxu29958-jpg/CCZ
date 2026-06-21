package com.ccz.saveio.selftest

import com.ccz.core.battle.BattleState
import com.ccz.core.model.Faction
import com.ccz.core.save.SaveEnvelope
import com.ccz.core.save.SaveVersions
import com.ccz.saveio.SaveFileStore
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val dir = Files.createTempDirectory("ccz-save-io-selftest")
    try {
        runSmoke(dir)
        println("OK save-io atomic write/read self-test passed")
    } finally {
        dir.toFile().deleteRecursively() // clean up on success AND on a failed check
    }
}

private fun runSmoke(dir: Path) {
    val target = dir.resolve("slot.save")
    val envelope = sampleEnvelope()

    SaveFileStore.save(target, envelope)
    check(SaveFileStore.load(target) == envelope) {
        "save-io round-trip produced a different envelope"
    }

    // an overwrite must still leave no temp file behind
    SaveFileStore.save(target, envelope)
    val strays = Files.list(dir).use { stream ->
        stream.filter { it.fileName.toString().endsWith(".tmp") }.count()
    }
    check(strays == 0L) { "save-io left $strays stray temp file(s) behind" }
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
