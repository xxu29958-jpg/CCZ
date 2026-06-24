package com.ccz.app.scenario

import com.ccz.contentpack.ContentValidator
import com.ccz.core.event.ScenarioOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 大兴山 intro cutscene that now precedes the real battle — an authored R-script matching the
 * 刘备-vs-黄巾 fight (replacing the mismatched 曹操 demo intro). Pins that its bundled pack decodes and
 * validates clean, and that the cutscene is the branching 桃园三兄弟 scene the battle expects.
 */
class RealScenarioTest {
    @Test
    fun theIntroPackValidatesClean() {
        assertEquals(
            "the bundled 大兴山 intro pack must validate before it reaches the ScenarioRunner",
            emptyList<Any>(),
            ContentValidator.validate(RealScenario.pack()),
        )
    }

    @Test
    fun theRealIntroIsTheDaxingshanCutsceneWithABranchingChoice() {
        val script = RealScenario.script()
        assertEquals("daxingshan_intro", script.id)
        assertTrue("the authored 大兴山 intro offers a march-plan choice", script.ops.any { it is ScenarioOp.Choice })
        assertTrue("it is a multi-op authored cutscene", script.ops.size > 5)
    }
}
