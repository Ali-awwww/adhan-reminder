package com.adhanreminder.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MonthScheduleDao {
    @Query("SELECT * FROM month_schedules ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<MonthSchedule>>

    @Query("SELECT * FROM month_schedules WHERE id = :scheduleId")
    fun observeById(scheduleId: Long): Flow<MonthSchedule?>

    @Query("SELECT * FROM month_schedules WHERE id = :scheduleId")
    suspend fun getById(scheduleId: Long): MonthSchedule?

    @Query("SELECT * FROM month_schedules WHERE status = 'APPROVED' ORDER BY createdAtMillis DESC LIMIT 1")
    suspend fun getLatestApproved(): MonthSchedule?

    @Query("SELECT * FROM month_schedules WHERE status = 'APPROVED'")
    suspend fun getAllApproved(): List<MonthSchedule>

    @Query("UPDATE month_schedules SET status = 'ARCHIVED' WHERE status = 'APPROVED'")
    suspend fun demoteAllApproved()

    @Insert
    suspend fun insert(schedule: MonthSchedule): Long

    @Update
    suspend fun update(schedule: MonthSchedule)

    @Delete
    suspend fun delete(schedule: MonthSchedule)

    @Query("DELETE FROM month_schedules")
    suspend fun deleteAll()
}
