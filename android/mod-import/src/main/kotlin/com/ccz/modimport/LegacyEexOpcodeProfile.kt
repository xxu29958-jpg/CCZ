package com.ccz.modimport

/**
 * The legacy EEX payload layouts are stable, but command words are not: the old decoded reference corpus uses
 * the native command ids from `docs/recon/eex-opcode-ledger.md`, while the current E-drive APK resource pack
 * carries a remapped opcode profile (same payload bytes, different cmd words). Keep that variance explicit so
 * importers do not accidentally treat a byte coincidence from one profile as a real command in another.
 */
enum class LegacyEexOpcodeProfile(
    val profileId: String,
    val labelCommands: Set<Int>,
    val dialogueCommands: Set<Int>,
    val commonInfoCommands: Set<Int>,
    val friendDispatchCommand: Int,
    val enemyDispatchCommand: Int,
) {
    LEGACY_DECODED(
        profileId = "legacy_decoded",
        labelCommands = setOf(0x02),
        dialogueCommands = setOf(0x14, 0x15),
        commonInfoCommands = (0x16..0x1a).toSet() + setOf(0x69),
        friendDispatchCommand = 0x46,
        enemyDispatchCommand = 0x47,
    ) {
        override val actorVisibleCommand: Int = 0x4c
        override val armyChangeCommand: Int = 0x3b
    },
    TRSSGSHZ_CURRENT_APK(
        profileId = "trssgshz_current_apk",
        labelCommands = setOf(0x0f),
        dialogueCommands = setOf(0x45),
        commonInfoCommands = setOf(0x4b, 0x4e, 0x51, 0x54, 0x57, 0x44),
        friendDispatchCommand = 0xdb,
        enemyDispatchCommand = 0xde,
    ) {
        override val actorVisibleCommand: Int = 0xed
        override val armyChangeCommand: Int = 0xba
    };

    abstract val actorVisibleCommand: Int
    abstract val armyChangeCommand: Int

    companion object {
        val detectionOrder: List<LegacyEexOpcodeProfile> = listOf(TRSSGSHZ_CURRENT_APK, LEGACY_DECODED)
    }
}
