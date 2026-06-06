package com.mednote.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mednote.data.SettingsStore
import com.mednote.data.VisitStore
import com.mednote.service.AudioRecorderService
import com.mednote.service.VisitPipeline
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun RecordScreen(
    settingsStore: SettingsStore,
    visitStore: VisitStore,
    audioRecorder: AudioRecorderService,
    visitPipeline: VisitPipeline,
    onVisitReady: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val step by visitPipeline.step.collectAsState()
    var isActive by remember { mutableStateOf(false) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var alert by remember { mutableStateOf<String?>(null) }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            runCatching {
                recordingFile = audioRecorder.startRecording(visitStore.audioDirectory)
                isActive = true
            }.onFailure { alert = it.message }
        } else {
            alert = "Microphone permission is required."
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .background(
                    if (isActive) Color(0x26DC2626) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    CircleShape
                )
                .clickable {
                    if (isActive) {
                        scope.launch {
                            isActive = false
                            val file = audioRecorder.stopRecording() ?: recordingFile
                            recordingFile = null
                            if (file != null) {
                                visitPipeline.processRecording(
                                    file, audioRecorder.elapsedSeconds, visitStore, settingsStore, "Clinic Visit"
                                )?.let { onVisitReady(it.id) }
                            }
                        }
                    } else {
                        micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(if (isActive) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isActive) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            if (isActive) formatTime(audioRecorder.elapsedSeconds) else "Tap to record",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            if (isActive) "Recording in-person visit…" else "Record at the clinic",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        step?.let {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
            Text(it.label, modifier = Modifier.padding(top = 8.dp))
        }
        alert?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun formatTime(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
