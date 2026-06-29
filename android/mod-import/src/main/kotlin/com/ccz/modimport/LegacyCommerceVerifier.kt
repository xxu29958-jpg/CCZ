package com.ccz.modimport

import com.ccz.contentpack.CommerceResolutionException
import com.ccz.contentpack.CommerceResolver
import com.ccz.contentpack.CommerceTables
import com.ccz.contentpack.EntitlementDef
import com.ccz.contentpack.EntitlementKind
import com.ccz.contentpack.ItemGrantDef
import com.ccz.contentpack.PriceDef
import com.ccz.contentpack.ProductDef
import com.ccz.contentpack.RewardDef
import com.ccz.contentpack.StageDef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class LegacyCommerceException(message: String) : RuntimeException(message)

data class LegacyCommerceCatalog(
    val products: Map<String, LegacyProduct>,
    val deliveries: Map<String, List<LegacyDeliveryRule>>,
    val entitlements: Map<String, Set<EntitlementKind>>,
    val goods: Map<Int, LegacyGood>,
    val stages: Map<Int, LegacyStage>,
    val commerce: CommerceTables,
)

data class LegacyProduct(
    val chargeId: String,
    val subject: String,
    val priceFen: Int,
)

data class LegacyDeliveryRule(
    val chargeId: String,
    val goodId: Int,
    val quantity: Int,
)

data class LegacyGood(
    val id: Int,
    val name: String,
)

data class LegacyStage(
    val id: Int,
    val name: String,
    val requiredGoodIds: List<Int>,
)

data class LegacyDeliveredGood(
    val good: LegacyGood,
    val quantity: Int,
)

data class LegacyStageAccess(
    val stage: LegacyStage,
    val unlocked: Boolean,
    val requiredGoods: List<LegacyGood>,
    val missingGoods: List<LegacyGood>,
    val allStagesEntitled: Boolean,
)

data class LegacyPurchaseVerification(
    val product: LegacyProduct,
    val deliveredGoods: List<LegacyDeliveredGood>,
    val inventory: Map<Int, Int>,
    val entitlements: Set<EntitlementKind>,
    val stageAccess: LegacyStageAccess?,
)

/**
 * Offline verifier for the legacy commerce tables. It consumes decrypted JSON as data only and models the
 * purchase -> delivery -> stage access path inside this repository.
 */
object LegacyCommerceVerifier {
    private const val ALL_STAGES_CONTENT_MARKER = "\u5f00\u542f\u5168\u90e8\u5173\u5361"
    private const val STAGE_LOCK_SUBJECT_MARKER = "\u5173\u5361\u9501"

    private val reader = Json { ignoreUnknownKeys = true; isLenient = true }

    fun load(extractedRoot: String): LegacyCommerceCatalog {
        val jsonDir = File(extractedRoot, "json")
        if (!jsonDir.isDirectory) throw LegacyCommerceException("missing legacy json directory: ${jsonDir.path}")
        val products = loadProducts(jsonDir)
        val goods = loadGoods(jsonDir)
        val deliveries = loadDeliveries(jsonDir, products, goods)
        val stages = loadStages(jsonDir, goods)
        val entitlements = loadEntitlements(jsonDir, products)
        return LegacyCommerceCatalog(
            products = products,
            deliveries = deliveries,
            entitlements = entitlements,
            goods = goods,
            stages = stages,
            commerce = toNativeCommerce(products, deliveries, entitlements, stages),
        )
    }

    fun verifyPaidPurchase(
        catalog: LegacyCommerceCatalog,
        chargeId: String,
        stageId: Int? = null,
    ): LegacyPurchaseVerification {
        val receipt = try {
            CommerceResolver.resolvePurchase(catalog.commerce, chargeId, stageId?.let(::stageKey))
        } catch (e: CommerceResolutionException) {
            throw LegacyCommerceException(e.message ?: "commerce resolution failed")
        }
        val inventory = receipt.inventory.mapKeys { (itemId, _) -> legacyGoodId(itemId) }
        val delivered = receipt.reward.itemGrants.map { grant ->
            LegacyDeliveredGood(catalog.goods.getValue(legacyGoodId(grant.itemId)), grant.quantity)
        }
        return LegacyPurchaseVerification(
            product = catalog.products.getValue(chargeId),
            deliveredGoods = delivered,
            inventory = inventory,
            entitlements = receipt.entitlements,
            stageAccess = receipt.stageAccess?.let { access ->
                val id = legacyStageId(access.stage.id)
                LegacyStageAccess(
                    stage = catalog.stages.getValue(id),
                    unlocked = access.unlocked,
                    requiredGoods = access.stage.requiredItems.map { catalog.goods.getValue(legacyGoodId(it)) },
                    missingGoods = access.missingItems.map { catalog.goods.getValue(legacyGoodId(it)) },
                    allStagesEntitled = access.allStagesEntitled,
                )
            },
        )
    }

