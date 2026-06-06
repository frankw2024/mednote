package com.mednote.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mednote.data.VisitStore

@Composable
fun VisitDetailScreen(visitId: String, visitStore: VisitStore) {
    val visits by visitStore.visits.collectAsState()
    val visit = visits.find { it.id == visitId } ?: return
    var isPlaying by remember { mutableStateOf(false) }
    val player = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                if (player.isPlaying) player.stop()
                player.release()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(visit.doctor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("${visit.date} · ${visit.time} · ${visit.duration}", style = MaterialTheme.typography.bodySmall)

        if (visit.isProcessing) {
            Surface(color = Color(0x1AF59E0B), shape = RoundedCornerShape(12.dp)) {
                RowWithProgress("AI is analyzing this visit…")
            }
        }

        visitStore.audioFile(visit)?.let {
            Button(onClick = {
                if (isPlaying) {
                    player.pause()
                    isPlaying = false
                } else {
                    runCatching {
                        player.reset()
                        player.setDataSource(it.absolutePath)
                        player.prepare()
                        player.start()
                        isPlaying = true
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(if (isPlaying) "Pause audio" else "Play audio")
            }
        }

        visit.summary?.let { summary ->
            SectionCard("AI Summary", summary)
        }

        val transcript = visit.translatedTranscript ?: visit.originalTranscript
        if (transcript.isNotEmpty()) {
            SectionCard(
                if (visit.translatedTranscript != null) "Translated Transcript" else "Transcript",
                transcript.joinToString("\n\n")
            )
        }

        if (visit.extractedItems.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(14.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Extracted Items", fontWeight = FontWeight.Bold)
                    visit.extractedItems.forEach { item ->
                        Text("• ${item.text}", style = MaterialTheme.typography.bodyMedium)
                        if (item.time.isNotEmpty()) {
                            Text(item.time, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, body: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body)
        }
    }
}

@Composable
private fun RowWithProgress(text: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        Text(text)
    }
}
