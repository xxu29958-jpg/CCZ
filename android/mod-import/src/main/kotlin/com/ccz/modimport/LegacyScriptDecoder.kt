package com.ccz.modimport

/**
 * Second layer of the EEX legacy-script generator (ADR 0009): groups [EexCodec]'s raw `(offset, cmd, text)`
 * stream into a **legacy script AST** — dialogue lines (speaker split out), scene labels, and win/lose
 * objective blocks. It interprets the legacy FILE FORMAT (which command id means what, the `&speaker` marker,
 * the 胜利条件/失败条件 block layout) but assigns NO engine meaning: a clause stays the raw legacy string
 * `"全灭敌军。"`, never an engine condition. Mapping the AST to engine ops/objectives is the next layer
 * (`LegacySemanticMapper`); this layer only answers "what does the legacy script SAY".
 *
 * Pure and faithful: it never fabricates content (it reports exactly what [EexCodec] decoded) and never
 * guesses semantics (unmappable legacy concepts are simply carried as text for the mapper to fail-close on).
 */
object LegacyScriptDecoder {
    // Legacy command ids (from libMyGame.so getScriptByCmd; see docs/recon). These name the FILE FORMAT, not
    // engine concepts: 0x02 a labelled sub-block (scene title), 0x14/0x15 actor dialogue, 0x16..0x1a the
    // CommonInfo text variants (title / location / win-lose condition display).
    private const val CMD_LABEL = 0x02
    private const val CMD_TALK = 0x14
    private const val CMD_TALK2 = 0x15
    private const val CMD_COMMON_LO = 0x16
    private const val CMD_COMMON_HI = 0x1a
    private const val SPEAKER_MARK = '&' // a dialogue string prefixes each speaker's line with '&Name\n...'
    private const val WIN_HEADER = "胜利条件"
    private const val LOSE_HEADER = "失败条件"

    /** The decoded legacy script: every dialogue line and every win/lose objective block, in file order. */
    data class LegacyScript(
        val dialogue: List<LegacyLine>,
        val objectives: List<LegacyObjectives>,
    )

    /** One spoken line: the [scene] it sits under (nearest preceding label), the [speaker] (null = narration),
     *  the raw [text] (legacy line breaks preserved), and its [offset] for stable ordering / scene slicing. */
    data class LegacyLine(val scene: String?, val speaker: String?, val text: String, val offset: Int)

    /** One objective block's RAW clauses, split only by the legacy 胜利/失败条件 headers — each clause is the
     *  legacy string (e.g. `"刘备死亡。"`), NOT yet an engine condition (that is the mapper's call). */
    data class LegacyObjectives(val win: List<String>, val lose: List<String>, val offset: Int)

    /** Decode an EEX script [blob] into its legacy AST. Throws [EexFormatException] on malformed framing. */
    fun decode(blob: ByteArray): LegacyScript {
        val dialogue = ArrayList<LegacyLine>()
        val objectives = ArrayList<LegacyObjectives>()
        var scene: String? = null
        for (s in EexCodec.strings(blob)) {
            when {
                s.cmd == CMD_LABEL -> scene = s.text
                s.cmd == CMD_TALK || s.cmd == CMD_TALK2 ->
                    for ((speaker, line) in splitSpeakers(s.text)) {
                        dialogue.add(LegacyLine(scene, speaker, line, s.offset))
                    }
                s.cmd in CMD_COMMON_LO..CMD_COMMON_HI && isObjectiveBlock(s.text) ->
                    objectives.add(parseObjectives(s.text, s.offset))
            }
        }
        return LegacyScript(dialogue, objectives)
    }

    /** True when a CommonInfo block is a win/lose objective display (carries both headers). */
    private fun isObjectiveBlock(text: String): Boolean = WIN_HEADER in text && LOSE_HEADER in text

    /**
     * Split one dialogue string into its `&speaker\ntext` segments. A single legacy [Talk] string can carry
     * several speakers (`&A\n…&B\n…`), so this returns a list; a string with no `&` is one narration line
     * (speaker null). The text keeps its legacy internal line breaks.
     */
    private fun splitSpeakers(text: String): List<Pair<String?, String>> {
        if (!text.startsWith(SPEAKER_MARK)) return listOf(null to text)
        return text.split(SPEAKER_MARK).filter { it.isNotEmpty() }.map { seg ->
            val nl = seg.indexOf('\n')
            if (nl >= 0) seg.substring(0, nl).trim() to seg.substring(nl + 1) else seg.trim() to ""
        }
    }

    /**
     * Split a `胜利条件\n…\n失败条件\n…` block into its raw win-clause and lose-clause lists, stripping only the
     * legacy bullet markers (★☆· and whitespace). Each clause stays a legacy string; their engine meaning is
     * decided downstream. Clauses before the first header are ignored (the headers anchor the sections).
     */
    private fun parseObjectives(text: String, offset: Int): LegacyObjectives {
        val win = ArrayList<String>()
        val lose = ArrayList<String>()
        var bucket: ArrayList<String>? = null
        for (raw in text.split("\n")) {
            val line = raw.trim().trim('★', '☆', '·', ' ').trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith(WIN_HEADER) -> bucket = win
                line.startsWith(LOSE_HEADER) -> bucket = lose
                else -> bucket?.add(line)
            }
        }
        return LegacyObjectives(win, lose, offset)
    }
}
