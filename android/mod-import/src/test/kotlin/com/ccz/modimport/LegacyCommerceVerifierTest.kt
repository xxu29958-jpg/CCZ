package com.ccz.modimport

import com.ccz.contentpack.EntitlementKind
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacyCommerceVerifierTest {
    @Test
    fun paidChargeDeliversGoodsAndUnlocksStageByRequiredGood() {
        val catalog = LegacyCommerceVerifier.load(
            legacyRoot(
                products = """[{ "charge_id": "trssgshz03", "subject": "Yuanbao x188", "price": 1200 }]""",
                deliveries = """[{ "charge_id": "trssgshz03", "good_id": 110, "num": 188 }]""",
                goods = goods(110 to "yuanbao"),
                stages = """[{ "gkid": 1, "gkname": "Stage 1", "buyid": "110" }]""",
            ),
        )

        val result = LegacyCommerceVerifier.verifyPaidPurchase(catalog, "trssgshz03", stageId = 1)

        assertEquals(188, result.inventory[110])
        assertEquals("yuanbao", result.deliveredGoods.single().good.name)
        assertTrue(result.stageAccess!!.unlocked)
    }

    @Test
    fun fullPurchaseUnlocksAllStagesFromBuyChargeContent() {
        val catalog = LegacyCommerceVerifier.load(
            legacyRoot(
                products = """[{ "charge_id": "trssgshz01", "subject": "Full game", "price": 1800 }]""",
                buyCharge = """[{ "charge_id": "trssgshz01", "content": "$ALL_STAGES" }]""",
                goods = goods(88 to "ticket"),
                stages = """[{ "gkid": 2, "gkname": "Stage 2", "buyid": "88" }]""",
            ),
        )

        val result = LegacyCommerceVerifier.verifyPaidPurchase(catalog, "trssgshz01", stageId = 2)

        assertEquals(setOf(EntitlementKind.ALL_STAGES), result.entitlements)
        val access = result.stageAccess!!
        assertTrue(access.unlocked)
        assertTrue(access.allStagesEntitled)
    }

    @Test
    fun stageLockProductUnlocksAllStagesFromSubject() {
        val catalog = LegacyCommerceVerifier.load(
            legacyRoot(
                products = """[{ "charge_id": "trssgshz02", "subject": "Buy $STAGE_LOCK", "price": 1800 }]""",
                goods = goods(88 to "ticket"),
                stages = """[{ "gkid": 1, "gkname": "Stage 1", "buyid": "88" }]""",
            ),
        )

        val result = LegacyCommerceVerifier.verifyPaidPurchase(catalog, "trssgshz02", stageId = 1)

        assertEquals(setOf(EntitlementKind.ALL_STAGES), result.entitlements)
        assertTrue(result.stageAccess!!.unlocked)
    }

    @Test
    fun stageStaysLockedWhenRequiredGoodIsMissing() {
        val catalog = LegacyCommerceVerifier.load(
            legacyRoot(
                products = """[{ "charge_id": "trssgshz03", "subject": "Yuanbao x188", "price": 1200 }]""",
                deliveries = """[{ "charge_id": "trssgshz03", "good_id": 110, "num": 188 }]""",
                goods = goods(88 to "ticket", 110 to "yuanbao"),
                stages = """[{ "gkid": 1, "gkname": "Stage 1", "buyid": "88" }]""",
            ),
        )

        val result = LegacyCommerceVerifier.verifyPaidPurchase(catalog, "trssgshz03", stageId = 1)

        val access = result.stageAccess!!
        assertFalse(access.unlocked)
        assertEquals(listOf(88), access.missingGoods.map { it.id })
    }

    @Test
    fun unknownChargeFailsClosed() {
        val catalog = LegacyCommerceVerifier.load(legacyRoot(goods = goods(88 to "ticket")))

        assertFailsWith<LegacyCommerceException> {
            LegacyCommerceVerifier.verifyPaidPurchase(catalog, "missing")
        }
    }

    @Test
    fun chargeWithoutDeliveryOrEntitlementFailsClosed() {
        val catalog = LegacyCommerceVerifier.load(
            legacyRoot(
                products = """[{ "charge_id": "broken", "subject": "Broken", "price": 100 }]""",
                goods = goods(88 to "ticket"),
            ),
        )

        assertFailsWith<LegacyCommerceException> {
            LegacyCommerceVerifier.verifyPaidPurchase(catalog, "broken")
        }
    }

    @Test
    fun deliveryReferencingUnknownGoodFailsAtLoad() {
        assertFailsWith<LegacyCommerceException> {
            LegacyCommerceVerifier.load(
                legacyRoot(
                    products = """[{ "charge_id": "broken", "subject": "Broken", "price": 100 }]""",
                    deliveries = """[{ "charge_id": "broken", "good_id": 999, "num": 1 }]""",
                    goods = goods(88 to "ticket"),
                ),
            )
        }
    }

    private fun legacyRoot(
        products: String = "[]",
        deliveries: String = "[]",
        buyCharge: String = "[]",
        goods: String = "[]",
        stages: String = "[]",
    ): String {
        val dir = createTempDirectory("legacy-commerce-")
        val json = dir.resolve("json").createDirectories()
        json.resolve("trpay.json").writeText(products)
        json.resolve("good_charge.json").writeText(deliveries)
        json.resolve("buy_charge.json").writeText(buyCharge)
        json.resolve("dic_item.json").writeText(goods)
        json.resolve("dic_gk.json").writeText(stages)
        return dir.toString()
    }

    private fun goods(vararg rows: Pair<Int, String>): String =
        rows.joinToString(prefix = "[", postfix = "]") { (id, name) -> """{ "good_id": $id, "name": "$name" }""" }

    private companion object {
        private const val ALL_STAGES = "\u5f00\u542f\u5168\u90e8\u5173\u5361"
        private const val STAGE_LOCK = "\u5173\u5361\u9501"
    }
}
