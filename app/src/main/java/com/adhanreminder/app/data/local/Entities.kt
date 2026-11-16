package com.adhanreminder.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "month_schedules")
data class MonthSchedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hijriMonthLabel: String,
    val createdAtMillis: Long,
    val status: ScheduleStatus = ScheduleStatus.DRAFT,
    val sourceImagePath: String? = null,
)

@Entity(
    tableName = "prayer_days",
    foreignKeys = [
        ForeignKey(
            entity = MonthSchedule::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["scheduleId"]),
        Index(value = ["gregorianDate"]),
    ],
)
data class PrayerDay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long,
    val gregorianDate: String,
    val hijriDay: Int? = null,
    val fajr: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val reviewFlagsJson: String = "[]",
)

@Entity(
    tableName = "scheduled_alarms",
    foreignKeys = [
        ForeignKey(
            entity = MonthSchedule::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["scheduleId"]),
        Index(value = ["gregorianDate"]),
        Index(value = ["scheduleId", "requestCode"], unique = true),
    ],
)
data class ScheduledAlarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long,
    val prayerKey: PrayerKey,
    val gregorianDate: String,
    val triggerAtMillis: Long,
    val requestCode: Int,
    val fired: Boolean = false,
)

enum class ScheduleStatus { DRAFT, APPROVED, ARCHIVED }

enum class PrayerKey { FAJR, DHUHR, ASR, MAGHRIB, ISHA }
