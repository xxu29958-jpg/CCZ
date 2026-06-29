package com.ccz.app.campaign

/** Native packs promoted from the legacy stage migration report and committed as app-playable runtimes. */
object PromotedStageRuntimes {
    val QuyangSiege: BundledBattleRuntime = promotedStage(2)

    val ShimenAttack: BundledBattleRuntime = promotedStage(3)

    val SishuiPassOne: BundledBattleRuntime = promotedStage(4)

    val SishuiPassTwo: BundledBattleRuntime = promotedStage(5)

    val HulaoPassBattle: BundledBattleRuntime = promotedStage(6)

    private val laterReadyStages: List<BundledBattleRuntime> =
        listOf(
            8, 9, 10, 11, 12, 13, 14,
            16, 17, 18, 19, 21, 23, 24, 29, 30,
            31, 34, 35, 36, 37, 38, 39, 40, 41, 42,
            44, 45, 46, 47, 48,
            50, 51, 52, 53, 54, 55, 56, 57, 58, 59,
            61, 62,
            64, 66, 69, 70, 72, 73, 76, 77, 78, 83, 86, 89,
            91, 93, 94, 95, 96, 97, 99,
            101, 102, 103, 104, 105, 107, 109,
            111, 112, 114, 115, 116, 119, 120,
            122, 127, 128, 130, 131, 134, 135, 136, 137,
            141, 143, 144, 145, 146, 147, 148, 152, 156, 157, 158,
            165, 172, 174, 175, 176, 178, 179, 180, 182, 186, 189, 194, 196, 197,
            201, 202, 204, 205, 206, 207,
            209, 210, 214, 215, 216, 217, 218, 219, 220, 221, 222, 224, 225, 226,
            227, 228, 229, 230, 231, 233,
            234, 235, 236, 237, 239, 242, 243, 244, 245, 249, 250, 251, 252, 257,
            258, 259, 263, 264, 265, 266,
        ).map(::promotedStage)

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
