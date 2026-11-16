package com.adhanreminder.app.domain

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale

object ExtractionParser {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withDecimalStyle(DecimalStyle.STANDARD)
    private val isoDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun parse(raw: String): Result<ParsedTimetable> = runCatching {
        val root = JSONObject(raw)
        if (root.optString("error") == "not_a_prayer_timetable") throw ExtractionFailure.NotAPrayerTimetable
        val daysJson = root.optJSONArray("days") ?: throw ExtractionFailure.MissingField("days")
        if (daysJson.length() !in 1..31) throw ExtractionFailure.InvalidDayCount(daysJson.length())
        val flags = parseFlags(root.optJSONArray("flags"))
        val days = buildList {
            for (i in 0 until daysJson.length()) {
                val item = daysJson.optJSONObject(i) ?: throw ExtractionFailure.MalformedJson("days[$i]")
                val rawDate = normalizeDigits(item.optString("gregorianDate"))
                if (rawDate.isBlank()) throw ExtractionFailure.MissingField("gregorianDate")
                val date = normalizeDate(rawDate)
                add(RawPrayerDay(
                    gregorianDate = date,
                    hijriDay = item.optNullableInt("hijriDay"),
                    fajr = parseRawTime(item, "fajr"),
                    dhuhr = parseRawTime(item, "dhuhr"),
                    asr = parseRawTime(item, "asr"),
                    maghrib = parseRawTime(item, "maghrib"),
                    isha = parseRawTime(item, "isha"),
                ))
            }
        }
        ParsedTimetable(
            hijriMonthName = root.optNullableString("hijriMonthName"),
            hijriYear = root.optNullableInt("hijriYear"),
            fajrNoteMinutes = root.optNullableInt("fajrNoteMinutes"),
            days = days,
            flags = flags,
        )
    }.recoverCatching { error ->
        when (error) {
            is ExtractionFailure -> throw error
            is JSONException -> throw ExtractionFailure.MalformedJson(error.message ?: "JSON")
            else -> throw ExtractionFailure.MalformedJson(error.message ?: error::class.simpleName.orEmpty())
        }
    }

    fun normalizeDate(raw: String): String {
        val cleaned = normalizeDigits(raw).trim()
        if (cleaned.isEmpty()) return cleaned
        runCatching { return LocalDate.parse(cleaned, isoDateFormatter).format(isoDateFormatter) }

        val ymd = Regex("^(\\d{4})[/\\-.](\\d{1,2})[/\\-.](\\d{1,2})$").matchEntire(cleaned)
        if (ymd != null) {
            val y = ymd.groupValues[1].toInt()
            val m = ymd.groupValues[2].toInt()
            val d = ymd.groupValues[3].toInt()
            runCatching { return LocalDate.of(y, m, d).format(isoDateFormatter) }
        }

        val dmy = Regex("^(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{4})$").matchEntire(cleaned)
        if (dmy != null) {
            val d = dmy.groupValues[1].toInt()
            val m = dmy.groupValues[2].toInt()
            val y = dmy.groupValues[3].toInt()
            runCatching { return LocalDate.of(y, m, d).format(isoDateFormatter) }
        }

        return cleaned
    }

    fun parseTime(time: String?, period: String?): Result<String?> = runCatching {
        if (time == null || time == "null" || time.isBlank()) return@runCatching null
        var normalized = normalizeDigits(time.trim())
        var resolvedPeriod = period?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

        when {
            normalized.endsWith("ص") -> {
                normalized = normalized.removeSuffix("ص").trim()
                if (resolvedPeriod == null) resolvedPeriod = "AM"
            }
            normalized.endsWith("م") -> {
                normalized = normalized.removeSuffix("م").trim()
                if (resolvedPeriod == null) resolvedPeriod = "PM"
            }
            normalized.uppercase(Locale.ROOT).endsWith("AM") -> {
                normalized = normalized.dropLast(2).trim()
                if (resolvedPeriod == null) resolvedPeriod = "AM"
            }
            normalized.uppercase(Locale.ROOT).endsWith("PM") -> {
                normalized = normalized.dropLast(2).trim()
                if (resolvedPeriod == null) resolvedPeriod = "PM"
            }
        }

        val match = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(normalized)
            ?: throw ExtractionFailure.InvalidTime(time)
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        require(minute in 0..59) { throw ExtractionFailure.InvalidTime(time) }

        when (resolvedPeriod?.uppercase(Locale.ROOT)) {
            "AM", "ص" -> {
                require(hour in 1..12 || hour == 0) { throw ExtractionFailure.InvalidTime(time) }
                if (hour == 12) hour = 0
            }
            "PM", "م" -> {
                if (hour in 13..23) {
                    // Already 24-hour afternoon/evening time paired with PM marker; accept directly.
                } else {
                    require(hour in 1..12) { throw ExtractionFailure.InvalidTime(time) }
                    if (hour != 12) hour += 12
                }
            }
            null -> {
                require(hour in 0..23) { throw ExtractionFailure.InvalidTime(time) }
            }
            else -> throw ExtractionFailure.InvalidTime("$time $resolvedPeriod")
        }
        LocalTime.of(hour, minute).format(timeFormatter)
    }

    fun normalizeDigits(value: String): String = value.map {
        when (it) {
            in '٠'..'٩' -> ('0'.code + (it.code - '٠'.code)).toChar()
            in '۰'..'۹' -> ('0'.code + (it.code - '۰'.code)).toChar()
            else -> it
        }
    }.joinToString("")

    private fun parseRawTime(day: JSONObject, key: String): RawTime {
        val obj = day.optJSONObject(key) ?: return RawTime(null, null)
        return RawTime(obj.optNullableString("time"), obj.optNullableString("period"))
    }

    private fun parseFlags(array: JSONArray?): List<ReviewFlag> = buildList {
        if (array == null) return@buildList
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            add(ReviewFlag(normalizeDate(obj.optString("gregorianDate")), obj.optString("field"), obj.optString("reason")))
        }
    }

    private fun JSONObject.optNullableString(name: String): String? = if (!has(name) || isNull(name)) null else optString(name).takeUnless { it.isBlank() }
    private fun JSONObject.optNullableInt(name: String): Int? = if (isNull(name) || !has(name)) null else optInt(name)
}
