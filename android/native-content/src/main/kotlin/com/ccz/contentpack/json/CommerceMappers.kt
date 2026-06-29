package com.ccz.contentpack.json

import com.ccz.contentpack.CommerceTables
import com.ccz.contentpack.EntitlementDef
import com.ccz.contentpack.ItemGrantDef
import com.ccz.contentpack.PriceDef
import com.ccz.contentpack.ProductDef
import com.ccz.contentpack.RewardDef
import com.ccz.contentpack.StageDef

internal fun toCommerce(dto: CommerceDto): CommerceTables =
    CommerceTables(
        products = dto.products.map { toProduct(it) },
        rewards = dto.rewards.mapIndexed { index, value -> toReward(index, value) },
        stages = dto.stages.map { toStage(it) },
    )

private fun toProduct(dto: ProductDto): ProductDef =
    ProductDef(
        id = dto.id,
        name = dto.name,
        price = PriceDef(amountFen = dto.price.amountFen, currency = dto.price.currency),
        rewardId = dto.rewardId,
    )

private fun toReward(rewardIndex: Int, dto: RewardDto): RewardDef =
    RewardDef(
        id = dto.id,
        itemGrants = dto.itemGrants.map { ItemGrantDef(it.itemId, it.quantity) },
        entitlements = dto.entitlements.mapIndexed { index, entitlement ->
            EntitlementDef(
                kind = decodeEntitlementKind("commerce.rewards[$rewardIndex].entitlements[$index].kind", entitlement.kind),
                target = entitlement.target,
            )
        },
    )

private fun toStage(dto: StageDto): StageDef =
    StageDef(id = dto.id, name = dto.name, entry = dto.entry, requiredItems = dto.requiredItems)
