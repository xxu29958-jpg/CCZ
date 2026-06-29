package com.ccz.app.campaign

/** Native packs promoted from the legacy stage migration report and committed as app-playable runtimes. */
object PromotedStageRuntimes {
    val QuyangSiege: BundledBattleRuntime = promotedStage(2)

    val ShimenAttack: BundledBattleRuntime = promotedStage(3)

    val SishuiPassOne: BundledBattleRuntime = promotedStage(4)

    val SishuiPassTwo: BundledBattleRuntime = promotedStage(5)

    val HulaoPassBattle: BundledBattleRuntime = promotedStage(6)

    private val laterReadyStages: List<BundledBattleRuntime> =
        listOf(8, 9, 10, 11, 12, 13, 14, 16, 17, 18, 19, 21, 23, 24, 29, 30).map(::promotedStage)

    fun all(): List<BundledBattleRuntime> = listOf(
        QuyangSiege,
        ShimenAttack,
        SishuiPassOne,
        SishuiPassTwo,
        HulaoPassBattle,
    ) + laterReadyStages

    private fun promotedStage(stageNumber: Int): BundledBattleRuntime {
        val stageId = "legacy_stage_$stageNumber"
        return BundledBattleRuntime(
            stageId = stageId,
            battleScriptId = stageId,
            mapId = "${stageId}_map",
            battlePackResource = "/content/$stageId/campaign.json",
        )
    }
}
