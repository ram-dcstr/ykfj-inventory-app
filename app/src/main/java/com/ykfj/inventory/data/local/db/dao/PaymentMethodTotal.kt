package com.ykfj.inventory.data.local.db.dao

/**
 * Projection for "sum of amounts grouped by payment method / channel for a day"
 * (Daily Cash). [method] is the [com.ykfj.inventory.data.local.db.enums.PaymentMethod]
 * enum name, e.g. "CASH". Only methods with matching rows appear, so callers
 * default missing ones to 0.0 — matching the per-method `COALESCE(..., 0.0)`.
 */
data class PaymentMethodTotal(
    val method: String,
    val total: Double,
)
