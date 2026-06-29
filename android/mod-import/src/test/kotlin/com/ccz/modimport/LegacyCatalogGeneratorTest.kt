package com.ccz.modimport

import com.ccz.contentpack.ContentValidator
import com.ccz.contentpack.EntitlementKind
import com.ccz.contentpack.json.ContentJsonLoader
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegacyCatalogGeneratorTest {
    @Test
    fun generatesNativeCatalogPackFromLegacyTables() {
        val json = LegacyCatalogGenerator.generate(
            legacyRoot(
                products = """[
                    { "charge_id": "trssgshz01", "subject": "Full game", "price": 1800 },
                    { "charge_id": "trssgshz03", "subject": "Yuanbao x188", "price": 1200 }
                ]""",
                deliveries = """[{ "charge_id": "trssgshz03", "good_id": 110, "num": 188 }]""",
                buyCharge = """[{ "charge_id": "trssgshz01", "content": "$ALL_STAGES" }]""",
                goods = goods(88 to "ticket", 110 to "yuanbao"),
                stages = """[{ "gkid": 1, "gkname": "Stage 1", "buyid": "88" }]""",
            ),
        )

        val content = ContentJsonLoader.load(json)

        assertEquals(emptyList(), ContentValidator.validate(content))
        assertEquals("trssgshz_catalog", content.manifest.contentId)
        assertEquals(listOf("legacy_good_88", "legacy_good_110"), content.tables.items.map { it.id })
        assertEquals(setOf("trssgshz01", "trssgshz03"), content.commerce.products.mapTo(HashSet()) { it.id })
        assertEquals(listOf("legacy_good_110" to 188), content.commerce.rewards.first { it.id.endsWith("trssgshz03") }.itemGrants.map { it.itemId to it.quantity })
        assertEquals(EntitlementKind.ALL_STAGES, content.commerce.rewards.first { it.id.endsWith("trssgshz01") }.entitlements.single().kind)
        assertEquals(listOf("legacy_good_88"), content.commerce.stages.single().requiredItems)
        assertTrue(content.events.rScripts.any { it.id == content.manifest.entry })
    }

    private fun legacyRoot(
        products: String,
        deliveries: String = "[]",
        buyCharge: String = "[]",
        goods: String,
        stages: String,
    ): String {
        val dir = createTempDirectory("legacy-catalog-")
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
    }
}
