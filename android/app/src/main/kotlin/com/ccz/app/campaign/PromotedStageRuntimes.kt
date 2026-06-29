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

    val SishuiPassTwo: BundledBattleRuntime = BundledBattleRuntime(
        stageId = "legacy_stage_5",
        battleScriptId = "legacy_stage_5",
        mapId = "legacy_stage_5_map",
        battlePackResource = "/content/legacy_stage_5/campaign.json",
    )

    val HulaoPassBattle: BundledBattleRuntime = BundledBattleRuntime(
        stageId = "legacy_stage_6",
        battleScriptId = "legacy_stage_6",
        mapId = "legacy_stage_6_map",
        battlePackResource = "/content/legacy_stage_6/campaign.json",
    )

    fun all(): List<CampaignStageRuntime> = listOf(
        QuyangSiege,
        ShimenAttack,
        SishuiPassOne,
        SishuiPassTwo,
        HulaoPassBattle,
    )
}
