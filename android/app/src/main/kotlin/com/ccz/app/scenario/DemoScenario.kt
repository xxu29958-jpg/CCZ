package com.ccz.app.scenario

import com.ccz.app.battle.CampaignContent
import com.ccz.core.event.RScript

/**
 * Thin accessor for the demo intro cutscene, loaded from the bundled native-content JSON pack. It remains
 * only initial input handed to the authoritative [com.ccz.core.event.ScenarioRunner]; scenario vars,
 * branches, and choices are still interpreted exclusively by game-core.
 */
object DemoScenario {
    fun script(): RScript = CampaignContent.introScript()
}
