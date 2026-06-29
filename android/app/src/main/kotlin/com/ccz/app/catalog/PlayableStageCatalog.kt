package com.ccz.app.catalog

import com.ccz.app.campaign.CampaignRuntime
import com.ccz.app.campaign.CampaignStageRuntime
import com.ccz.app.campaign.PromotedStageRuntimes
import com.ccz.contentpack.CommerceResolver
import com.ccz.contentpack.EntitlementKind
import com.ccz.contentpack.StageAccess
import com.ccz.contentpack.StageDef

data class PlayableStageSummary(
    val stage: StageDef,
    val runtime: CampaignStageRuntime,
)

data class PlayableStageAccess(
    val stageAccess: StageAccess,
    val inventory: Map<String, Int>,
    val entitlements: Set<EntitlementKind>,
    val runtime: CampaignStageRuntime?,
) {
    val canStart: Boolean get() = stageAccess.unlocked && runtime != null

    fun launchRuntimeOrNull(): CampaignStageRuntime? = runtime?.takeIf { canStart }
}

/**
 * App-side registry for native stages that are actually playable today. The legacy commerce catalog can unlock
 * 397 rows, but only entries registered here may launch a battle runtime; this prevents catalog migration evidence
 * from being mistaken for app-selectable content.
 */
object PlayableStageCatalog {
    const val FULL_UNLOCK_PRODUCT_ID = "trssgshz01"

    private val runtimesByStageId: Map<String, CampaignStageRuntime> =
        (listOf(CampaignRuntime) + PromotedStageRuntimes.all()).associateBy { it.stageId }

    private val stagesById: Map<String, StageDef> by lazy {
        LegacyCatalogContent.pack().commerce.stages.associateBy { it.id }
    }

    private val productNamesById: Map<String, String> by lazy {
        LegacyCatalogContent.pack().commerce.products.associate { it.id to it.name }
    }

    private val itemNamesById: Map<String, String> by lazy {
        LegacyCatalogContent.pack().tables.items.associate { it.id to it.name }
    }

    fun catalogStageCount(): Int = stagesById.size

    fun productName(productId: String): String = productNamesById[productId] ?: productId

    fun itemName(itemId: String): String = itemNamesById[itemId] ?: itemId

    fun playableStages(): List<PlayableStageSummary> =
        runtimesByStageId.values.map { runtime ->
            val stage = stagesById[runtime.stageId]
                ?: error("playable runtime not present in legacy catalog: ${runtime.stageId}")
            PlayableStageSummary(stage = stage, runtime = runtime)
        }

    fun accessFor(
        stageId: String,
        inventory: Map<String, Int> = emptyMap(),
        entitlements: Set<EntitlementKind> = emptySet(),
    ): PlayableStageAccess {
        val access = CommerceResolver.accessFor(
            commerce = LegacyCatalogContent.pack().commerce,
            stageId = stageId,
            inventory = inventory,
            entitlements = entitlements,
        )
        return PlayableStageAccess(
            stageAccess = access,
            inventory = inventory,
            entitlements = entitlements,
            runtime = runtimesByStageId[stageId],
        )
    }

    fun resolvePurchase(productId: String, stageId: String): PlayableStageAccess {
        val receipt = LegacyCatalogContent.resolvePurchase(productId = productId, stageId = stageId)
        return PlayableStageAccess(
            stageAccess = requireNotNull(receipt.stageAccess),
            inventory = receipt.inventory,
            entitlements = receipt.entitlements,
            runtime = runtimesByStageId[stageId],
        )
    }
}
