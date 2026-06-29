package com.ccz.contentpack

class CommerceResolutionException(message: String) : RuntimeException(message)

data class PurchaseResolution(
    val product: ProductDef,
    val reward: RewardDef,
    val inventory: Map<String, Int>,
    val entitlements: Set<EntitlementKind>,
    val stageAccess: StageAccess?,
)

data class StageAccess(
    val stage: StageDef,
    val unlocked: Boolean,
    val missingItems: List<String>,
    val allStagesEntitled: Boolean,
)

/**
 * Pure commerce resolver for native content. It does not talk to a payment SDK; callers pass a product id only
 * after a local/test purchase has succeeded, and this layer deterministically grants content-defined rewards.
 */
object CommerceResolver {
    fun resolvePurchase(
        commerce: CommerceTables,
        productId: String,
        stageId: String? = null,
    ): PurchaseResolution {
        val product = commerce.products.associateBy { it.id }[productId]
            ?: throw CommerceResolutionException("unknown product: $productId")
        val reward = commerce.rewards.associateBy { it.id }[product.rewardId]
            ?: throw CommerceResolutionException("product ${product.id} references unknown reward: ${product.rewardId}")
        if (reward.itemGrants.isEmpty() && reward.entitlements.isEmpty()) {
            throw CommerceResolutionException("reward ${reward.id} has neither item grants nor entitlements")
        }
        val inventory = reward.itemGrants.fold(linkedMapOf<String, Int>()) { acc, grant ->
            acc[grant.itemId] = acc.getOrDefault(grant.itemId, 0) + grant.quantity
            acc
        }
        val entitlements = reward.entitlements.mapTo(linkedSetOf()) { it.kind }
        return PurchaseResolution(
            product = product,
            reward = reward,
            inventory = inventory,
            entitlements = entitlements,
            stageAccess = stageId?.let { accessFor(commerce, it, inventory, entitlements) },
        )
    }

    fun accessFor(
        commerce: CommerceTables,
        stageId: String,
        inventory: Map<String, Int>,
        entitlements: Set<EntitlementKind>,
    ): StageAccess {
        val stage = commerce.stages.associateBy { it.id }[stageId]
            ?: throw CommerceResolutionException("unknown stage: $stageId")
        val allStages = EntitlementKind.ALL_STAGES in entitlements
        val missing = if (allStages) emptyList() else stage.requiredItems.filter { inventory.getOrDefault(it, 0) <= 0 }
        return StageAccess(
            stage = stage,
            unlocked = allStages || missing.isEmpty(),
            missingItems = missing,
            allStagesEntitled = allStages,
        )
    }
}
