package com.adhanreminder.app.domain

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.DecimalStyle
import java.util.Locale

object ExtractionParser {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withDecimalStyle(DecimalStyle.STANDARD)

    fun parse(raw: String): Result<ParsedTimetable> = runCatching {
        val root = JSONObject(raw)
        if (root.optString("error") == "not_a_prayer_timetable") throw ExtractionFailure.NotAPrayerTimetable
        val daysJson = root.optJSONArray("days") ?: throw ExtractionFailure.MissingField("days")
        if (daysJson.length() !in 1..31) throw ExtractionFailure.InvalidDayCount(daysJson.length())
        val flags = parseFlags(root.optJSONArray("flags"))
        val days = buildList {
            for (i in 0 until daysJson.length()) {
                val item = daysJson.optJSONObject(i) ?: throw ExtractionFailure.MalformedJson("days[$i]")
                val date = normalizeDigits(item.optString("gregorianDate"))
                if (date.isBlank()) throw ExtractionFailure.MissingField("gregorianDate")
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

    fun parseTime(time: String?, period: String?): Result<String?> = runCatching {
        if (time == null || time == "null" || time.isBlank()) return@runCatching null
        val normalized = normalizeDigits(time.trim())
        val match = Regex("^(\\d{2}):(\\d{2})$").matchEntire(normalized)
            ?: throw ExtractionFailure.InvalidTime(time)
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        require(hour in 1..12 && minute in 0..59) { throw ExtractionFailure.InvalidTime(time) }
        when (period?.uppercase()) {
            "AM" -> if (hour == 12) hour = 0
            "PM" -> if (hour != 12) hour += 12
            null, "" -> require(hour in 0..23) { throw ExtractionFailure.InvalidTime(time) }
            else -> throw ExtractionFailure.InvalidTime("$time $period")
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
            add(ReviewFlag(obj.optString("gregorianDate"), obj.optString("field"), obj.optString("reason")))
        }
    }

    private fun JSONObject.optNullableString(name: String): String? = if (!has(name) || isNull(name)) null else optString(name).takeUnless { it.isBlank() }
    private fun JSONObject.optNullableInt(name: String): Int? = if (isNull(name) || !has(name)) null else optInt(name)
}
