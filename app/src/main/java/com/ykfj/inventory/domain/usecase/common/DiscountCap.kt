package com.ykfj.inventory.domain.usecase.common

/**
 * Single source of truth for the "max discount = 20% of profit" rule from
 * [docs/business/Pricing-and-Discounts.md].
 *
 * The UI presents the cap in terms of the catalog price:
 *   `maxDiscount = 0.20 × (catalogPrice − capitalPrice)`
 *
 * Use cases see the **final** sold price (already discounted) and the per-unit
 * capital. The algebraically equivalent constraint they can check is:
 *
 *   `discountAmount ≤ (soldPrice − capitalPrice) / 4`     when profit > 0
 *   `discountAmount ≤ 0`                                   when profit ≤ 0
 *
 * Derivation: let P = catalog price, S = soldPrice, D = discountAmount.
 * Then S = P − D, so P = S + D. The UI rule D ≤ 0.20·(P − cap) becomes
 * D ≤ 0.20·(S + D − cap), i.e. 0.80·D ≤ 0.20·(S − cap), i.e. D ≤ (S − cap)/4.
 *
 * All values are per-unit.
 */
object DiscountCap {

    /** Tolerance for floating-point comparison (half a centavo). */
    const val EPSILON = 0.005

    /**
     * Per-unit max discount allowed for a sale at [soldPrice] (final, after discount)
     * with snapshotted [capitalPrice]. Returns 0 when there's no profit to discount from.
     */
    fun maxPerUnit(soldPrice: Double, capitalPrice: Double): Double {
        val realizedProfit = soldPrice - capitalPrice
        return (realizedProfit / 4.0).coerceAtLeast(0.0)
    }
}
