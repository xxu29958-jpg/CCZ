package com.ccz.app

import com.ccz.core.battle.BattleRules
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Local unit smoke test for the app shell: proves the :app module compiles against
 * :game-core and can read an already-decided value (the rules version) without owning
 * any combat authority. Runs on the JVM (no device), under the grayDebug unit-test task.
 */
class ShellSmokeTest {
    @Test
    fun gameCoreRulesVersionIsReadable() {
        assertTrue("rules version must be a positive constant", BattleRules.RULES_VERSION >= 1)
    }
}
