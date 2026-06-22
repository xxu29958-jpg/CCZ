package com.ccz.app.battle

import com.ccz.core.battle.RejectReason

/**
 * Maps a [RejectReason] — an internal command-contract token — to a short, user-facing log phrase.
 * Pure presentation, zero combat authority: it only renames a rejection the authority already
 * decided, never changes whether a command is legal. The exhaustive `when` is the fail-closed seam:
 * a new [RejectReason] added in game-core fails to compile here until it is given a phrase, so a raw
 * enum token (e.g. SKILL_NOT_IN_LOADOUT) can never leak to the player. This is stricter than a
 * lookup-with-fallback (xiaopiaojia errors.py ERROR_MESSAGES) — there is no fallback to miss.
 */
internal fun phraseOf(reason: RejectReason): String = when (reason) {
    RejectReason.NOT_ACTIVE_FACTION -> "Not this side's turn"
    RejectReason.UNIT_NOT_FOUND -> "No such unit"
    RejectReason.UNIT_DEAD -> "That unit is down"
    RejectReason.UNKNOWN_CLASS -> "Unknown unit class"
    RejectReason.DESTINATION_OUT_OF_BOUNDS -> "Off the battlefield"
    RejectReason.DESTINATION_IMPASSABLE -> "Can't move there"
    RejectReason.DESTINATION_OCCUPIED -> "Tile is occupied"
    RejectReason.OUT_OF_MOVE_RANGE -> "Out of move range"
    RejectReason.UNKNOWN_SKILL -> "Unknown skill"
    RejectReason.SKILL_NOT_IN_LOADOUT -> "Skill not equipped"
    RejectReason.TARGET_NOT_FOUND -> "No target there"
    RejectReason.TARGET_DEAD -> "Target already down"
    RejectReason.SELF_TARGET -> "Can't attack itself"
    RejectReason.TARGET_FRIENDLY -> "That's an ally"
    RejectReason.OUT_OF_ATTACK_RANGE -> "Out of attack range"
    RejectReason.WRONG_END_TURN_FACTION -> "Not your turn to end"
    RejectReason.UNIT_ALREADY_MOVED -> "Already moved this turn"
    RejectReason.UNIT_ALREADY_ACTED -> "Already acted this turn"
}
