package com.adhanreminder.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PrayerDayDao {
    @Query("SELECT * FROM prayer_days WHERE scheduleId = :scheduleId ORDER BY gregorianDate ASC")
    fun observeForSchedule(scheduleId: Long): Flow<List<PrayerDay>>

    @Query("SELECT * FROM prayer_days WHERE id = :dayId")
    fun observeById(dayId: Long): Flow<PrayerDay?>

    @Query("SELECT * FROM prayer_days WHERE scheduleId = :scheduleId AND gregorianDate = :gregorianDate")
    suspend fun getByDate(scheduleId: Long, gregorianDate: String): PrayerDay?

    @Query("SELECT * FROM prayer_days WHERE scheduleId = :scheduleId ORDER BY gregorianDate ASC")
    suspend fun getAllForSchedule(scheduleId: Long): List<PrayerDay>

    @Insert
    suspend fun insert(day: PrayerDay): Long

    @Insert
    suspend fun insertAll(days: List<PrayerDay>)

    @Update
    suspend fun update(day: PrayerDay)

    @Query("DELETE FROM prayer_days WHERE scheduleId = :scheduleId")
    suspend fun deleteForSchedule(scheduleId: Long)

    @Query("DELETE FROM prayer_days")
    suspend fun deleteAll()
}
