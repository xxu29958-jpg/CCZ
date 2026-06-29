package com.ccz.app.catalog

import com.ccz.contentpack.CommerceResolver
import com.ccz.contentpack.NativeContent
import com.ccz.contentpack.PurchaseResolution
import com.ccz.contentpack.json.ContentJsonLoader

/** Bundled native catalog generated from the legacy item/product/reward/stage tables. */
object LegacyCatalogContent {
    private const val RESOURCE = "/content/trssgshz_catalog/catalog.json"

    private val catalog: NativeContent by lazy { load() }

    fun pack(): NativeContent = catalog

    fun resolvePurchase(productId: String, stageId: String? = null): PurchaseResolution =
        CommerceResolver.resolvePurchase(pack().commerce, productId, stageId)

    private fun load(): NativeContent =
        ContentJsonLoader.load(
            (javaClass.getResourceAsStream(RESOURCE) ?: error("bundled legacy catalog missing: $RESOURCE"))
                .bufferedReader(Charsets.UTF_8).use { it.readText() },
        )
}
