package com.ykfj.inventory.domain.usecase.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks down the core pricing rule: max discount = 20% of profit
 * (docs/business/Pricing-and-Discounts.md). Getting this wrong over- or
 * under-charges real money on every discounted sale.
 */
class DiscountCapTest {

    @Test
    fun `cap is a quarter of the realized profit`() {
        // sold 1000, capital 600 -> profit 400 -> per-unit cap 100.
        assertEquals(100.0, DiscountCap.maxPerUnit(soldPrice = 1000.0, capitalPrice = 600.0), 1e-9)
    }

    @Test
    fun `cap scales linearly with profit`() {
        assertEquals(250.0, DiscountCap.maxPerUnit(soldPrice = 2000.0, capitalPrice = 1000.0), 1e-9)
    }

    @Test
    fun `no profit means no discount allowed`() {
        assertEquals(0.0, DiscountCap.maxPerUnit(soldPrice = 600.0, capitalPrice = 600.0), 1e-9)
    }

    @Test
    fun `selling below capital never yields a negative cap`() {
        assertEquals(0.0, DiscountCap.maxPerUnit(soldPrice = 500.0, capitalPrice = 600.0), 1e-9)
    }

    @Test
    fun `cap matches the UI's 20 percent-of-catalog-profit framing`() {
        // The UI computes maxDiscount = 0.20 * (catalogPrice - capital). At the
        // capped sold price (catalog - thatDiscount), maxPerUnit must return the
        // same number — the two framings are algebraically equivalent.
        val catalog = 1000.0
        val capital = 600.0
        val uiCap = 0.20 * (catalog - capital) // 80
        val soldAtCap = catalog - uiCap        // 920
        assertEquals(uiCap, DiscountCap.maxPerUnit(soldAtCap, capital), 1e-9)
    }
}
