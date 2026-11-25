package com.adhanreminder.app.scheduling

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adhanreminder.app.ui.theme.AdhanReminderTheme
import com.adhanreminder.app.ui.theme.PlexArabic
import kotlinx.coroutines.delay

class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val prayerKey = intent.getStringExtra(AlarmReceiver.EXTRA_PRAYER_KEY).orEmpty()
        val writtenTime = intent.getStringExtra(AlarmReceiver.EXTRA_ADHAN_TIME).orEmpty().ifBlank { "--:--" }
        val prayerName = AlarmReceiver.prayerName(prayerKey)
        setContent {
            AdhanReminderTheme(darkTheme = false) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "تنبيه قبل الأذان",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontFamily = PlexArabic,
                            fontWeight = FontWeight.Medium,
                            fontSize = 22.sp,
                        )
                        Text(
                            prayerName,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontFamily = PlexArabic,
                            fontWeight = FontWeight.Bold,
                            fontSize = 52.sp,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            "الأذان الساعة $writtenTime",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontFamily = PlexArabic,
                            fontWeight = FontWeight.Medium,
                            fontSize = 26.sp,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                        Button(
                            onClick = { stopAndClose() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .padding(top = 48.dp)
                                .fillMaxWidth()
                                .heightIn(min = 72.dp)
                                .semantics { contentDescription = "تم، إيقاف المنبه" },
                        ) {
                            Text(
                                "تم — إيقاف المنبه",
                                fontFamily = PlexArabic,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                            )
                        }
                        LaunchedEffect(Unit) {
                            delay(10 * 60 * 1000L)
                            stopAndClose()
                        }
                    }
                }
            }
        }
    }

    private fun stopAndClose() {
        getSystemService(android.app.NotificationManager::class.java).cancelAll()
        finishAndRemoveTask()
    }
}
