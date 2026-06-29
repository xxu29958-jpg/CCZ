package com.ccz.app.catalog

import com.ccz.contentpack.ContentValidator
import com.ccz.contentpack.EntitlementKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyCatalogContentTest {
    @Test
    fun bundledLegacyCatalogValidatesCleanWithFullTableCounts() {
        val pack = LegacyCatalogContent.pack()

        assertEquals(emptyList<Any>(), ContentValidator.validate(pack))
        assertEquals("trssgshz_catalog", pack.manifest.contentId)
        assertEquals(2870, pack.tables.items.size)
        assertEquals(19, pack.commerce.products.size)
        assertEquals(19, pack.commerce.rewards.size)
        assertEquals(397, pack.commerce.stages.size)
    }

    @Test
    fun yuanbaoPurchaseDeliversCurrencyButDoesNotUnlockTicketStage() {
        val receipt = LegacyCatalogContent.resolvePurchase("trssgshz03", "legacy_stage_1")
        val access = receipt.stageAccess!!

        assertEquals(188, receipt.inventory["legacy_good_110"])
        assertFalse(access.unlocked)
        assertEquals(listOf("legacy_good_88"), access.missingItems)
    }

    @Test
    fun fullGameAndStageLockProductsUnlockStages() {
        val fullGame = LegacyCatalogContent.resolvePurchase("trssgshz01", "legacy_stage_2")
        val stageLock = LegacyCatalogContent.resolvePurchase("trssgshz02", "legacy_stage_1")

        assertEquals(setOf(EntitlementKind.ALL_STAGES), fullGame.entitlements)
        assertTrue(fullGame.stageAccess!!.unlocked)
        assertTrue(stageLock.stageAccess!!.unlocked)
    }

    @Test
    fun giftBoxProductDeliversItsLegacyGood() {
        val receipt = LegacyCatalogContent.resolvePurchase("trssgshz09")

        assertEquals(1, receipt.inventory["legacy_good_884"])
    }
}
