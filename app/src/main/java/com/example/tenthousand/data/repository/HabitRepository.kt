package com.example.tenthousand.data.repository
import HabitEntity
import com.example.tenthousand.data.local.dao.HabitDao
import kotlinx.coroutines.flow.Flow

class HabitRepository(private val dao: HabitDao)
{
    fun observeAll(): Flow<List<HabitEntity>> = dao.observeAll()
    fun observeById(id: Long): Flow<HabitEntity?> = dao.observeById(id)

    suspend fun createHabit(name: String): Long
    {
        val entity = HabitEntity(name = name)
        return dao.insert(entity)
    }

    suspend fun renameHabit(id: Long, newName: String)
    {
        val current = dao.observeById(id) // can't call Flow directly - see usage note below
        // In practice: read current with a suspend query or pass full entity to update.
    }

    suspend fun update(habit: HabitEntity) = dao.update(habit)

    suspend fun delete(habit: HabitEntity) = dao.delete(habit)

    suspend fun addSeconds(id: Long, seconds: Long) = dao.addSeconds(id, seconds)
}
