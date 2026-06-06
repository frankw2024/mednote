package com.mednote.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mednote.data.VisitStore

@Composable
fun VisitsScreen(
    visitStore: VisitStore,
    onOpenVisit: (String) -> Unit
) {
    val visits by visitStore.visits.collectAsState()

    if (visits.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text("No visits yet", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text("Phone calls and recordings appear here after AI processing.")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            item {
                Text("Past Visits", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            items(visits, key = { it.id }) { visit ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { onOpenVisit(visit.id) },
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(visit.doctor, fontWeight = FontWeight.SemiBold)
                        Text("${visit.date} · ${visit.duration}", style = MaterialTheme.typography.bodySmall)
                        if (visit.isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
}
