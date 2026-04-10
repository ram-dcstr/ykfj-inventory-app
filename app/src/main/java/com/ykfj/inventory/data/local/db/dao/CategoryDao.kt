package com.ykfj.inventory.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ykfj.inventory.data.local.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE is_deleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE category_id = :categoryId AND is_deleted = 0 LIMIT 1")
    suspend fun getById(categoryId: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("UPDATE categories SET is_deleted = 1, updated_at = :now WHERE category_id = :categoryId")
    suspend fun softDelete(categoryId: String, now: Long)

    @Query("SELECT * FROM categories WHERE updated_at > :since")
    suspend fun getChangedSince(since: Long): List<CategoryEntity>
}
