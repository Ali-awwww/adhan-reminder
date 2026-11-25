package com.adhanreminder.app.data

import android.content.Context
import com.adhanreminder.app.data.local.MonthSchedule
import com.adhanreminder.app.data.local.PrayerDay
import com.adhanreminder.app.data.local.ScheduleStatus
import com.adhanreminder.app.scheduling.SchedulingEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BuiltInDay(
    val gregorianDate: String,
    val hijriDay: Int,
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
)

/**
 * جدول وزارة الأوقاف لشهر جمادى الآخرة 1448 هـ — مواقيت منطقة السفيرة وما حولها
 * (29 يوماً من 2026-11-11 إلى 2026-12-09، بصيغة 24 ساعة).
 * الشروق محفوظ للعرض المرجعي فقط ولا يُجدول له منبه.
 */
object BuiltInTimetable {
    const val LABEL = "جدول السفيرة — جمادى الآخرة 1448"

    private val seedMutex = Mutex()

    val days: List<BuiltInDay> = listOf(
        BuiltInDay("2026-11-11", 1, "05:35", "07:03", "12:16", "15:05", "17:27", "18:49"),
        BuiltInDay("2026-11-12", 2, "05:36", "07:04", "12:16", "15:05", "17:27", "18:48"),
        BuiltInDay("2026-11-13", 3, "05:37", "07:05", "12:16", "15:04", "17:26", "18:47"),
        BuiltInDay("2026-11-14", 4, "05:38", "07:06", "12:16", "15:03", "17:25", "18:47"),
        BuiltInDay("2026-11-15", 5, "05:39", "07:07", "12:16", "15:03", "17:25", "18:46"),
        BuiltInDay("2026-11-16", 6, "05:39", "07:08", "12:16", "15:02", "17:24", "18:46"),
        BuiltInDay("2026-11-17", 7, "05:40", "07:09", "12:17", "15:02", "17:23", "18:45"),
        BuiltInDay("2026-11-18", 8, "05:41", "07:10", "12:17", "15:01", "17:23", "18:45"),
        BuiltInDay("2026-11-19", 9, "05:42", "07:11", "12:17", "15:01", "17:22", "18:45"),
        BuiltInDay("2026-11-20", 10, "05:43", "07:12", "12:17", "15:01", "17:22", "18:44"),
        BuiltInDay("2026-11-21", 11, "05:44", "07:13", "12:17", "15:00", "17:21", "18:44"),
        BuiltInDay("2026-11-22", 12, "05:45", "07:14", "12:18", "15:00", "17:21", "18:44"),
        BuiltInDay("2026-11-23", 13, "05:45", "07:15", "12:18", "15:00", "17:20", "18:43"),
        BuiltInDay("2026-11-24", 14, "05:46", "07:16", "12:18", "14:59", "17:20", "18:43"),
        BuiltInDay("2026-11-25", 15, "05:47", "07:17", "12:19", "14:59", "17:20", "18:43"),
        BuiltInDay("2026-11-26", 16, "05:48", "07:18", "12:19", "14:59", "17:19", "18:43"),
        BuiltInDay("2026-11-27", 17, "05:49", "07:19", "12:19", "14:59", "17:19", "18:42"),
        BuiltInDay("2026-11-28", 18, "05:50", "07:20", "12:20", "14:58", "17:19", "18:42"),
        BuiltInDay("2026-11-29", 19, "05:50", "07:21", "12:20", "14:58", "17:19", "18:42"),
        BuiltInDay("2026-11-30", 20, "05:51", "07:22", "12:20", "14:58", "17:18", "18:42"),
        BuiltInDay("2026-12-01", 21, "05:52", "07:23", "12:21", "14:58", "17:18", "18:42"),
        BuiltInDay("2026-12-02", 22, "05:53", "07:24", "12:21", "14:58", "17:18", "18:42"),
        BuiltInDay("2026-12-03", 23, "05:54", "07:25", "12:21", "14:58", "17:18", "18:42"),
        BuiltInDay("2026-12-04", 24, "05:54", "07:26", "12:22", "14:58", "17:18", "18:42"),
        BuiltInDay("2026-12-05", 25, "05:55", "07:26", "12:22", "14:58", "17:18", "18:42"),
        BuiltInDay("2026-12-06", 26, "05:56", "07:27", "12:23", "14:58", "17:18", "18:42"),
        BuiltInDay("2026-12-07", 27, "05:57", "07:28", "12:23", "14:58", "17:18", "18:42"),
        BuiltInDay("2026-12-08", 28, "05:57", "07:29", "12:24", "14:58", "17:18", "18:42"),
        BuiltInDay("2026-12-09", 29, "05:58", "07:30", "12:24", "14:58", "17:18", "18:43"),
    )

    private val sunriseByDate: Map<String, String> by lazy {
        days.associate { it.gregorianDate to it.sunrise }
    }

    fun sunriseForDate(gregorianDate: String): String? = sunriseByDate[gregorianDate]

    fun toPrayerDays(scheduleId: Long): List<PrayerDay> = days.map { day ->
        PrayerDay(
            scheduleId = scheduleId,
            gregorianDate = day.gregorianDate,
            hijriDay = day.hijriDay,
            fajr = day.fajr,
            dhuhr = day.dhuhr,
            asr = day.asr,
            maghrib = day.maghrib,
            isha = day.isha,
            reviewFlagsJson = "[]",
        )
    }

    suspend fun seedIfEmpty(repository: AdhanRepository): Boolean = seedMutex.withLock {
        if (repository.getScheduleCount() > 0) return@withLock false
        val scheduleId = repository.insertSchedule(
            MonthSchedule(
                hijriMonthLabel = LABEL,
                createdAtMillis = System.currentTimeMillis(),
                status = ScheduleStatus.APPROVED,
            ),
        )
        repository.insertPrayerDays(toPrayerDays(scheduleId))
        true
    }

    suspend fun restoreAsApproved(
        context: Context,
        repository: AdhanRepository,
        settings: SettingsRepository,
    ): Int = seedMutex.withLock {
        val scheduleId = repository.insertSchedule(
            MonthSchedule(
                hijriMonthLabel = LABEL,
                createdAtMillis = System.currentTimeMillis(),
                status = ScheduleStatus.DRAFT,
            ),
        )
        repository.insertPrayerDays(toPrayerDays(scheduleId))
        SchedulingEngine(context, repository, settings).approveSchedule(scheduleId)
    }
}
