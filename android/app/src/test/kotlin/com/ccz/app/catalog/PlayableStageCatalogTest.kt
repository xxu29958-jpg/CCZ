package com.ccz.app.catalog

import com.ccz.app.campaign.CampaignRuntime
import com.ccz.app.campaign.CampaignStageRuntime
import com.ccz.app.campaign.PromotedStageExpectations
import com.ccz.app.campaign.PromotedStageRuntimes
import com.ccz.contentpack.EntitlementKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayableStageCatalogTest {
    @Test
    fun playableRegistryOnlyExposesCommittedNativeStages() {
        val playable = PlayableStageCatalog.playableStages()

        assertEquals(397, PlayableStageCatalog.catalogStageCount())
        assertEquals(expectedRuntimes.map { it.stageId }, playable.map { it.stage.id })
        expectedRuntimes.forEachIndexed { index, runtime ->
            assertSame(runtime, playable[index].runtime)
        }
    }

    @Test
    fun lockedPlayableStageCannotLaunchBeforePurchase() {
        val access = PlayableStageCatalog.accessFor(CampaignRuntime.STAGE_ID)

        assertFalse(access.stageAccess.unlocked)
        assertEquals(listOf("legacy_good_88"), access.stageAccess.missingItems)
        assertFalse(access.canStart)
        assertNull(access.launchRuntimeOrNull())
    }

    @Test
    fun fullUnlockPurchaseDeliversAccessAndLaunchesNativeRuntime() {
        val access = PlayableStageCatalog.resolvePurchase(
            productId = PlayableStageCatalog.FULL_UNLOCK_PRODUCT_ID,
            stageId = CampaignRuntime.STAGE_ID,
        )

        assertEquals(setOf(EntitlementKind.ALL_STAGES), access.entitlements)
        assertTrue(access.stageAccess.unlocked)
        assertTrue(access.canStart)
        assertSame(CampaignRuntime, access.launchRuntimeOrNull())
        assertEquals(33, access.launchRuntimeOrNull()!!.initialState().units.size)
    }

    @Test
    fun promotedStagesUnlockAndLaunchNativeRuntimes() {
        PromotedStageRuntimes.all().forEach { runtime ->
            val access = PlayableStageCatalog.resolvePurchase(
                productId = PlayableStageCatalog.FULL_UNLOCK_PRODUCT_ID,
                stageId = runtime.stageId,
            )
            val launched = access.launchRuntimeOrNull()

            assertTrue(access.stageAccess.unlocked)
            assertTrue(access.canStart)
            assertSame(runtime, launched)
            assertEquals(
                PromotedStageExpectations.initialUnitsByStageId.getValue(runtime.stageId),
                requireNotNull(launched).initialState().units.size,
            )
        }
    }

    @Test
    fun unlockedButUnregisteredLegacyStageDoesNotLaunch() {
        val access = PlayableStageCatalog.resolvePurchase(
            productId = PlayableStageCatalog.FULL_UNLOCK_PRODUCT_ID,
            stageId = "legacy_stage_7",
        )

        assertTrue(access.stageAccess.unlocked)
        assertFalse(access.canStart)
        assertNull(access.launchRuntimeOrNull())
    }

    private companion object {
        private val expectedRuntimes: List<CampaignStageRuntime> =
            listOf(CampaignRuntime) + PromotedStageRuntimes.all()
    }
}
