package com.ccz.modimport

import com.ccz.contentpack.ContentValidator
import com.ccz.contentpack.EntitlementKind
import com.ccz.contentpack.json.ContentJsonLoader
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class PackItem(
    val id: String,
    val name: String,
    val type: String,
)

@Serializable
data class PackCommerce(
    val products: List<PackProduct> = emptyList(),
    val rewards: List<PackReward> = emptyList(),
    val stages: List<PackStage> = emptyList(),
)

@Serializable
data class PackProduct(
    val id: String,
    val name: String,
    val price: PackPrice,
    @SerialName("reward_id") val rewardId: String,
)

@Serializable
data class PackPrice(
    @SerialName("amount_fen") val amountFen: Int,
    val currency: String = "CNY",
)

@Serializable
data class PackReward(
    val id: String,
    @SerialName("item_grants") val itemGrants: List<PackItemGrant> = emptyList(),
    val entitlements: List<PackEntitlement> = emptyList(),
)

@Serializable
data class PackItemGrant(
    @SerialName("item_id") val itemId: String,
    val quantity: Int,
)

@Serializable
data class PackEntitlement(
    val kind: String,
    val target: String? = null,
)

@Serializable
data class PackStage(
    val id: String,
    val name: String,
    val entry: String? = null,
    @SerialName("required_items") val requiredItems: List<String> = emptyList(),
)

/**
 * Generates the full legacy catalog (items + products + rewards + stage unlock requirements) as native content.
 *
 * This is a catalog pack, not a battle pack: it carries one empty R-script as the manifest entry so the strict
 * native validator can accept it without pretending all 397 legacy stages have battle scripts converted already.
 */
object LegacyCatalogGenerator {
    private const val CONTENT_ID = "trssgshz_catalog"
    private const val CONTENT_VERSION = "0.1.0"
    private const val ENTRY = "catalog_entry"
    private const val ITEM_TYPE = "legacy_good"
    private const val ITEM_PREFIX = "legacy_good_"
    private const val STAGE_PREFIX = "legacy_stage_"
    private const val REWARD_PREFIX = "legacy_reward_"

    private val writer = Json { prettyPrint = true }

    fun generate(extractedRoot: String): String {
        val catalog = LegacyCommerceVerifier.load(extractedRoot)
        val pack = PackContent(
            manifest = PackManifest(
                nativeFormatVersion = LegacyContentImporter.NATIVE_FORMAT_VERSION,
                contentId = CONTENT_ID,
                contentVersion = CONTENT_VERSION,
                source = PackSource(mod = "trssgshz"),
                entry = ENTRY,
            ),
            tables = PackTables(items = catalog.goods.values.sortedBy { it.id }.map(::toItem)),
            events = PackEvents(rScripts = listOf(PackRScript(ENTRY, ops = emptyList()))),
            commerce = PackCommerce(
                products = catalog.products.values.map(::toProduct),
                rewards = catalog.products.keys.sorted().map { chargeId -> toReward(chargeId, catalog) },
                stages = catalog.stages.values.sortedBy { it.id }.map(::toStage),
            ),
        )
        return writer.encodeToString(PackContent.serializer(), pack).also(::validateGenerated)
    }

    private fun validateGenerated(json: String) {
        val content = ContentJsonLoader.load(json)
        val issues = ContentValidator.validate(content)
        if (issues.isNotEmpty()) {
            throw LegacyCommerceException("generated catalog failed validation: ${issues.joinToString { "${it.path}: ${it.message}" }}")
        }
    }

    private fun toItem(good: LegacyGood): PackItem =
        PackItem(id = itemId(good.id), name = good.name, type = ITEM_TYPE)

    private fun toProduct(product: LegacyProduct): PackProduct =
        PackProduct(
            id = product.chargeId,
            name = product.subject,
            price = PackPrice(amountFen = product.priceFen),
            rewardId = rewardId(product.chargeId),
        )

    private fun toReward(chargeId: String, catalog: LegacyCommerceCatalog): PackReward =
        PackReward(
            id = rewardId(chargeId),
            itemGrants = catalog.deliveries[chargeId].orEmpty().map { PackItemGrant(itemId(it.goodId), it.quantity) },
            entitlements = catalog.entitlements[chargeId].orEmpty().map(::toEntitlement),
        )

    private fun toEntitlement(kind: EntitlementKind): PackEntitlement =
        PackEntitlement(kind = kind.name.lowercase())

    private fun toStage(stage: LegacyStage): PackStage =
        PackStage(
            id = stageId(stage.id),
            name = stage.name,
            requiredItems = stage.requiredGoodIds.map(::itemId),
        )

    private fun itemId(goodId: Int): String = ITEM_PREFIX + goodId

    private fun stageId(gkId: Int): String = STAGE_PREFIX + gkId

    private fun rewardId(chargeId: String): String = REWARD_PREFIX + chargeId

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2 && args[0].isNotBlank() && args[1].isNotBlank()) {
            "usage: <extractedRoot> <outPath>"
        }
        val json = generate(args[0])
        File(args[1]).apply { parentFile?.mkdirs() }.writeText(json, Charsets.UTF_8)
    }
}
