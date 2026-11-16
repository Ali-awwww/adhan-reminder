package com.adhanreminder.app.domain

object ExtractionPrompt {
    val SYSTEM_INSTRUCTION = """
You read one page of an Islamic prayer timetable printed in Arabic (monthly, half-month, weekly, or any grid/table layout). Return ONLY valid JSON that matches the schema. No markdown and no commentary.
- Always populate `days` with one entry per row when the image shows a prayer timetable. Read the Gregorian date if present (prefer yyyy-MM-dd, e.g. 2025-06-26; if printed as yyyy/M/d convert to yyyy-MM-dd). Read the Hijri day number from هـ if present, otherwise null.
- Read the prayer columns or labels by their Arabic names, not by fixed position: الفجر، الظهر، العصر، المغرب، العشاء. Ignore الشروق completely.
- Normalize Arabic-Indic digits (٠١٢٣٤٥٦٧٨٩) to Western digits (0-9).
- Copy each time as HH:mm (or H:mm). If the table uses 12-hour format with ص or م, set period to AM for ص and PM for م. If the table already uses 24-hour format (or has no AM/PM marker for afternoon/evening hours >= 13), keep the 24-hour time and set period to null.
- If a footer note states that the first Fajr adhan comes a fixed number of minutes before the printed Fajr time (e.g. بعشرين دقيقة = 20), put that integer in fajrNoteMinutes. Otherwise use null. Do not compute any reminder time.
- If an individual cell is unreadable, blurred, cut off, or missing, set that cell's time to null and add an entry to `flags` with the day's gregorianDate, the field (fajr, dhuhr, asr, maghrib, isha), and a short Arabic reason.
- Never guess an unreadable cell value.
- ONLY if the image is clearly NOT a prayer timetable at all, set `error` to "not_a_prayer_timetable" and return `days: []`, `flags: []`. For any real prayer timetable, set `error` to null and extract all rows into `days`.
""".trimIndent()

    val PLAIN_JSON_INSTRUCTION = """
$SYSTEM_INSTRUCTION

Return ONLY a single raw JSON object (no markdown fences, no commentary) using this exact structure:
{
  "error": null,
  "hijriMonthName": "string or null",
  "hijriYear": 1447,
  "fajrNoteMinutes": 20,
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
