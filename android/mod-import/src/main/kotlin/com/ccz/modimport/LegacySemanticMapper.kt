package com.ccz.modimport

/**
 * Third layer of the EEX legacy-script generator (ADR 0009): maps the legacy AST ([LegacyScriptDecoder]) into
 * the engine's content-pack semantics. This is where legacy concepts become CCZ ops/conditions — and where
 * fail-closed lives: a legacy clause whose engine meaning is not CONFIDENTLY known is recorded as
 * [UnsupportedClause] (raw clause + reason), NEVER force-mapped onto a near-miss. A wrong objective is worse
 * than a flagged-missing one.
 *
 * Pure: it takes the AST plus a hero-name→id resolver (the generator supplies it from dic_hero) and returns
 * engine-shaped [PackCondition]s; it reads no files and decides no battle design.
 */
object LegacySemanticMapper {
    private const val SIDE_WIN = "win"
    private const val SIDE_LOSE = "lose"
    private val DEATH = Regex("^(.+?)死亡$")
    private val TURN_LIMIT = Regex("回合数超过\\d+")

    /**
     * Phrases that unambiguously mean "wipe out ALL enemies" — the DEFAULT clear condition for ordinary stages
     * (special stages use reach/protect/survive instead). All require the all-enemies sense (敌军/全部/全军),
     * so a specific-unit kill like "歼灭程远志" is NOT caught here (it falls to the death/defeat clause). This
     * is the one place broad synonym recognition is right rather than a guess: annihilation is the norm.
     */
    private val ANNIHILATE_MARKERS = listOf(
        "全灭敌", "全歼", "歼灭敌", "全军覆没", "全部敌军", "全部敌人", "击破全部敌", "消灭全部敌", "击败全部敌", "击杀全部敌",
    )

    /** Mapped win/lose conditions plus the clauses that could not be confidently mapped (fail-closed). */
    data class MappedObjectives(
        val win: List<PackCondition>,
        val lose: List<PackCondition>,
        val unsupported: List<UnsupportedClause>,
    )

    /** A legacy objective clause with no confident CCZ mapping: kept as evidence, never silently dropped. */
    data class UnsupportedClause(val clause: String, val side: String, val reason: String)

    /**
     * Map a decoded objective block into CCZ win/lose conditions, resolving unit names via [nameToId] (a
     * dic_hero name→`hero_<id>` lookup the generator supplies). A clause maps to a condition by its OWN
     * meaning regardless of side; the legacy block's section decides which list it lands in.
     */
    fun mapObjectives(
        objectives: LegacyScriptDecoder.LegacyObjectives,
        nameToId: (String) -> String?,
    ): MappedObjectives {
        val win = ArrayList<PackCondition>()
        val lose = ArrayList<PackCondition>()
        val unsupported = ArrayList<UnsupportedClause>()
        mapSide(objectives.win, SIDE_WIN, nameToId, win, unsupported)
        mapSide(objectives.lose, SIDE_LOSE, nameToId, lose, unsupported)
        return MappedObjectives(win, lose, unsupported)
    }

    /**
     * Map decoded dialogue lines into cutscene dialogue ops, one op per line. Faithful and lossless: the
     * speaker and text are carried verbatim (legacy line breaks preserved). Scene framing — `scene_transition`,
     * portraits, bgm, fades — is a presentation/generation choice, NOT inferred here, so this layer stays a
     * pure content mapping (the generator adds framing if it wants).
     */
    fun mapDialogue(lines: List<LegacyScriptDecoder.LegacyLine>): List<PackScenarioOp> =
        lines.map { PackScenarioOp.dialogue(it.speaker, it.text) }

    private fun mapSide(
        clauses: List<String>,
        side: String,
        nameToId: (String) -> String?,
        out: MutableList<PackCondition>,
        unsupported: MutableList<UnsupportedClause>,
    ) {
        for (raw in clauses) {
            val (cond, reason) = mapClause(raw, side, nameToId)
            if (cond != null) out.add(cond) else unsupported.add(UnsupportedClause(raw, side, reason ?: "no mapping"))
        }
    }

    /**
     * One legacy clause -> a CCZ condition, or (null, reason) if it cannot be confidently mapped. The [side]
     * the clause sits in decides the death mapping (see [deathClause]). Fail-closed: only clauses whose CCZ
     * semantics MATCH are emitted; near-misses are reported unsupported rather than forced onto a condition
     * that means something different — a turn DEADLINE ("回合数超过N", lose if turns exceed N) is NOT CCZ's
     * SurviveTurns (survive-to-N = win); an area objective needs a map-binding the EEX script does not carry
     * (the map is loaded by native code — see docs/recon). Annihilation, by contrast, IS the ordinary clear
     * condition, so its synonyms ([ANNIHILATE_MARKERS]) are recognized broadly.
     */
    private fun mapClause(rawClause: String, side: String, nameToId: (String) -> String?): Pair<PackCondition?, String?> {
        val clause = rawClause.trim().trimEnd('。', '.', '！', '!', ' ')
        if (ANNIHILATE_MARKERS.any { it in clause }) return PackCondition(PackCondition.ANNIHILATE_ENEMIES) to null
        DEATH.find(clause)?.let { return deathClause(it.groupValues[1], side, nameToId) }
        if (TURN_LIMIT.containsMatchIn(clause)) {
            return null to "turn-deadline ('$clause'): CCZ has no turn-limit-lose (SurviveTurns is survive-to-win, the opposite)"
        }
        if ("到达" in clause || "村庄" in clause || "占领" in clause) {
            return null to "area/tile objective ('$clause'): needs script map-binding (deferred)"
        }
        return null to "no confident CCZ mapping for '$clause'"
    }

    /**
     * A "<name>死亡" clause -> a unit condition, keyed by which side it appears in: in the LOSE section it means
     * "you lose if this unit dies" -> [PackCondition.PROTECT_ALIVE]; in the WIN section it means "kill this unit
     * to win" -> [PackCondition.DEFEAT_UNIT] (e.g. a boss). Unsupported (never guessed) when the name has no
     * global dic_hero id — a battle-local actor id can't be resolved here.
     */
    private fun deathClause(name: String, side: String, nameToId: (String) -> String?): Pair<PackCondition?, String?> {
        val id = nameToId(name)
            ?: return null to "unit '$name' has no global dic_hero id (likely a battle-local roster id)"
        val type = if (side == SIDE_WIN) PackCondition.DEFEAT_UNIT else PackCondition.PROTECT_ALIVE
        return PackCondition(type, unit = id) to null
    }
}
