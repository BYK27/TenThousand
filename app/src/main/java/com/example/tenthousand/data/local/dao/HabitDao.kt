package com.example.tenthousand.data.local.dao

import HabitEntity
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao
{
    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun observeByIdOnce(id: Long): HabitEntity?
    @Query("SELECT * FROM habits ORDER BY id DESC")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<HabitEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Delete
    suspend fun delete(habit: HabitEntity)

    @Query("UPDATE habits SET totalSeconds = totalSeconds + :seconds WHERE id = :id")
    suspend fun addSeconds(id: Long, seconds: Long)
}
