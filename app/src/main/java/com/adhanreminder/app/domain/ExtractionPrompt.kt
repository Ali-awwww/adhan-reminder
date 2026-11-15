package com.adhanreminder.app.domain

object ExtractionPrompt {
    val SYSTEM_INSTRUCTION = """
You read one page of a monthly Islamic prayer timetable printed in Arabic. Return ONLY valid JSON that matches the schema. No markdown and no commentary.
- Each row is one Gregorian day. Read the Gregorian date from the column titled الميلادي, in yyyy-MM-dd form. Read the Hijri day number from the column titled هـ.
- Read the prayer columns by their Arabic headers, not by position: الفجر، الظهر، العصر، المغرب، العشاء. Ignore الشروق completely.
- Copy each time exactly as printed in HH:mm. Copy its period marker: ص becomes AM and م becomes PM. Do not convert to 24-hour format yourself.
- If the footer states that the first Fajr adhan comes a fixed number of minutes before the printed Fajr time, put that number in fajrNoteMinutes. Otherwise use null. Do not compute any reminder time.
- If a value is unreadable, blurred, cut off, or inconsistent, set it to null and add an entry to flags with the day, the field, and a short Arabic reason.
- Never guess. An accurate null with a flag is better than a plausible number.
- If the image is not a prayer timetable, return {"error":"not_a_prayer_timetable"}.
""".trimIndent()

    val RESPONSE_SCHEMA = """
{"type":"object","properties":{"hijriMonthName":{"type":["string","null"]},"hijriYear":{"type":["integer","null"]},"fajrNoteMinutes":{"type":["integer","null"]},"days":{"type":"array","items":{"type":"object","properties":{"gregorianDate":{"type":"string"},"hijriDay":{"type":["integer","null"]},"fajr":{"type":"object"},"dhuhr":{"type":"object"},"asr":{"type":"object"},"maghrib":{"type":"object"},"isha":{"type":"object"}}}},"flags":{"type":"array"}}}
""".trimIndent()

    // Sample first row: 2025-06-26, hijriDay 1, fajr 03:26 AM, dhuhr 12:35 PM,
    // asr 04:25 PM, maghrib 07:55 PM, isha 09:36 PM, fajrNoteMinutes 20.
}
