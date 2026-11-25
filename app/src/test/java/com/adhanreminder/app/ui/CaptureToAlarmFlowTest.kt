package com.adhanreminder.app.ui

import android.app.AlarmManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.adhanreminder.app.AdhanApplication
import com.adhanreminder.app.MainActivity
import com.adhanreminder.app.data.BuiltInTimetable
import com.adhanreminder.app.data.GeminiClient
import com.adhanreminder.app.data.StreamAttemptRunner
import com.adhanreminder.app.data.local.PrayerKey
import com.adhanreminder.app.data.local.ScheduleStatus
import com.adhanreminder.app.scheduling.SchedulingEngine
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CaptureToAlarmFlowTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var app: AdhanApplication

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        // Pin scheduling evaluation to 2026-11-25 04:00 so Fajr (05:37) and Dhuhr (12:09)
        // on 2026-11-25 are in the future and get scheduled in Room + AlarmManager.
        SchedulingEngine.nowProvider = { LocalDateTime.of(2026, 11, 25, 4, 0) }
        runBlocking {
            app.repository.deleteAllData()
            app.settings.clearUserData()
        }
        val mockJson = buildSafirahMockJson()
        app.geminiProvider = {
            GeminiClient(StreamAttemptRunner { _, _, _, _ -> mockJson })
        }
    }

    @After
    fun tearDown() {
        SchedulingEngine.nowProvider = { LocalDateTime.now(it) }
        app.geminiProvider = { GeminiClient() }
        runBlocking {
            app.repository.deleteAllData()
            app.settings.clearUserData()
        }
    }

    @Test
    fun fullCaptureToReviewEditAndAlarmSchedulingFlow() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // 1. Home screen appears with the seeded Safirah timetable and capture CTA
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag("home_capture_button").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(BuiltInTimetable.LABEL).assertIsDisplayed()

            // 2. Tap "صوّر الجدول"
            composeRule.onNodeWithTag("home_capture_button")
                .performScrollTo()
                .performClick()

            // 3. Capture screen appears -> trigger extraction with mock extractor via sample timetable
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag("capture_sample_timetable_button").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("capture_sample_timetable_button")
                .performScrollTo()
                .performClick()

            // 4. Review screen appears with extracted Safirah days (including 2026-11-25)
            composeRule.waitUntil(timeoutMillis = 15_000) {
                composeRule.onAllNodesWithTag("review_approve_button").fetchSemanticsNodes().isNotEmpty() &&
                    composeRule.onAllNodesWithTag("review_cell_2026-11-25_fajr").fetchSemanticsNodes().isNotEmpty()
            }

            composeRule.onNodeWithTag("review_cell_2026-11-25_fajr").performScrollTo()
            composeRule.onNodeWithTag("review_cell_2026-11-25_fajr").assertIsDisplayed()
            composeRule.onNodeWithTag("review_cell_2026-11-25_dhuhr").performScrollTo()
            composeRule.onNodeWithTag("review_cell_2026-11-25_dhuhr").assertIsDisplayed()

            // 5. Edit one prayer time (2026-11-25 Isha: 18:43 -> 19:43)
            composeRule.onNodeWithTag("review_cell_2026-11-25_isha")
                .performScrollTo()
                .performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag("time_edit_save_button").fetchSemanticsNodes().isNotEmpty()
            }

            // Select hour 19 on the 24-hour Material3 TimePicker dial (keeping minute 43)
            selectHourOnTimePicker("19")
            composeRule.onNodeWithTag("time_edit_save_button").performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("19:43").fetchSemanticsNodes().isNotEmpty()
            }

            // 6. Confirm approval & save
            composeRule.onNodeWithTag("review_approve_button")
                .performScrollTo()
                .performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag("review_conflict_keep_latest_button").fetchSemanticsNodes().isNotEmpty() ||
                    composeRule.onAllNodesWithTag("review_confirm_approve_button").fetchSemanticsNodes().isNotEmpty()
            }

            if (composeRule.onAllNodesWithTag("review_conflict_keep_latest_button").fetchSemanticsNodes().isNotEmpty()) {
                composeRule.onNodeWithTag("review_conflict_keep_latest_button").performClick()
            } else {
                composeRule.onNodeWithTag("review_confirm_approve_button").performClick()
            }

            // 7. Wait for navigation to Schedule screen after saving and scheduling
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag("schedule_back_button").fetchSemanticsNodes().isNotEmpty()
            }

            // 8. Verify Room database state & scheduled alarms (10 minutes before each prayer)
            runBlocking {
                val approved = app.repository.getLatestApprovedSchedule()
                assertNotNull("Expected an approved schedule in database", approved)
                approved!!
                assertEquals(ScheduleStatus.APPROVED, approved.status)
                assertEquals("جمادى الآخرة 1448", approved.hijriMonthLabel)

                val savedDays = app.repository.getPrayerDays(approved.id)
                assertEquals(29, savedDays.size)
                val nov25 = savedDays.first { it.gregorianDate == "2026-11-25" }
                assertEquals("05:47", nov25.fajr)
                assertEquals("12:19", nov25.dhuhr)
                assertEquals("14:59", nov25.asr)
                assertEquals("17:20", nov25.maghrib)
                assertEquals("19:43", nov25.isha)

                val alarms = app.repository.getAlarms(approved.id)
                val nov25Alarms = alarms.filter { it.gregorianDate == "2026-11-25" }
                assertEquals("Only the 5 prayers (not Sunrise) should be scheduled", 5, nov25Alarms.size)

                val zone = ZoneId.systemDefault()
                fun alarmLocalTime(key: PrayerKey): LocalTime {
                    val row = nov25Alarms.first { it.prayerKey == key }
                    return Instant.ofEpochMilli(row.triggerAtMillis).atZone(zone).toLocalTime()
                }

                assertEquals(LocalTime.of(5, 37), alarmLocalTime(PrayerKey.FAJR))
                assertEquals(LocalTime.of(12, 9), alarmLocalTime(PrayerKey.DHUHR))
                assertEquals(LocalTime.of(14, 49), alarmLocalTime(PrayerKey.ASR))
                assertEquals(LocalTime.of(17, 10), alarmLocalTime(PrayerKey.MAGHRIB))
                assertEquals(LocalTime.of(19, 33), alarmLocalTime(PrayerKey.ISHA))

                val alarmManager = app.getSystemService(AlarmManager::class.java)
                @Suppress("DEPRECATION")
                val scheduledTriggers = Shadows.shadowOf(alarmManager).scheduledAlarms.map { it.triggerAtTime }
                val fajrTrigger = nov25Alarms.first { it.prayerKey == PrayerKey.FAJR }.triggerAtMillis
                val dhuhrTrigger = nov25Alarms.first { it.prayerKey == PrayerKey.DHUHR }.triggerAtMillis
                val ishaTrigger = nov25Alarms.first { it.prayerKey == PrayerKey.ISHA }.triggerAtMillis
                assertTrue(
                    "AlarmManager should contain 2026-11-25 Fajr alarm at 05:37",
                    fajrTrigger in scheduledTriggers,
                )
                assertTrue(
                    "AlarmManager should contain 2026-11-25 Dhuhr alarm at 12:09",
                    dhuhrTrigger in scheduledTriggers,
                )
                assertTrue(
                    "AlarmManager should contain edited 2026-11-25 Isha alarm at 19:33",
                    ishaTrigger in scheduledTriggers,
                )
            }
        }
    }

    private fun selectHourOnTimePicker(hourText: String) {
        val inDialogByDesc = composeRule.onAllNodes(
            hasAnyAncestor(isDialog()) and hasContentDescription(hourText, substring = true),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        if (inDialogByDesc.isNotEmpty()) {
            composeRule.onAllNodes(
                hasAnyAncestor(isDialog()) and hasContentDescription(hourText, substring = true),
                useUnmergedTree = true,
            )[0].performClick()
            return
        }
        val inDialogByText = composeRule.onAllNodes(
            hasAnyAncestor(isDialog()) and hasText(hourText),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        if (inDialogByText.isNotEmpty()) {
            composeRule.onAllNodes(
                hasAnyAncestor(isDialog()) and hasText(hourText),
                useUnmergedTree = true,
            )[0].performTouchInput { click() }
        }
    }

    private fun buildSafirahMockJson(): String {
        val daysArray = JSONArray()
        BuiltInTimetable.days.forEach { day ->
            daysArray.put(
                JSONObject()
                    .put("gregorianDate", day.gregorianDate)
                    .put("hijriDay", day.hijriDay)
                    .put("fajr", JSONObject().put("time", day.fajr).put("period", "AM"))
                    .put("dhuhr", JSONObject().put("time", day.dhuhr).put("period", "PM"))
                    .put("asr", JSONObject().put("time", day.asr).put("period", "PM"))
                    .put("maghrib", JSONObject().put("time", day.maghrib).put("period", "PM"))
                    .put("isha", JSONObject().put("time", day.isha).put("period", "PM")),
            )
        }
        return JSONObject()
            .put("error", JSONObject.NULL)
            .put("hijriMonthName", "جمادى الآخرة")
            .put("hijriYear", 1448)
            .put("fajrNoteMinutes", JSONObject.NULL)
            .put("days", daysArray)
            .put("flags", JSONArray())
            .toString()
    }
}
