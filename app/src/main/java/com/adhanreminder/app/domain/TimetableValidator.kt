package com.adhanreminder.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object TimetableValidator {
    const val MAX_DAY_TO_DAY_CHANGE_MINUTES = 5
    val ADVISORY_FIELDS = setOf("month", "date")

    fun isAdvisoryFlag(flag: ReviewFlag): Boolean = flag.field in ADVISORY_FIELDS

    fun isMandatoryFlag(flag: ReviewFlag): Boolean = flag.field !in ADVISORY_FIELDS

    fun hasValidPrayerOrder(times: PrayerTimes24): Boolean {
        val values = listOf(times.fajr, times.dhuhr, times.asr, times.maghrib, times.isha)
        if (values.any { it.isNullOrBlank() }) return true
        val minutes = values.mapNotNull { runCatching { it!!.toMinutes() }.getOrNull() }
        if (minutes.size != 5) return false
        return !minutes.zipWithNext().any { it.first >= it.second }
    }

    fun validate(parsed: ParsedTimetable): ValidatedTimetable {
        val modelFlags = parsed.flags
        val days = parsed.days.map { raw ->
            val date = runCatching { LocalDate.parse(raw.gregorianDate) }.getOrNull()
            val flags = modelFlags.filter { it.gregorianDate == raw.gregorianDate }.toMutableList()
            val times = PrayerTimes24(
                parseCell(raw.gregorianDate, "fajr", raw.fajr, flags),
                parseCell(raw.gregorianDate, "dhuhr", raw.dhuhr, flags),
                parseCell(raw.gregorianDate, "asr", raw.asr, flags),
                parseCell(raw.gregorianDate, "maghrib", raw.maghrib, flags),
                parseCell(raw.gregorianDate, "isha", raw.isha, flags),
            )
            validateOrder(raw.gregorianDate, times, flags)
            ValidatedDay(date, raw.gregorianDate, raw.hijriDay, times, flags.distinct().toMutableList())
        }
        if (days.size !in 28..31) {
            days.forEach { it.flags.add(ReviewFlag(it.gregorianDate, "month", "عدد أيام الجدول (${days.size}) خارج نطاق الشهر الكامل (28–31)")) }
        }
        days.zipWithNext().forEach { (previous, current) ->
            val datesAreConsecutive = previous.date != null && current.date != null && ChronoUnit.DAYS.between(previous.date, current.date) == 1L
            if (!datesAreConsecutive) {
                current.flags.add(ReviewFlag(current.gregorianDate, "date", "تواريخ الأيام ليست متتالية"))
            }
            compareDay(previous, current)
        }
        val flagged = days.sumOf { it.flags.size }
        return ValidatedTimetable(days, ValidationSummary(days.size, flagged, flagged == 0), parsed.fajrNoteMinutes)
    }

    private fun parseCell(date: String, field: String, value: RawTime, flags: MutableList<ReviewFlag>): String? {
        val result = ExtractionParser.parseTime(value.time, value.period)
        if (result.isFailure) {
            flags.add(ReviewFlag(date, field, if (value.time == null) "الخانة فارغة" else "تعذّر قراءة الوقت"))
            return null
        }
        val parsed = result.getOrNull()
        if (parsed == null) flags.add(ReviewFlag(date, field, "الخانة فارغة"))
        return parsed
    }

    private fun validateOrder(date: String, times: PrayerTimes24, flags: MutableList<ReviewFlag>) {
        val values = listOf(times.fajr, times.dhuhr, times.asr, times.maghrib, times.isha)
        if (values.any { it == null }) return
        if (!hasValidPrayerOrder(times)) {
            flags.add(ReviewFlag(date, "day", "ترتيب أوقات الصلاة داخل اليوم غير منطقي"))
        }
    }

    private fun compareDay(previous: ValidatedDay, current: ValidatedDay) {
        val pairs = listOf(
            "fajr" to (previous.times.fajr to current.times.fajr),
            "dhuhr" to (previous.times.dhuhr to current.times.dhuhr),
            "asr" to (previous.times.asr to current.times.asr),
            "maghrib" to (previous.times.maghrib to current.times.maghrib),
            "isha" to (previous.times.isha to current.times.isha),
        )
        pairs.forEach { (field, pair) ->
            if (pair.first != null && pair.second != null) {
                val difference = kotlin.math.abs(pair.first!!.toMinutes() - pair.second!!.toMinutes())
                if (difference > MAX_DAY_TO_DAY_CHANGE_MINUTES) {
                    current.flags.add(ReviewFlag(current.gregorianDate, field, "تغير الوقت عن اليوم السابق أكبر من 5 دقائق"))
                }
            }
        }
    }

    private fun String.toMinutes(): Int {
        val parts = split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }
}
