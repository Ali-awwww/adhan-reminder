package com.adhanreminder.app.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adhanreminder.app.device.ManufacturerMapping
import com.adhanreminder.app.ui.theme.AdhanReminderTheme

class ManufacturerGuideActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AdhanReminderTheme {
                val guide = ManufacturerMapping.guideFor(android.os.Build.MANUFACTURER)
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("إرشادات ${guide.titleAr}")
                    guide.stepsAr.forEach { Text("• $it") }
                    Button(onClick = { ManufacturerMapping.open(this@ManufacturerGuideActivity, guide) }) { Text("فتح شاشة الإعداد") }
                    Button(onClick = { finish() }) { Text("رجوع") }
                }
            }
        }
    }
}
