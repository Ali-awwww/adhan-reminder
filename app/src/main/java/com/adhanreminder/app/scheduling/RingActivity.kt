package com.adhanreminder.app.scheduling

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adhanreminder.app.ui.theme.PlexArabic
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val prayerKey = intent.getStringExtra(AlarmReceiver.EXTRA_PRAYER_KEY).orEmpty()
        val writtenTime = intent.getStringExtra(AlarmReceiver.EXTRA_ADHAN_TIME).orEmpty().ifBlank { "--:--" }
        val prayerName = AlarmReceiver.prayerName(prayerKey)
        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(prayerName, color = Color.White, fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = 44.sp)
                    Text("الأذان الساعة $writtenTime", color = Color.White, fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = 24.sp, modifier = Modifier.padding(top = 12.dp))
                    Button(
                        onClick = { stopAndClose() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        modifier = Modifier.padding(top = 48.dp).semantics { contentDescription = "تم، إيقاف المنبه" },
                    ) { Text("تم", fontFamily = PlexArabic, fontSize = 20.sp) }
                    LaunchedEffect(Unit) { delay(10 * 60 * 1000L); stopAndClose() }
                }
            }
        }
    }

    private fun stopAndClose() {
        getSystemService(android.app.NotificationManager::class.java).cancelAll()
        finishAndRemoveTask()
    }
}
