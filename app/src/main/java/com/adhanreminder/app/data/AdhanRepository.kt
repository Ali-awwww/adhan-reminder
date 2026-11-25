package com.adhanreminder.app.data

import com.adhanreminder.app.data.local.MonthSchedule
import com.adhanreminder.app.data.local.MonthScheduleDao
import com.adhanreminder.app.data.local.PrayerDay
import com.adhanreminder.app.data.local.PrayerDayDao
import com.adhanreminder.app.data.local.ScheduledAlarm
import com.adhanreminder.app.data.local.ScheduledAlarmDao
import kotlinx.coroutines.flow.Flow

class AdhanRepository(
    private val monthScheduleDao: MonthScheduleDao,
    private val prayerDayDao: PrayerDayDao,
    private val scheduledAlarmDao: ScheduledAlarmDao,
) {
    fun observeSchedules(): Flow<List<MonthSchedule>> = monthScheduleDao.observeAll()
    fun observeSchedule(scheduleId: Long): Flow<MonthSchedule?> = monthScheduleDao.observeById(scheduleId)
    suspend fun getScheduleCount(): Int = monthScheduleDao.count()
    suspend fun getSchedule(scheduleId: Long): MonthSchedule? = monthScheduleDao.getById(scheduleId)
    suspend fun getLatestApprovedSchedule(): MonthSchedule? = monthScheduleDao.getLatestApproved()
    suspend fun demoteAllApprovedSchedules() = monthScheduleDao.demoteAllApproved()
    suspend fun insertSchedule(schedule: MonthSchedule): Long = monthScheduleDao.insert(schedule)
    suspend fun updateSchedule(schedule: MonthSchedule) = monthScheduleDao.update(schedule)
    suspend fun deleteSchedule(schedule: MonthSchedule) = monthScheduleDao.delete(schedule)

    fun observePrayerDays(scheduleId: Long): Flow<List<PrayerDay>> = prayerDayDao.observeForSchedule(scheduleId)
    fun observePrayerDay(dayId: Long): Flow<PrayerDay?> = prayerDayDao.observeById(dayId)
    suspend fun getPrayerDay(scheduleId: Long, date: String): PrayerDay? = prayerDayDao.getByDate(scheduleId, date)
    suspend fun insertPrayerDay(day: PrayerDay): Long = prayerDayDao.insert(day)
    suspend fun insertPrayerDays(days: List<PrayerDay>) = prayerDayDao.insertAll(days)
    suspend fun updatePrayerDay(day: PrayerDay) = prayerDayDao.update(day)
    suspend fun deletePrayerDays(scheduleId: Long) = prayerDayDao.deleteForSchedule(scheduleId)
    suspend fun getPrayerDays(scheduleId: Long): List<PrayerDay> = prayerDayDao.getAllForSchedule(scheduleId)

    fun observeAlarms(scheduleId: Long): Flow<List<ScheduledAlarm>> = scheduledAlarmDao.observeForSchedule(scheduleId)
    fun observeAlarm(alarmId: Long): Flow<ScheduledAlarm?> = scheduledAlarmDao.observeById(alarmId)
    suspend fun getAlarm(alarmId: Long): ScheduledAlarm? = scheduledAlarmDao.getById(alarmId)
    fun observeAlarmsForDate(scheduleId: Long, date: String): Flow<List<ScheduledAlarm>> = scheduledAlarmDao.observeForDate(scheduleId, date)
    suspend fun getPendingAlarms(scheduleId: Long): List<ScheduledAlarm> = scheduledAlarmDao.getPendingForSchedule(scheduleId)
    suspend fun getAlarms(scheduleId: Long): List<ScheduledAlarm> = scheduledAlarmDao.getAllForSchedule(scheduleId)
    suspend fun getAllAlarms(): List<ScheduledAlarm> = scheduledAlarmDao.getAll()
    suspend fun getAlarmByRequestCode(requestCode: Int): ScheduledAlarm? = scheduledAlarmDao.getByRequestCode(requestCode)
    suspend fun insertAlarm(alarm: ScheduledAlarm): Long = scheduledAlarmDao.insert(alarm)
    suspend fun insertAlarms(alarms: List<ScheduledAlarm>) = scheduledAlarmDao.insertAll(alarms)
    suspend fun updateAlarm(alarm: ScheduledAlarm) = scheduledAlarmDao.update(alarm)
    suspend fun setAlarmFired(alarmId: Long, fired: Boolean) = scheduledAlarmDao.setFired(alarmId, fired)
    suspend fun deleteAlarms(scheduleId: Long) = scheduledAlarmDao.deleteForSchedule(scheduleId)
    suspend fun deleteAlarmsForDate(scheduleId: Long, date: String) = scheduledAlarmDao.deleteForDate(scheduleId, date)
    suspend fun deleteAllAlarms() = scheduledAlarmDao.deleteAll()

    suspend fun deleteAllData() {
        scheduledAlarmDao.deleteAll()
        prayerDayDao.deleteAll()
        monthScheduleDao.deleteAll()
    }
}
