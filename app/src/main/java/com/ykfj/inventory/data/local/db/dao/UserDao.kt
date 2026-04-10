package com.ykfj.inventory.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ykfj.inventory.data.local.db.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE is_deleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE user_id = :userId AND is_deleted = 0 LIMIT 1")
    suspend fun getById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username AND is_deleted = 0 LIMIT 1")
    suspend fun getByUsername(username: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users WHERE is_deleted = 0")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity)

    @Update
    suspend fun update(user: UserEntity)

    @Query("UPDATE users SET is_deleted = 1, updated_at = :now WHERE user_id = :userId")
    suspend fun softDelete(userId: String, now: Long)

    @Query("SELECT * FROM users WHERE updated_at > :since")
    suspend fun getChangedSince(since: Long): List<UserEntity>
}
