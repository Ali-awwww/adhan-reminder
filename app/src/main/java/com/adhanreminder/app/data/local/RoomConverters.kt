package com.adhanreminder.app.data.local

import androidx.room.TypeConverter

class RoomConverters {
    @TypeConverter
    fun scheduleStatusToString(value: ScheduleStatus): String = value.name

    @TypeConverter
    fun stringToScheduleStatus(value: String): ScheduleStatus = ScheduleStatus.valueOf(value)

    @TypeConverter
    fun prayerKeyToString(value: PrayerKey): String = value.name

    @TypeConverter
    fun stringToPrayerKey(value: String): PrayerKey = PrayerKey.valueOf(value)
}
