package com.mednote.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mednote.data.SettingsStore
import com.mednote.data.VisitStore

@Composable
fun HomeScreen(
    visitStore: VisitStore,
    settingsStore: SettingsStore,
    onOpenCall: () -> Unit,
    onOpenRecord: () -> Unit,
    onOpenVisit: (String) -> Unit
) {
    val visits by visitStore.visits.collectAsState()
    val groq by settingsStore.groqKey.collectAsState()
    val gemini by settingsStore.geminiKey.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("🩺 MedNote", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Record doctor calls & visits, then AI transcribes and summarizes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip("Groq", groq.isNotBlank())
            StatusChip("AI", groq.isNotBlank() || gemini.isNotBlank())
        }
        ActionCard(Icons.Default.Phone, Color(0xFF16A34A), "Call & Record", "Dial doctor, record with speakerphone", onOpenCall)
        ActionCard(Icons.Default.Mic, MaterialTheme.colorScheme.primary, "In-Person Visit", "Record at the clinic", onOpenRecord)
        if (visits.isEmpty()) {
            Text(
                "No visits yet — use Call or Record to get started.",
                modifier = Modifier.padding(top = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text("Recent", fontWeight = FontWeight.SemiBold)
            visits.take(3).forEach { visit ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenVisit(visit.id) },
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(visit.doctor, fontWeight = FontWeight.SemiBold)
                        Text("${visit.date} · ${visit.duration}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, ok: Boolean) {
    Surface(
        color = if (ok) Color(0x2616A34A) else Color(0x26F59E0B),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = if (ok) "$label ✓" else "$label —",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (ok) Color(0xFF16A34A) else Color(0xFFD97706)
        )
    }
}

@Composable
private fun ActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(color = tint.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
