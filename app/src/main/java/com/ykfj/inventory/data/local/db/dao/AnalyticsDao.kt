package com.ykfj.inventory.data.local.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalyticsDao {

    data class SalesMetricsRow(
        val revenue: Double,
        val capital: Double,
        @ColumnInfo(name = "item_count") val itemCount: Int,
    )

    data class CategorySalesRow(
        @ColumnInfo(name = "category_id") val categoryId: String,
        @ColumnInfo(name = "category_name") val categoryName: String,
        val count: Int,
    )

    @Query(
        """
        SELECT COALESCE(SUM(sold_price * quantity), 0.0) AS revenue,
               COALESCE(SUM(capital_price * quantity), 0.0) AS capital,
               COUNT(sold_id) AS item_count
        FROM sold_records
        WHERE is_deleted = 0 AND sold_date BETWEEN :start AND :end
        """,
    )
    fun observeSalesMetrics(start: Long, end: Long): Flow<SalesMetricsRow>

    @Query(
        """
        SELECT p.category_id, c.name AS category_name, COUNT(sr.sold_id) AS count
        FROM sold_records sr
        JOIN products p ON p.product_id = sr.product_id
        JOIN categories c ON c.category_id = p.category_id
        WHERE sr.is_deleted = 0 AND sr.sold_date BETWEEN :start AND :end
        GROUP BY p.category_id
        ORDER BY count DESC
        LIMIT 5
        """,
    )
    fun observeTopCategories(start: Long, end: Long): Flow<List<CategorySalesRow>>

    @Query(
        """
        SELECT COUNT(*) FROM products
        WHERE is_deleted = 0 AND status != 'SOLD'
        """,
    )
    fun observeActiveProductCount(): Flow<Int>

    @Query(
        """
        SELECT COALESCE(SUM(capital_price * quantity), 0.0)
        FROM products
        WHERE is_deleted = 0 AND status != 'SOLD'
        """,
    )
    fun observeInventoryCapital(): Flow<Double>

    @Query(
        """
        SELECT COALESCE(SUM((unit_price * quantity) - total_paid), 0.0)
        FROM layaway_records
        WHERE is_deleted = 0 AND status = 'ACTIVE'
        """,
    )
    fun observeLayawayOutstanding(): Flow<Double>

    @Query(
        """
        SELECT COUNT(*) FROM paluwagan_groups
        WHERE is_deleted = 0 AND is_archived = 0 AND status = 'ACTIVE'
        """,
    )
    fun observeActivePaluwaganCount(): Flow<Int>

    @Query(
        """
        SELECT COALESCE(SUM(pp.amount_paid), 0.0)
        FROM paluwagan_payments pp
        JOIN paluwagan_groups pg ON pg.group_id = pp.group_id
        WHERE pp.is_deleted = 0 AND pp.status IN ('PAID', 'PREPAID')
          AND pg.is_deleted = 0 AND pg.status = 'ACTIVE'
        """,
    )
    fun observePaluwaganCollected(): Flow<Double>
}
