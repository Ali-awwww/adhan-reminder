package com.adhanreminder.app.domain

object ExtractionPrompt {
    val SYSTEM_INSTRUCTION = """
You read one page of a printed Arabic Islamic prayer timetable (such as the Syrian Ministry of Endowments monthly timetable with columns right-to-left: هـ، اليوم، الميلادي، الفجر، الشروق، الظهر، العصر، المغرب، العشاء). Return ONLY valid JSON that matches the schema. No markdown and no commentary.
- Always populate `days` with one entry per row (صف لكل يوم) when the image shows a prayer timetable.
- Read the Hijri month name (e.g. جمادى الآخرة) into `hijriMonthName` and Hijri year (e.g. 1448) into `hijriYear` from the header if present.
- Read the Hijri day integer from the «هـ» column into `hijriDay` (e.g. 1..30), otherwise null. Ignore the weekday name column («اليوم» such as الأربعاء، الخميس).
- Read the Gregorian date from the «الميلادي» column (often printed as yyyy/MM/dd such as 2026/11/11, or yyyy/M/d) and convert it to yyyy-MM-dd (e.g. 2026-11-11).
- Read the five prayer columns by their Arabic headers: الفجر (fajr), الظهر (dhuhr), العصر (asr), المغرب (maghrib), العشاء (isha).
- CRITICAL: The table usually includes a «الشروق» (sunrise) column between الفجر and الظهر. Ignore «الشروق» completely! NEVER put الشروق into dhuhr or shift any column, and NEVER create an alarm or prayer entry for الشروق.
- Normalize Arabic-Indic digits (٠١٢٣٤٥٦٧٨٩) to Western digits (0-9).
- Copy each prayer time literally as HH:mm (or H:mm). Times may include ص or م (e.g. 05:35 ص, 12:16 م, 03:05 م): set period to "AM" for ص and "PM" for م. If the table uses 24-hour format without ص/م (hours >= 13), keep the 24-hour time and set period to null.
- Set `fajrNoteMinutes` to null. Never subtract or compute any reminder offset or Fajr gap from the printed times.
- If an individual prayer cell is unreadable, blurred, cut off, or missing, set that cell's time to null and add an entry to `flags` with the day's gregorianDate, the field (fajr, dhuhr, asr, maghrib, isha), and a short Arabic reason.
- Never guess an unreadable cell value.
- ONLY if the image is clearly NOT a prayer timetable at all, set `error` to "not_a_prayer_timetable" and return `days: []`, `flags: []`. For any real prayer timetable, set `error` to null and extract all rows into `days`.
""".trimIndent()

    val PLAIN_JSON_INSTRUCTION = """
$SYSTEM_INSTRUCTION

Return ONLY a single raw JSON object (no markdown fences, no commentary) using this exact structure:
{
  "error": null,
  "hijriMonthName": "string or null",
  "hijriYear": 1448,
  "fajrNoteMinutes": null,
  "days": [
    {
      "gregorianDate": "yyyy-MM-dd",
      "hijriDay": 1,
      "fajr": {"time": "HH:mm", "period": "AM or PM or null"},
      "dhuhr": {"time": "HH:mm", "period": "AM or PM or null"},
      "asr": {"time": "HH:mm", "period": "AM or PM or null"},
      "maghrib": {"time": "HH:mm", "period": "AM or PM or null"},
      "isha": {"time": "HH:mm", "period": "AM or PM or null"}
    }
  ],
  "flags": [
    {"gregorianDate": "yyyy-MM-dd", "field": "fajr", "reason": "سبب قصير"}
  ]
}
""".trimIndent()
}
