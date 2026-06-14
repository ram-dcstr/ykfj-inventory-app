package com.ykfj.inventory.util

import com.ykfj.inventory.data.local.db.dao.ProductDao
import javax.inject.Inject

/**
 * Generates a short, human-readable product ID:
 * `{NAME_ABBR}-{RATE_ABBR}-{CAT_ABBR}-{6-digit-seq}`
 *
 * Examples:
 * - `Monaco` + `18K Saudi Gold` + `Necklace`  → `MNC-18KSG-NCK-000001`
 * - `Cuban`  + `18K Yellow Gold` + `Bracelet` → `CBN-18KYG-BRC-000002`
 * - `Bangle` + null (fixed price)  + `Bangle`  → `BNG-FXD-BNG-000001`
 *
 * Abbreviation rules:
 *  - Name / Category (single word): first 3 consonants, uppercase; pad with 'X' if short.
 *  - Name / Category (multi-word): initials of each word, up to 3 chars.
 *  - Metal rate: leading karat prefix (e.g. "18K") + first letter of each remaining word.
 *  - Fixed-price items use "FXD" for the rate segment.
 *
 * The 6-digit sequence is scoped per ID **prefix** (`NAME-RATE-CAT-`), not per raw
 * name/rate/category — so distinct names that abbreviate to the same prefix (e.g.
 * "piyao" and "payao" both → "PYX") still receive unique, non-colliding IDs.
 */
class ProductIdGenerator @Inject constructor(
    private val productDao: ProductDao,
) {
    suspend fun generate(
        name: String,
        metalRateName: String?,
        categoryName: String,
    ): String {
        val nameAbbr = abbreviateWord(name)
        val rateAbbr = if (metalRateName != null) abbreviateRate(metalRateName) else "FXD"
        val catAbbr = abbreviateWord(categoryName)

        val prefix = "$nameAbbr-$rateAbbr-$catAbbr-"
        val maxSeq = productDao.maxSequenceForPrefix(prefix) ?: 0
        val seq = (maxSeq + 1).toString().padStart(6, '0')
        return "$prefix$seq"
    }

    /**
     * Abbreviates a name or category to ~3 uppercase chars.
     * Multi-word → initials (up to 3). Single word → first 3 consonants.
     */
    private fun abbreviateWord(value: String): String {
        val words = value.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return if (words.size > 1) {
            // Multi-word: take first letter of each word, up to 3
            words.take(3).map { it.first().uppercaseChar() }.joinToString("")
                .padEnd(2, 'X')
        } else {
            // Single word: first 3 consonants, skip vowels
            val consonants = words[0].uppercase()
                .filter { it.isLetter() && it !in "AEIOU" }
            consonants.take(3).padEnd(3, 'X')
        }
    }

    /**
     * Abbreviates a metal rate name.
     * Keeps leading karat prefix (digits + "K"), then appends first letter of each remaining word.
     * e.g. "18K Saudi Gold" → "18KSG", "Sterling Silver" → "SS"
     */
    private fun abbreviateRate(value: String): String {
        val upper = value.trim().uppercase()
        val karatMatch = Regex("^(\\d+K)").find(upper)
        return if (karatMatch != null) {
            val prefix = karatMatch.value                         // "18K"
            val rest = upper.removePrefix(prefix).trim()
            val initials = rest.split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .map { it.first() }
                .joinToString("")
            "$prefix$initials"
        } else {
            // No karat prefix — treat like a regular word abbreviation
            abbreviateWord(value)
        }
    }
}
