package com.ccz.app.battle

import com.ccz.core.battle.RejectReason
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [phraseOf] as a total, leak-free projection: the exhaustive `when` already forces a phrase
 * for every [RejectReason] at compile time, and this checks each phrase is user-facing — non-blank
 * and never exposing the raw internal token (the enum name or its underscores) to the player.
 */
class RejectPhraseTest {
    @Test
    fun everyRejectReasonMapsToAUserFacingPhraseThatLeaksNoInternalToken() {
        for (reason in RejectReason.entries) {
            val phrase = phraseOf(reason)
            assertTrue("$reason must map to a non-blank phrase", phrase.isNotBlank())
            assertFalse("$reason phrase must not leak the enum name", phrase.contains(reason.name))
            assertFalse("$reason phrase must not contain a raw underscore token", phrase.contains("_"))
        }
    }
}
