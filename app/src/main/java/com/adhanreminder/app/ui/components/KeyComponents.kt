package com.adhanreminder.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adhanreminder.app.ui.theme.AdhanDimensions
import com.adhanreminder.app.ui.theme.PlexMono

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.height(AdhanDimensions.buttonHeight), shape = RoundedCornerShape(AdhanDimensions.fieldRadius), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) { Text(text) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.height(AdhanDimensions.buttonHeight), shape = RoundedCornerShape(AdhanDimensions.fieldRadius), border = BorderStroke(AdhanDimensions.border, MaterialTheme.colorScheme.outline)) { Text(text) }
}

@Composable
fun LowPriorityButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) { TextButton(onClick = onClick, modifier = modifier) { Text(text) } }

@Composable
fun KeyCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(AdhanDimensions.cardRadius), border = BorderStroke(AdhanDimensions.border, MaterialTheme.colorScheme.outline), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) { Column(Modifier.padding(AdhanDimensions.s16), verticalArrangement = Arrangement.spacedBy(AdhanDimensions.s12)) { content() } }
}

@Composable
fun StatusRow(icon: String, title: String, detail: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().semantics { contentDescription = "$icon $title${detail?.let { ": $it" }.orEmpty()}" }, horizontalArrangement = Arrangement.spacedBy(AdhanDimensions.s12)) {
        Text(icon)
        Column { Text(title); if (!detail.isNullOrBlank()) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun ReasonCode(code: String) { Text(code, fontFamily = PlexMono, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { contentDescription = "رمز الخطأ $code" }) }
