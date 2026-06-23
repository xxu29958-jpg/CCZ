package com.ccz.app.battle

import com.ccz.contentpack.NativeContent
import com.ccz.contentpack.json.ContentJsonLoader
import com.ccz.core.event.RScript

/**
 * The app's built-in demo campaign, **authored as a JSON content pack** (a bundled resource) and decoded
 * via native-content's [ContentJsonLoader] — content now lives as data, not Kotlin. This is the real
 * content pipeline end to end: JSON → strict fail-closed decode → (validate) →
 * [com.ccz.contentpack.assembly.CampaignAssembler] → battle. [BATTLE_SCRIPT_ID] / [MAP_ID] name which
 * S-script and map to assemble (the manifest's `entry` mirrors the script id). The pack is NOT a second
 * source of combat truth — the app only decodes + assembles it and hands the result to
 * [com.ccz.core.battle.Gameplay].
 *
 * Decoding is fail-closed (unknown keys / enums / op discriminators throw); cross-reference validity is a
 * separate layer, gated at build/test time by CampaignContentTest (which runs `ContentValidator` over this
 * pack), so the bundled resource is known-valid before it ever reaches the assembler at runtime.
 */
object CampaignContent {
    const val BATTLE_SCRIPT_ID = "demo_battle"
    const val INTRO_SCRIPT_ID = "demo_intro"
    const val MAP_ID = "battle_map"

    private const val RESOURCE = "/content/ccz_demo/campaign.json"

    fun pack(): NativeContent = ContentJsonLoader.load(readResource())

    fun introScript(): RScript =
        pack().events.rScripts.singleOrNull { it.id == INTRO_SCRIPT_ID }
            ?: error("bundled campaign intro script missing: $INTRO_SCRIPT_ID")

    private fun readResource(): String =
        (javaClass.getResourceAsStream(RESOURCE) ?: error("bundled campaign content missing: $RESOURCE"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
}
