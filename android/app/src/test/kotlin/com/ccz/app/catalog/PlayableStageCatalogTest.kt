package com.ccz.app.catalog

import com.ccz.app.campaign.CampaignRuntime
import com.ccz.app.campaign.CampaignStageRuntime
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
            assertEquals(expectedInitialUnits.getValue(runtime.stageId), requireNotNull(launched).initialState().units.size)
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

        private val expectedInitialUnits = mapOf(
            "legacy_stage_2" to 43,
            "legacy_stage_3" to 72,
            "legacy_stage_4" to 52,
            "legacy_stage_5" to 66,
            "legacy_stage_6" to 58,
            "legacy_stage_8" to 70,
            "legacy_stage_9" to 46,
            "legacy_stage_10" to 92,
            "legacy_stage_11" to 47,
            "legacy_stage_12" to 78,
            "legacy_stage_13" to 65,
            "legacy_stage_14" to 96,
        )
    }
}
