package com.ccz.contentpack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CommerceResolverTest {
    @Test
    fun purchaseGrantsItemsAndUnlocksStageByRequiredItem() {
        val commerce = commerce(
            rewards = listOf(RewardDef("reward_yuanbao", itemGrants = listOf(ItemGrantDef("yuanbao", 188)))),
            products = listOf(product("trssgshz03", "reward_yuanbao")),
            stages = listOf(StageDef("stage_1", "Daxingshan", requiredItems = listOf("yuanbao"))),
        )

        val receipt = CommerceResolver.resolvePurchase(commerce, "trssgshz03", "stage_1")

        assertEquals(mapOf("yuanbao" to 188), receipt.inventory)
        val access = receipt.stageAccess!!
        assertTrue(access.unlocked)
        assertEquals(emptyList(), access.missingItems)
    }

    @Test
    fun allStagesEntitlementUnlocksStageWithoutRequiredItems() {
        val commerce = commerce(
            rewards = listOf(RewardDef("reward_full", entitlements = listOf(EntitlementDef(EntitlementKind.ALL_STAGES)))),
            products = listOf(product("trssgshz01", "reward_full")),
            stages = listOf(StageDef("stage_2", "Quyang", requiredItems = listOf("ticket"))),
        )

        val receipt = CommerceResolver.resolvePurchase(commerce, "trssgshz01", "stage_2")

        assertEquals(setOf(EntitlementKind.ALL_STAGES), receipt.entitlements)
        val access = receipt.stageAccess!!
        assertTrue(access.unlocked)
        assertTrue(access.allStagesEntitled)
    }

    @Test
    fun emptyRewardFailsClosedAtResolution() {
        val commerce = commerce(
            rewards = listOf(RewardDef("empty")),
            products = listOf(product("broken", "empty")),
        )

        assertFailsWith<CommerceResolutionException> {
            CommerceResolver.resolvePurchase(commerce, "broken")
        }
    }

    private fun commerce(
        products: List<ProductDef>,
        rewards: List<RewardDef>,
        stages: List<StageDef> = emptyList(),
    ): CommerceTables = CommerceTables(products = products, rewards = rewards, stages = stages)

    private fun product(id: String, rewardId: String): ProductDef =
        ProductDef(id = id, name = id, price = PriceDef(amountFen = 100), rewardId = rewardId)
}
