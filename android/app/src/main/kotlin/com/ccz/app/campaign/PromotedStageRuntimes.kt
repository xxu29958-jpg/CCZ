package com.ccz.app.campaign

/** Native packs promoted from the legacy stage migration report and committed as app-playable runtimes. */
object PromotedStageRuntimes {
    val QuyangSiege: BundledBattleRuntime = BundledBattleRuntime(
        stageId = "legacy_stage_2",
        battleScriptId = "legacy_stage_2",
        mapId = "legacy_stage_2_map",
        battlePackResource = "/content/legacy_stage_2/campaign.json",
    )

    val ShimenAttack: BundledBattleRuntime = BundledBattleRuntime(
        stageId = "legacy_stage_3",
        battleScriptId = "legacy_stage_3",
        mapId = "legacy_stage_3_map",
        battlePackResource = "/content/legacy_stage_3/campaign.json",
    )

    val SishuiPassOne: BundledBattleRuntime = BundledBattleRuntime(
        stageId = "legacy_stage_4",
        battleScriptId = "legacy_stage_4",
        mapId = "legacy_stage_4_map",
        battlePackResource = "/content/legacy_stage_4/campaign.json",
    )

    fun all(): List<CampaignStageRuntime> = listOf(QuyangSiege, ShimenAttack, SishuiPassOne)
}
