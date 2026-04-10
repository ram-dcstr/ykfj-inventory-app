package com.ykfj.inventory.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ykfj.inventory.data.local.db.entity.ProductImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductImageDao {

    @Query("SELECT * FROM product_images WHERE product_id = :productId AND is_deleted = 0 LIMIT 1")
    fun observeByProduct(productId: String): Flow<ProductImageEntity?>

    @Query("SELECT * FROM product_images WHERE product_id = :productId AND is_deleted = 0 LIMIT 1")
    suspend fun getByProduct(productId: String): ProductImageEntity?

    @Query("SELECT * FROM product_images WHERE image_id = :imageId AND is_deleted = 0 LIMIT 1")
    suspend fun getById(imageId: String): ProductImageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(image: ProductImageEntity)

    @Update
    suspend fun update(image: ProductImageEntity)

    @Query("UPDATE product_images SET is_deleted = 1, updated_at = :now WHERE image_id = :imageId")
    suspend fun softDelete(imageId: String, now: Long)

    @Query("SELECT * FROM product_images WHERE updated_at > :since")
    suspend fun getChangedSince(since: Long): List<ProductImageEntity>
}
