package com.ykfj.inventory.util

import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {

    private val formatter: NumberFormat =
        NumberFormat.getCurrencyInstance(Locale("en", "PH")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }

    /** Formats [amount] as `₱3,200.00`. Thread-safe via per-call synchronization in NumberFormat. */
    fun format(amount: Double): String = synchronized(formatter) {
        formatter.format(amount)
    }
}
