package com.ccz.app.campaign

/** Native packs promoted from the legacy stage migration report and committed as app-playable runtimes. */
object PromotedStageRuntimes {
    val QuyangSiege: BundledBattleRuntime = BundledBattleRuntime(
        stageId = "legacy_stage_2",
        battleScriptId = "legacy_stage_2",
        mapId = "legacy_stage_2_map",
        battlePackResource = "/content/legacy_stage_2/campaign.json",
    )

    fun all(): List<CampaignStageRuntime> = listOf(QuyangSiege)
}
