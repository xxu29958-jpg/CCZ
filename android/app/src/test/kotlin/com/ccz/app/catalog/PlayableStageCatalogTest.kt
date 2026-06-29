package com.ccz.app.catalog

import com.ccz.app.campaign.CampaignRuntime
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
        assertEquals(
            listOf(CampaignRuntime.STAGE_ID, PromotedStageRuntimes.QuyangSiege.stageId, PromotedStageRuntimes.ShimenAttack.stageId),
            playable.map { it.stage.id },
        )
        assertSame(CampaignRuntime, playable.first().runtime)
        assertSame(PromotedStageRuntimes.QuyangSiege, playable[1].runtime)
        assertSame(PromotedStageRuntimes.ShimenAttack, playable[2].runtime)
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
        val second = PlayableStageCatalog.resolvePurchase(
            productId = PlayableStageCatalog.FULL_UNLOCK_PRODUCT_ID,
            stageId = PromotedStageRuntimes.QuyangSiege.stageId,
        )
        val third = PlayableStageCatalog.resolvePurchase(
            productId = PlayableStageCatalog.FULL_UNLOCK_PRODUCT_ID,
            stageId = PromotedStageRuntimes.ShimenAttack.stageId,
        )

        assertTrue(second.stageAccess.unlocked)
        assertTrue(second.canStart)
        assertSame(PromotedStageRuntimes.QuyangSiege, second.launchRuntimeOrNull())
        assertEquals(43, second.launchRuntimeOrNull()!!.initialState().units.size)
        assertTrue(third.stageAccess.unlocked)
        assertTrue(third.canStart)
        assertSame(PromotedStageRuntimes.ShimenAttack, third.launchRuntimeOrNull())
        assertEquals(72, third.launchRuntimeOrNull()!!.initialState().units.size)
    }

    @Test
    fun unlockedButUnregisteredLegacyStageDoesNotLaunch() {
        val access = PlayableStageCatalog.resolvePurchase(
            productId = PlayableStageCatalog.FULL_UNLOCK_PRODUCT_ID,
            stageId = "legacy_stage_4",
        )

        assertTrue(access.stageAccess.unlocked)
        assertFalse(access.canStart)
        assertNull(access.launchRuntimeOrNull())
    }
}