    fun format(result: LegacyPurchaseVerification): String = buildString {
        appendLine("PURCHASE_OK charge_id=${result.product.chargeId} price_fen=${result.product.priceFen}")
        appendLine("product=${result.product.subject}")
        appendLine("delivered_goods=${formatDelivered(result.deliveredGoods)}")
        appendLine("entitlements=${result.entitlements.ifEmpty { setOf() }.joinToString { it.name }.ifBlank { "none" }}")
        result.stageAccess?.let { access ->
            appendLine(
                "stage=${access.stage.id} ${access.stage.name} " +
                    "unlocked=${access.unlocked} all_stages_entitled=${access.allStagesEntitled}",
            )
            appendLine("stage_required=${formatGoods(access.requiredGoods)}")
            appendLine("stage_missing=${formatGoods(access.missingGoods)}")
        }
    }

    private fun loadProducts(jsonDir: File): Map<String, LegacyProduct> {
        val rows = readRows(jsonDir, "trpay.json", LegacyPayRow.serializer())
        rows.forEach { row ->
            if (row.chargeId.isBlank()) throw LegacyCommerceException("trpay.json contains blank charge_id")
            if (row.price < 0) throw LegacyCommerceException("trpay.json contains negative price for ${row.chargeId}")
        }
        return associateUnique(rows, "trpay.charge_id") { it.chargeId }
            .mapValues { (_, row) -> LegacyProduct(row.chargeId, row.subject, row.price) }
    }

    private fun loadGoods(jsonDir: File): Map<Int, LegacyGood> {
        val rows = readRows(jsonDir, "dic_item.json", LegacyItemRow.serializer())
        rows.forEach { row ->
            if (row.goodId <= 0) throw LegacyCommerceException("dic_item.json contains non-positive good_id: ${row.goodId}")
        }
        return associateUnique(rows, "dic_item.good_id") { it.goodId }
            .mapValues { (_, row) -> LegacyGood(row.goodId, row.name) }
    }

    private fun loadDeliveries(
        jsonDir: File,
        products: Map<String, LegacyProduct>,
        goods: Map<Int, LegacyGood>,
    ): Map<String, List<LegacyDeliveryRule>> {
        val rows = readRows(jsonDir, "good_charge.json", LegacyGoodChargeRow.serializer())
        val rules = rows.map { row -> LegacyDeliveryRule(row.chargeId, row.goodId, row.quantity) }
        rules.forEach { rule ->
            if (rule.chargeId !in products) throw LegacyCommerceException("good_charge references unknown charge_id: ${rule.chargeId}")
            if (rule.goodId !in goods) throw LegacyCommerceException("good_charge references unknown good_id: ${rule.goodId}")
            if (rule.quantity <= 0) throw LegacyCommerceException("good_charge quantity must be positive: ${rule.quantity}")
        }
        return rules.groupBy { it.chargeId }
    }

    private fun loadStages(jsonDir: File, goods: Map<Int, LegacyGood>): Map<Int, LegacyStage> {
        val rows = readRows(jsonDir, "dic_gk.json", LegacyStageRow.serializer())
        val stages = rows.map { row ->
            LegacyStage(row.stageId, row.name, parseRequiredGoods(row.requiredGoods, row.stageId))
        }
        stages.forEach { stage ->
            if (stage.id <= 0) throw LegacyCommerceException("dic_gk contains non-positive gkid: ${stage.id}")
            stage.requiredGoodIds.forEach { goodId ->
                if (goodId !in goods) throw LegacyCommerceException("dic_gk ${stage.id} references unknown buyid good_id: $goodId")
            }
        }
        return associateUnique(stages, "dic_gk.gkid") { it.id }
    }

    private fun loadEntitlements(
        jsonDir: File,
        products: Map<String, LegacyProduct>,
    ): Map<String, Set<EntitlementKind>> {
        val entitlements = linkedMapOf<String, MutableSet<EntitlementKind>>()
        readRows(jsonDir, "buy_charge.json", LegacyBuyChargeRow.serializer()).forEach { row ->
            if (row.chargeId !in products) throw LegacyCommerceException("buy_charge references unknown charge_id: ${row.chargeId}")
            if (row.content.contains(ALL_STAGES_CONTENT_MARKER)) entitlements.add(row.chargeId, EntitlementKind.ALL_STAGES)
        }
        products.values.forEach { product ->
            if (product.subject.contains(STAGE_LOCK_SUBJECT_MARKER)) entitlements.add(product.chargeId, EntitlementKind.ALL_STAGES)
        }
        return entitlements.mapValues { (_, values) -> values.toSet() }
    }

