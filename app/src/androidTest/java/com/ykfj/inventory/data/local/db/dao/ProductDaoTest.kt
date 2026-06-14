package com.ykfj.inventory.data.local.db.dao

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ykfj.inventory.data.local.db.YkfjDatabase
import com.ykfj.inventory.data.local.db.entity.CategoryEntity
import com.ykfj.inventory.data.local.db.entity.ProductEntity
import com.ykfj.inventory.data.local.db.enums.PricingType
import com.ykfj.inventory.data.local.db.enums.ProductStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductDaoTest {

    private lateinit var db: YkfjDatabase
    private lateinit var productDao: ProductDao
    private lateinit var categoryDao: CategoryDao

    private val now = System.currentTimeMillis()

    private fun product(
        id: String,
        name: String = "Cuban Chain",
        categoryId: String = "cat-1",
        status: ProductStatus = ProductStatus.AVAILABLE,
        dateAcquired: Long = now,
        notes: String? = null,
        isDeleted: Boolean = false,
    ) = ProductEntity(
        product_id = id,
        name = name,
        category_id = categoryId,
        metal_rate_id = null,
        supplier_id = null,
        date_acquired = dateAcquired,
        pricing_type = PricingType.FIXED,
        capital_price = 100.0,
        selling_price = 150.0,
        weight_grams = null,
        size = null,
        quantity = 1,
        notes = notes,
        status = status,
        created_at = now,
        updated_at = now,
        is_deleted = isDeleted,
    )

    private suspend fun PagingSource<Int, ProductEntity>.loadAll(): List<ProductEntity> {
        val result = load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 100,
                placeholdersEnabled = false,
            ),
        )
        return (result as PagingSource.LoadResult.Page).data
    }

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, YkfjDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        productDao = db.productDao()
        categoryDao = db.categoryDao()
        categoryDao.insert(
            CategoryEntity(
                category_id = "cat-1",
                name = "Necklace",
                created_at = now,
                updated_at = now,
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndGetById() = runTest {
        productDao.insert(product("p1"))
        val result = productDao.getById("p1")
        assertNotNull(result)
        assertEquals("Cuban Chain", result!!.name)
    }

    @Test
    fun pagingAvailable_excludesSoldAndDeleted() = runTest {
        productDao.insert(product("p1", status = ProductStatus.AVAILABLE))
        productDao.insert(product("p2", status = ProductStatus.SOLD))
        productDao.insert(product("p3", isDeleted = true))

        val page = productDao.pagingAvailable().loadAll()
        assertEquals(1, page.size)
        assertEquals("p1", page[0].product_id)
    }

    @Test
    fun pagingAvailable_ordersByDateAcquiredDesc() = runTest {
        productDao.insert(product("old", dateAcquired = 1_000L))
        productDao.insert(product("new", dateAcquired = 2_000L))
        productDao.insert(product("newest", dateAcquired = 3_000L))

        val page = productDao.pagingAvailable().loadAll()
        assertEquals(listOf("newest", "new", "old"), page.map { it.product_id })
    }

    @Test
    fun ftsSearch_findsByName() = runTest {
        productDao.insert(product("p1", name = "Gold Cuban Chain"))
        productDao.insert(product("p2", name = "Silver Bracelet"))

        val results = productDao.searchPaging("Cuban*").loadAll()
        assertEquals(1, results.size)
        assertEquals("p1", results[0].product_id)
    }

    @Test
    fun ftsSearch_findsByNotes() = runTest {
        productDao.insert(product("p1", name = "Chain", notes = "special edition"))
        productDao.insert(product("p2", name = "Ring", notes = "plain"))

        val results = productDao.searchPaging("special*").loadAll()
        assertEquals(1, results.size)
        assertEquals("p1", results[0].product_id)
    }

    @Test
    fun ftsSearch_excludesDeleted() = runTest {
        productDao.insert(product("p1", name = "Cuban Chain"))
        productDao.insert(product("p2", name = "Cuban Ring", isDeleted = true))

        val results = productDao.searchPaging("Cuban*").loadAll()
        assertEquals(1, results.size)
        assertEquals("p1", results[0].product_id)
    }

    @Test
    fun pagingByStatus_filtersCorrectly() = runTest {
        productDao.insert(product("p1", status = ProductStatus.AVAILABLE))
        productDao.insert(product("p2", status = ProductStatus.LAYAWAY))
        productDao.insert(product("p3", status = ProductStatus.LAYAWAY))

        val results = productDao.pagingByStatus(ProductStatus.LAYAWAY).loadAll()
        assertEquals(2, results.size)
        assertTrue(results.all { it.status == ProductStatus.LAYAWAY })
    }

    @Test
    fun maxSequenceForPrefix_returnsHighestSuffix_includingDeleted() = runTest {
        productDao.insert(product("PYX-FXD-NCK-000001"))
        productDao.insert(product("PYX-FXD-NCK-000004", isDeleted = true)) // still occupies its slot
        productDao.insert(product("RPX-FXD-NCK-000001")) // different prefix, ignored

        // Highest suffix for the prefix is 4, even though that row is soft-deleted —
        // so the next ID will be 000005 and won't collide.
        assertEquals(4, productDao.maxSequenceForPrefix("PYX-FXD-NCK-"))
        // No product uses this prefix yet → null (generator treats as 0 → first ID 000001).
        assertEquals(null, productDao.maxSequenceForPrefix("ZZZ-FXD-NCK-"))
    }

    @Test
    fun updateQuantityAndStatus_updatesBothFields() = runTest {
        productDao.insert(product("p1"))
        productDao.updateQuantityAndStatus("p1", 0, ProductStatus.SOLD, now + 100)

        val updated = productDao.getById("p1")!!
        assertEquals(0, updated.quantity)
        assertEquals(ProductStatus.SOLD, updated.status)
    }
}
