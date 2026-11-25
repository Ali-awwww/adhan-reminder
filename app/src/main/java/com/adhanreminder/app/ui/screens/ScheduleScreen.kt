package com.adhanreminder.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.adhanreminder.app.AdhanApplication
import com.adhanreminder.app.R
import com.adhanreminder.app.data.local.ScheduleStatus
import com.adhanreminder.app.scheduling.SchedulingEngine
import com.adhanreminder.app.ui.theme.AdhanDimensions
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun ScheduleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as AdhanApplication
    val schedules by app.repository.observeSchedules().collectAsState(initial = emptyList())
    val schedule = schedules.firstOrNull { it.status == ScheduleStatus.APPROVED }
    val days = if (schedule == null) emptyList() else app.repository.observePrayerDays(schedule.id).collectAsState(initial = emptyList()).value
    val offset by app.settings.reminderOffsetMinutes.collectAsState(initial = 10)
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<EditTarget?>(null) }
    val todayString = remember { LocalDate.now().toString() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AdhanDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("الجدول", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 52.dp).testTag("schedule_back_button"),
                shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
            ) {
                Icon(painter = painterResource(R.drawable.ic_arrow_back_rtl), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("رجوع", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (schedule == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AdhanDimensions.cardRadius),
                border = BorderStroke(AdhanDimensions.border, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Text(
                    "لا يوجد جدول معتمد. صوّر الجدول أو استعد الجدول المدمج من الرئيسية.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(20.dp),
                )
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AdhanDimensions.cardRadius),
                border = BorderStroke(AdhanDimensions.border, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        schedule.hijriMonthLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        "${days.size} يوماً · التنبيه قبل الأذان بـ $offset دقائق",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            OutlinedButton(
                onClick = { editing = !editing },
                modifier = Modifier.fillMaxWidth().heightIn(min = AdhanDimensions.buttonHeight).testTag("schedule_edit_toggle_button"),
                shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (editing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Text(
                    if (editing) "إنهاء التعديل" else "تعديل أوقات الجدول",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            days.forEach { day ->
                DayCard(
                    day = day,
                    reminderOffset = offset,
                    editable = editing,
                    isToday = day.gregorianDate == todayString,
                ) { field -> selected = EditTarget(day, field) }
            }
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().heightIn(min = AdhanDimensions.buttonHeight),
            shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
        ) {
            Text("العودة للرئيسية", style = MaterialTheme.typography.labelLarge)
        }
    }

    selected?.let { target ->
        TimeEditDialog(target, onDismiss = { selected = null }) { updated ->
            scope.launch {
                app.repository.updatePrayerDay(updated)
                SchedulingEngine(context, app.repository, app.settings).rescheduleDay(updated.gregorianDate)
                selected = null
            }
        }
    }
}
