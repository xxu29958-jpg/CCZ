package com.ccz.app.scenario

import com.ccz.core.event.ChoiceOption
import com.ccz.core.event.DialogueLine
import com.ccz.core.event.RScript
import com.ccz.core.event.ScenarioOp

/**
 * Hardcoded presentation seed for the intro cutscene, mirroring `battle.DemoBattle`: a temporary
 * stand-in until the content-fed driver layer supplies real R-scripts. This is NOT a second source
 * of truth — it is only initial input handed to the authoritative [com.ccz.core.event.ScenarioRunner].
 * It deliberately exercises dialogue (with and without a speaker), a portrait, scene/bgm/fade ops, and
 * a choice whose two options set a var and `goto` different labels that re-converge — so the rendered
 * playback differs by choice, proving the app renders the runner's branch resolution rather than its own.
 */
object DemoScenario {
    fun script(): RScript = RScript(
        id = "demo_intro",
        ops = listOf(
            ScenarioOp.FadeIn,
            ScenarioOp.SceneTransition(target = "许昌·军帐"),
            ScenarioOp.PlayBgm(id = "war_council"),
            ScenarioOp.Portrait(unit = "cao_cao", emotion = "calm"),
            ScenarioOp.Dialogue(DialogueLine(speaker = "曹操", text = "诸位，前方便是敌军布防之地。")),
            ScenarioOp.Dialogue(DialogueLine(text = "（帐内众将肃然）")),
            ScenarioOp.Choice(
                prompt = "如何进军？",
                options = listOf(
                    ChoiceOption(text = "正面强攻", goto = "assault", setVars = mapOf("plan" to 1)),
                    ChoiceOption(text = "分兵包抄", goto = "flank", setVars = mapOf("plan" to 2)),
                ),
            ),
            ScenarioOp.Label("assault"),
            ScenarioOp.Dialogue(DialogueLine(speaker = "曹操", text = "全军压上，一鼓作气！")),
            // plan==1 (强攻) skips the flank line; plan==2 reached "flank" directly via the option goto.
            ScenarioOp.Branch(variable = "plan", equals = 1, target = "ready"),
            ScenarioOp.Label("flank"),
            ScenarioOp.Dialogue(DialogueLine(speaker = "曹操", text = "遣一军绕后，断其归路。")),
            ScenarioOp.Label("ready"),
            ScenarioOp.Dialogue(DialogueLine(text = "（战鼓擂动）")),
            ScenarioOp.FadeOut,
        ),
    )
}
