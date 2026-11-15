package com.adhanreminder.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledAlarmDao {
    @Query("SELECT * FROM scheduled_alarms WHERE scheduleId = :scheduleId ORDER BY triggerAtMillis ASC")
    fun observeForSchedule(scheduleId: Long): Flow<List<ScheduledAlarm>>

    @Query("SELECT * FROM scheduled_alarms WHERE id = :alarmId")
    fun observeById(alarmId: Long): Flow<ScheduledAlarm?>

    @Query("SELECT * FROM scheduled_alarms WHERE id = :alarmId")
    suspend fun getById(alarmId: Long): ScheduledAlarm?

    @Query("SELECT * FROM scheduled_alarms WHERE scheduleId = :scheduleId AND gregorianDate = :gregorianDate")
    fun observeForDate(scheduleId: Long, gregorianDate: String): Flow<List<ScheduledAlarm>>

    @Query("SELECT * FROM scheduled_alarms WHERE scheduleId = :scheduleId AND fired = 0")
    suspend fun getPendingForSchedule(scheduleId: Long): List<ScheduledAlarm>

    @Query("SELECT * FROM scheduled_alarms WHERE scheduleId = :scheduleId")
    suspend fun getAllForSchedule(scheduleId: Long): List<ScheduledAlarm>

    @Query("SELECT * FROM scheduled_alarms")
    suspend fun getAll(): List<ScheduledAlarm>

    @Query("SELECT * FROM scheduled_alarms WHERE requestCode = :requestCode LIMIT 1")
    suspend fun getByRequestCode(requestCode: Int): ScheduledAlarm?

    @Insert
    suspend fun insert(alarm: ScheduledAlarm): Long

    @Insert
    suspend fun insertAll(alarms: List<ScheduledAlarm>)

    @Update
    suspend fun update(alarm: ScheduledAlarm)

    @Query("UPDATE scheduled_alarms SET fired = :fired WHERE id = :alarmId")
    suspend fun setFired(alarmId: Long, fired: Boolean)

    @Query("DELETE FROM scheduled_alarms WHERE scheduleId = :scheduleId")
    suspend fun deleteForSchedule(scheduleId: Long)

    @Query("DELETE FROM scheduled_alarms WHERE scheduleId = :scheduleId AND gregorianDate = :gregorianDate")
    suspend fun deleteForDate(scheduleId: Long, gregorianDate: String)

    @Query("DELETE FROM scheduled_alarms")
    suspend fun deleteAll()
}
