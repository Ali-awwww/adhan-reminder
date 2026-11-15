package com.adhanreminder.app.domain

import com.adhanreminder.app.data.local.PrayerKey
import java.time.LocalDate
import java.time.LocalDateTime

sealed class ExtractionFailure(message: String) : Exception(message) {
    data object NotAPrayerTimetable : ExtractionFailure("not_a_prayer_timetable")
    data class MalformedJson(val detail: String) : ExtractionFailure("JSON غير صالح: $detail")
    data class InvalidTime(val value: String) : ExtractionFailure("وقت غير صالح: $value")
    data class MissingField(val field: String) : ExtractionFailure("حقل مفقود: $field")
    data class InvalidDayCount(val count: Int) : ExtractionFailure("عدد أيام غير صالح: $count")
}

data class RawTime(val time: String?, val period: String?)
data class RawPrayerDay(
    val gregorianDate: String,
    val hijriDay: Int?,
    val fajr: RawTime,
    val dhuhr: RawTime,
    val asr: RawTime,
    val maghrib: RawTime,
    val isha: RawTime,
)
data class ParsedTimetable(
    val hijriMonthName: String?,
    val hijriYear: Int?,
    val fajrNoteMinutes: Int?,
    val days: List<RawPrayerDay>,
    val flags: List<ReviewFlag>,
)
data class ReviewFlag(val gregorianDate: String, val field: String, val reason: String)

data class PrayerTimes24(
    val fajr: String?, val dhuhr: String?, val asr: String?, val maghrib: String?, val isha: String?,
)
data class ValidatedDay(
    val date: LocalDate?,
    val gregorianDate: String,
    val hijriDay: Int?,
    val times: PrayerTimes24,
    val flags: MutableList<ReviewFlag>,
)
data class ValidationSummary(val totalDays: Int, val flaggedCells: Int, val isReady: Boolean)
data class ValidatedTimetable(val days: List<ValidatedDay>, val summary: ValidationSummary, val fajrNoteMinutes: Int?)

data class AlarmPlan(
    val gregorianDate: LocalDate,
    val prayerKey: PrayerKey,
    val adhanTime: java.time.LocalTime,
    val triggerDateTime: LocalDateTime,
    val triggerAtMillis: Long,
)
data class AlarmCalculation(val plans: List<AlarmPlan>, val missingPrayerKeys: List<PrayerKey>)