    private fun toNativeCommerce(
        products: Map<String, LegacyProduct>,
        deliveries: Map<String, List<LegacyDeliveryRule>>,
        entitlements: Map<String, Set<EntitlementKind>>,
        stages: Map<Int, LegacyStage>,
    ): CommerceTables =
        CommerceTables(
            products = products.values.map { product ->
                ProductDef(
                    id = product.chargeId,
                    name = product.subject,
                    price = PriceDef(amountFen = product.priceFen),
                    rewardId = rewardKey(product.chargeId),
                )
            },
            rewards = products.keys.map { chargeId ->
                RewardDef(
                    id = rewardKey(chargeId),
                    itemGrants = deliveries[chargeId].orEmpty().map { ItemGrantDef(goodKey(it.goodId), it.quantity) },
                    entitlements = entitlements[chargeId].orEmpty().map { EntitlementDef(it) },
                )
            },
            stages = stages.values.map { stage ->
                StageDef(
                    id = stageKey(stage.id),
                    name = stage.name,
                    requiredItems = stage.requiredGoodIds.map(::goodKey),
                )
            },
        )

    private fun parseRequiredGoods(raw: String, stageId: Int): List<Int> =
        raw.split("&")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "0" }
            .map { token -> token.toIntOrNull() ?: throw LegacyCommerceException("dic_gk $stageId has non-integer buyid token: $token") }
            .distinct()

    private fun formatDelivered(delivered: List<LegacyDeliveredGood>): String =
        delivered.ifEmpty { return "none" }.joinToString { "${it.good.id}:${it.good.name}x${it.quantity}" }

    private fun formatGoods(goods: List<LegacyGood>): String =
        goods.ifEmpty { return "none" }.joinToString { "${it.id}:${it.name}" }

    private fun <T, K> associateUnique(rows: List<T>, label: String, keyOf: (T) -> K): Map<K, T> {
        val mapped = linkedMapOf<K, T>()
        rows.forEach { row ->
            val key = keyOf(row)
            if (mapped.put(key, row) != null) throw LegacyCommerceException("duplicate $label: $key")
        }
        return mapped
    }

    private fun <T> readRows(dir: File, name: String, serializer: kotlinx.serialization.KSerializer<T>): List<T> =
        reader.decodeFromString(ListSerializer(serializer), read(dir, name))

    private fun read(dir: File, name: String): String {
        val file = File(dir, name)
        if (!file.isFile) throw LegacyCommerceException("missing legacy table: ${file.path}")
        return file.readText(Charsets.UTF_8).removePrefix("\uFEFF")
    }

    private fun goodKey(goodId: Int): String = "legacy_good_$goodId"

    private fun legacyGoodId(itemId: String): Int =
        itemId.removePrefix("legacy_good_").toIntOrNull() ?: throw LegacyCommerceException("bad native item id: $itemId")

    private fun stageKey(stageId: Int): String = "legacy_stage_$stageId"

    private fun legacyStageId(stageId: String): Int =
        stageId.removePrefix("legacy_stage_").toIntOrNull() ?: throw LegacyCommerceException("bad native stage id: $stageId")

    private fun rewardKey(chargeId: String): String = "legacy_reward_$chargeId"

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size in 2..3 && args[0].isNotBlank() && args[1].isNotBlank()) {
            "usage: <extractedRoot> <chargeId> [gkId]"
        }
        val stageId = args.getOrNull(2)?.toIntOrNull()
        println(format(verifyPaidPurchase(load(args[0]), args[1], stageId)))
    }
}

private fun MutableMap<String, MutableSet<EntitlementKind>>.add(chargeId: String, entitlement: EntitlementKind) {
    getOrPut(chargeId) { linkedSetOf() }.add(entitlement)
}

@Serializable
private data class LegacyPayRow(
    @SerialName("charge_id") val chargeId: String,
    val subject: String = "",
    val price: Int = 0,
)

@Serializable
private data class LegacyGoodChargeRow(
    @SerialName("charge_id") val chargeId: String,
    @SerialName("good_id") val goodId: Int,
    @SerialName("num") val quantity: Int,
)

@Serializable
private data class LegacyBuyChargeRow(
    @SerialName("charge_id") val chargeId: String,
    val content: String = "",
)

@Serializable
private data class LegacyStageRow(
    @SerialName("gkid") val stageId: Int,
    @SerialName("gkname") val name: String,
    @SerialName("buyid") val requiredGoods: String = "",
)

@Serializable
private data class LegacyItemRow(
    @SerialName("good_id") val goodId: Int,
    val name: String = "",
)
