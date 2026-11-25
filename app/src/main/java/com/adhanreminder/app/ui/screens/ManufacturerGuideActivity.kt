package com.adhanreminder.app.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.adhanreminder.app.device.ManufacturerMapping
import com.adhanreminder.app.ui.theme.AdhanDimensions
import com.adhanreminder.app.ui.theme.AdhanReminderTheme

class ManufacturerGuideActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AdhanReminderTheme(darkTheme = false) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val guide = ManufacturerMapping.guideFor(android.os.Build.MANUFACTURER)
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text("إرشادات ${guide.titleAr}", style = MaterialTheme.typography.titleLarge)
                            guide.stepsAr.forEach {
                                Text("• $it", style = MaterialTheme.typography.bodyLarge)
                            }
                            Button(
                                onClick = { ManufacturerMapping.open(this@ManufacturerGuideActivity, guide) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = AdhanDimensions.buttonHeight),
                                shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
                            ) {
                                Text("فتح شاشة الإعداد", style = MaterialTheme.typography.labelLarge)
                            }
                            OutlinedButton(
                                onClick = { finish() },
                                modifier = Modifier.fillMaxWidth().heightIn(min = AdhanDimensions.buttonHeight),
                                shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
                            ) {
                                Text("رجوع", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}
