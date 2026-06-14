package com.ykfj.inventory.util

import com.ykfj.inventory.data.local.db.dao.ProductDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The product ID `{NAME}-{RATE}-{CAT}-{seq}` is customer-visible and used as a
 * primary key, so the abbreviation rules and the per-prefix sequence have to be
 * stable. These lock the documented examples plus the edge cases.
 */
class ProductIdGeneratorTest {

    private lateinit var productDao: ProductDao
    private lateinit var generator: ProductIdGenerator

    @Before
    fun setUp() {
        productDao = mockk()
        generator = ProductIdGenerator(productDao)
    }

    @Test
    fun `documented example - Monaco 18K Saudi Gold Necklace`() = runTest {
        coEvery { productDao.maxSequenceForPrefix("MNC-18KSG-NCK-") } returns null
        assertEquals(
            "MNC-18KSG-NCK-000001",
            generator.generate("Monaco", "18K Saudi Gold", "Necklace"),
        )
    }

    @Test
    fun `documented example - Cuban 18K Yellow Gold Bracelet continues the sequence`() = runTest {
        coEvery { productDao.maxSequenceForPrefix("CBN-18KYG-BRC-") } returns 1
        assertEquals(
            "CBN-18KYG-BRC-000002",
            generator.generate("Cuban", "18K Yellow Gold", "Bracelet"),
        )
    }

    @Test
    fun `fixed-price item with no metal rate uses FXD`() = runTest {
        coEvery { productDao.maxSequenceForPrefix("BNG-FXD-BNG-") } returns null
        assertEquals("BNG-FXD-BNG-000001", generator.generate("Bangle", null, "Bangle"))
    }

    @Test
    fun `sequence continues from the existing max for the prefix`() = runTest {
        coEvery { productDao.maxSequenceForPrefix("MNC-FXD-RNG-") } returns 41
        assertEquals("MNC-FXD-RNG-000042", generator.generate("Monaco", null, "Ring"))
    }

    @Test
    fun `short single-word abbreviation is padded with X`() = runTest {
        // "Bee" -> consonants "B" -> padded "BXX"; "Ring" -> "RNG".
        coEvery { productDao.maxSequenceForPrefix("BXX-FXD-RNG-") } returns null
        assertEquals("BXX-FXD-RNG-000001", generator.generate("Bee", null, "Ring"))
    }

    @Test
    fun `multi-word name uses each word's initial`() = runTest {
        coEvery { productDao.maxSequenceForPrefix("RS-FXD-RNG-") } returns null
        assertEquals("RS-FXD-RNG-000001", generator.generate("Red Sea", null, "Ring"))
    }

    @Test
    fun `sequence is scoped to the computed prefix`() = runTest {
        coEvery { productDao.maxSequenceForPrefix(any()) } returns null
        generator.generate("Monaco", "18K Saudi Gold", "Necklace")
        coVerify(exactly = 1) { productDao.maxSequenceForPrefix("MNC-18KSG-NCK-") }
    }
}
