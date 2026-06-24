package com.ccz.app.scenario

import com.ccz.contentpack.NativeContent
import com.ccz.contentpack.json.ContentJsonLoader
import com.ccz.core.event.RScript

/**
 * Intro cutscene for the real [com.ccz.app.battle.RealBattle] (大兴山之战), authored as its own bundled
 * content pack so it matches the battle (桃园三兄弟 vs 黄巾) — replacing the demo's 曹操 war-council, which
 * was a placeholder mismatched with the 刘备-vs-黄巾 battle. The R-script is narrative content (authored,
 * not mined from the legacy tables, which carry no scene scripts for this engine). It is only initial input
 * to the authoritative [com.ccz.core.event.ScenarioRunner]; vars/branches/choices stay game-core's.
 */
object RealScenario {
    const val INTRO_SCRIPT_ID = "daxingshan_intro"

    private const val RESOURCE = "/content/ccz_daxingshan/intro.json"

    fun script(): RScript =
        pack().events.rScripts.singleOrNull { it.id == INTRO_SCRIPT_ID }
            ?: error("bundled 大兴山 intro script missing: $INTRO_SCRIPT_ID")

    fun pack(): NativeContent =
        ContentJsonLoader.load(
            (javaClass.getResourceAsStream(RESOURCE) ?: error("bundled intro content missing: $RESOURCE"))
                .bufferedReader(Charsets.UTF_8).use { it.readText() },
        )
}
