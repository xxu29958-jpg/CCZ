package com.ccz.modimport

/**
 * Composes the EEX legacy-script generator's pure layers ([EexCodec] → [LegacyScriptDecoder] →
 * [LegacySemanticMapper]) into "import the win/lose objectives a real battle script declares", scoped to the
 * units actually deployed in this (possibly curated) battle. Part of the generator framework (ADR 0009): it
 * makes a battle's objectives come FROM the real script instead of being hand-coded.
 *
 * Two battle-design realities are handled honestly:
 * - A stage script can carry several objective phases (e.g. 大兴山: reach-village, then annihilate). The
 *   ordinary deploy-and-fight phase is the ANNIHILATION one, so that block is chosen; non-annihilation phases
 *   (area/turn objectives) are left to the mapper's `unsupported` rather than guessed.
 * - The script's objectives reference the stage's FULL roster, but a generated battle may deploy only a curated
 *   subset. A unit-scoped condition naming a unit not in [rosterIds] is dropped to [ImportedObjectives.outOfRoster]
 *   (recorded, never silently kept) — a curated battle cannot protect/defeat a unit it never deploys.
 *
 * Pure: it reads no files (the caller supplies the decrypted script bytes + a dic_hero name→id resolver).
 */
object LegacyObjectiveImporter {
    /** The win/lose conditions a battle script declares, scoped to the deployed roster, plus what was set aside. */
    data class ImportedObjectives(
        val win: List<PackCondition>,
        val lose: List<PackCondition>,
        val unsupported: List<LegacySemanticMapper.UnsupportedClause>,
        val outOfRoster: List<PackCondition>,
    )

    /**
     * Import the deploy-and-fight objectives from [scriptBytes], keeping only conditions whose unit (if any) is
     * in [rosterIds]. [nameToId] resolves a legacy unit name to its `hero_<id>`. Throws [EexFormatException] on
     * malformed framing (fail-closed). Returns empty objectives when the script declares none.
     */
    fun importObjectives(
        scriptBytes: ByteArray,
        rosterIds: Set<String>,
        nameToId: (String) -> String?,
    ): ImportedObjectives {
        val mapped = LegacyScriptDecoder.decode(scriptBytes).objectives
            .map { LegacySemanticMapper.mapObjectives(it, nameToId) }
        // Keep EVERY block's unsupported clauses, not just the chosen block's — a non-selected phase's
        // objectives (e.g. 大兴山 phase-1 reach-village) must not vanish silently (fail-closed contract).
        val unsupported = mapped.flatMap { it.unsupported }
        // Choose the deploy-and-fight phase (its win is an annihilation); if none annihilates, fall back to the
        // last block as a best effort — whatever it leaves unmapped still surfaces in [unsupported] above.
        val chosen = mapped.firstOrNull { m -> m.win.any { it.type == PackCondition.ANNIHILATE_ENEMIES } }
            ?: mapped.lastOrNull()
            ?: return ImportedObjectives(emptyList(), emptyList(), unsupported, emptyList())
        val (win, winDropped) = scopeToRoster(chosen.win, rosterIds)
        val (lose, loseDropped) = scopeToRoster(chosen.lose, rosterIds)
        return ImportedObjectives(win, lose, unsupported, winDropped + loseDropped)
    }

    /** Partition [conditions] into (kept, dropped): a unit-scoped condition is kept only when its unit is in
     *  [rosterIds]; roster-independent conditions (e.g. annihilate_enemies) are always kept. */
    private fun scopeToRoster(
        conditions: List<PackCondition>,
        rosterIds: Set<String>,
    ): Pair<List<PackCondition>, List<PackCondition>> {
        val kept = ArrayList<PackCondition>()
        val dropped = ArrayList<PackCondition>()
        for (c in conditions) {
            if (c.unit == null || c.unit in rosterIds) kept.add(c) else dropped.add(c)
        }
        return kept to dropped
    }
}
