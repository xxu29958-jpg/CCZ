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
            "legacy_stage_16" to 38,
            "legacy_stage_17" to 42,
            "legacy_stage_18" to 70,
            "legacy_stage_19" to 23,
            "legacy_stage_21" to 27,
            "legacy_stage_23" to 51,
            "legacy_stage_24" to 52,
            "legacy_stage_29" to 61,
            "legacy_stage_30" to 81,
            "legacy_stage_31" to 30,
            "legacy_stage_34" to 50,
            "legacy_stage_35" to 60,
            "legacy_stage_36" to 22,
            "legacy_stage_37" to 55,
            "legacy_stage_38" to 45,
            "legacy_stage_39" to 42,
            "legacy_stage_40" to 37,
            "legacy_stage_41" to 63,
            "legacy_stage_42" to 58,
            "legacy_stage_44" to 38,
            "legacy_stage_45" to 27,
            "legacy_stage_46" to 63,
            "legacy_stage_47" to 50,
            "legacy_stage_48" to 41,
            "legacy_stage_50" to 72,
            "legacy_stage_51" to 59,
            "legacy_stage_52" to 55,
            "legacy_stage_53" to 46,
            "legacy_stage_54" to 75,
            "legacy_stage_55" to 70,
            "legacy_stage_56" to 81,
            "legacy_stage_57" to 79,
            "legacy_stage_58" to 44,
            "legacy_stage_59" to 40,
            "legacy_stage_61" to 68,
            "legacy_stage_62" to 68,
        )
    }
}
