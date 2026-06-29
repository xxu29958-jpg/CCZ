package com.ccz.modimport

import com.ccz.modimport.EexFixtures.eexBlob
import com.ccz.modimport.EexFixtures.strRec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives [LegacyScriptDecoder] on synthetic EEX scripts (via [EexFixtures]). Proves the AST faithfully groups
 * dialogue (speaker split, multi-speaker, narration), scene labels, and win/lose objective blocks WITHOUT
 * assigning any engine meaning (clauses stay raw legacy strings).
 */
class LegacyScriptDecoderTest {
    @Test
    fun groupsDialogueUnderItsSceneAndSplitsSpeaker() {
        val script = LegacyScriptDecoder.decode(
            eexBlob(
                strRec(0x02, "战前过渡"),                       // scene label
                strRec(0x14, "&程远志\n借粮重任，托付于你。"),     // speaker line under that scene
                strRec(0x14, "（三骑并辔上山）"),                 // narration (no '&')
            ),
        )
        assertEquals(2, script.dialogue.size)
        val first = script.dialogue[0]
        assertEquals("战前过渡", first.scene)
        assertEquals("程远志", first.speaker)
        assertEquals("借粮重任，托付于你。", first.text)
        val narration = script.dialogue[1]
        assertEquals("战前过渡", narration.scene, "narration still sits under the active scene")
        assertNull(narration.speaker)
        assertEquals("（三骑并辔上山）", narration.text)
    }

    @Test
    fun splitsAMultiSpeakerLineIntoSeparateLines() {
        // a single legacy Talk string can carry several speakers: '&A\n…&B\n…'
        val script = LegacyScriptDecoder.decode(eexBlob(strRec(0x14, "&小孩2\n叔父！\n&小孩1\n刘先生……")))
        assertEquals(2, script.dialogue.size)
        assertEquals("小孩2" to "叔父！\n", script.dialogue[0].speaker to script.dialogue[0].text)
        assertEquals("小孩1" to "刘先生……", script.dialogue[1].speaker to script.dialogue[1].text)
    }

    @Test
    fun parsesWinLoseObjectiveBlockIntoRawClauses() {
        val block = "胜利条件\n★·全灭敌军。\n\n失败条件\n☆·刘备死亡。\n☆·回合数超过15。"
        val script = LegacyScriptDecoder.decode(eexBlob(strRec(0x19, block)))
        assertEquals(1, script.objectives.size)
        val obj = script.objectives[0]
        assertEquals(listOf("全灭敌军。"), obj.win, "win clauses kept as raw legacy strings (markers stripped)")
        assertEquals(listOf("刘备死亡。", "回合数超过15。"), obj.lose)
    }

    @Test
    fun decodesCurrentApkOpcodeProfile() {
        val block = "胜利条件\n★·全灭敌军。\n\n失败条件\n☆·刘备死亡。"
        val script = LegacyScriptDecoder.decode(
            eexBlob(
                strRec(0x0f, "战前过渡"),
                strRec(0x45, "&程远志\n借粮重任，托付于你。"),
                strRec(0x54, block),
            ),
            LegacyEexOpcodeProfile.TRSSGSHZ_CURRENT_APK,
        )

        assertEquals("战前过渡", script.dialogue.single().scene)
        assertEquals("程远志", script.dialogue.single().speaker)
        assertEquals(listOf("全灭敌军。"), script.objectives.single().win)
    }

    @Test
    fun aPlainCommonInfoTextIsNotMistakenForAnObjectiveBlock() {
        // CommonInfo 0x18 is also a scene location — only blocks carrying BOTH 胜利/失败条件 are objectives
        val script = LegacyScriptDecoder.decode(eexBlob(strRec(0x18, "蓟城  刺史府")))
        assertTrue(script.objectives.isEmpty())
        assertTrue(script.dialogue.isEmpty())
    }

    @Test
    fun dialogueBeforeAnyLabelHasNullSceneAndTalk2IsHandled() {
        // a 0x15 (ActorTalk2) line before any 0x02 label: scene is null, and 0x15 is decoded like 0x14
        val script = LegacyScriptDecoder.decode(eexBlob(strRec(0x15, "&旁白\n开场，尚无场景标签。")))
        assertEquals(1, script.dialogue.size)
        assertNull(script.dialogue[0].scene, "dialogue before any label sits under no scene")
        assertEquals("旁白", script.dialogue[0].speaker)
        assertEquals("开场，尚无场景标签。", script.dialogue[0].text)
    }

    @Test
    fun failsClosedOnMalformedFraming() {
        val notEex = ByteArray(32) { 0x7a }
        assertFailsWith<EexFormatException> { LegacyScriptDecoder.decode(notEex) }
    }
}
