package com.adhanreminder.app.domain

object ExtractionPrompt {
    val SYSTEM_INSTRUCTION = """
You read one page of an Islamic prayer timetable printed in Arabic (monthly, half-month, weekly, or any grid/table layout). Return ONLY valid JSON that matches the schema. No markdown and no commentary.
- Each row or entry represents one day. Read the Gregorian date if present (prefer yyyy-MM-dd, e.g. 2025-06-26; if printed as yyyy/M/d convert to yyyy-MM-dd). Read the Hijri day number from هـ if present, otherwise null.
- Read the prayer columns or labels by their Arabic names, not by fixed position: الفجر، الظهر، العصر، المغرب، العشاء. Ignore الشروق completely.
- Normalize Arabic-Indic digits (٠١٢٣٤٥٦٧٨٩) to Western digits (0-9).
- Copy each time as HH:mm (or H:mm). If the table uses 12-hour format with ص or م, set period to AM for ص and PM for م. If the table already uses 24-hour format (or has no AM/PM marker for afternoon/evening hours >= 13), keep the 24-hour time and set period to null.
- If a footer note states that the first Fajr adhan comes a fixed number of minutes before the printed Fajr time (e.g. بعشرين دقيقة = 20), put that integer in fajrNoteMinutes. Otherwise use null. Do not compute any reminder time.
- If a value is unreadable, blurred, cut off, or missing, set it to null and add an entry to flags with the day's gregorianDate, the field (fajr, dhuhr, asr, maghrib, isha), and a short Arabic reason.
- Never guess. An accurate null with a flag is better than a plausible number.
- If the image is clearly not a prayer timetable, return {"error":"not_a_prayer_timetable"}.
""".trimIndent()
}
