package com.mednote.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mednote.data.SettingsStore
import com.mednote.data.VisitStore
import com.mednote.models.CallVisitPhase
import com.mednote.service.AudioRecorderService
import com.mednote.service.CallService
import com.mednote.service.VisitPipeline
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CallScreen(
    settingsStore: SettingsStore,
    visitStore: VisitStore,
    audioRecorder: AudioRecorderService,
    visitPipeline: VisitPipeline,
    onVisitReady: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val phone by settingsStore.doctorPhone.collectAsState()
    val step by visitPipeline.step.collectAsState()
    var phase by remember { mutableStateOf(CallVisitPhase.IDLE) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var alert by remember { mutableStateOf<String?>(null) }
    var pendingStart by remember { mutableStateOf(false) }

    fun startCallFlow() {
        scope.launch {
            runCatching {
                val file = audioRecorder.startRecording(visitStore.audioDirectory)
                recordingFile = file
                phase = CallVisitPhase.RECORDING
                delay(800)
                val normalized = CallService.normalizedPhone(phone)!!
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$normalized")))
                phase = CallVisitPhase.ON_CALL
            }.onFailure {
                alert = it.message
                phase = CallVisitPhase.IDLE
            }
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingStart) {
            pendingStart = false
            startCallFlow()
        } else if (!granted) {
            pendingStart = false
            alert = "Microphone permission is required."
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            phase = CallVisitPhase.PROCESSING
            val temp = File(context.cacheDir, "import_${System.currentTimeMillis()}.m4a")
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            visitPipeline.processImportedFile(temp, visitStore, settingsStore)?.let { onVisitReady(it.id) }
            phase = CallVisitPhase.IDLE
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Phone Visit", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Surface(color = Color(0x1AF59E0B), shape = RoundedCornerShape(14.dp)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("One-tap call workflow", fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                Text(
                    "1. MedNote starts recording\n2. Phone dialer opens\n3. Use speakerphone\n4. Return and tap End & Transcribe",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        OutlinedTextField(
            value = phone,
            onValueChange = settingsStore::setDoctorPhone,
            label = { Text("Doctor's phone number") },
            placeholder = { Text("+1 555 123 4567") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        when (phase) {
            CallVisitPhase.IDLE -> {
                Button(
                    onClick = {
                        if (CallService.normalizedPhone(phone) == null) {
                            alert = "Enter a valid phone number."
                            return@Button
                        }
                        pendingStart = true
                        micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) { Text("Call & Record") }
            }
            CallVisitPhase.RECORDING, CallVisitPhase.ON_CALL -> {
                Surface(color = Color(0x1416A34A), shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Recording · ${formatTime(audioRecorder.elapsedSeconds)}", fontWeight = FontWeight.SemiBold, color = Color(0xFF16A34A))
                        }
                        Text("Use speakerphone. When done, tap below.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            val normalized = CallService.normalizedPhone(phone)
                            if (normalized != null) {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$normalized")))
                            }
                        }) { Text("Redial") }
                    }
                }
                Button(
                    onClick = {
                        scope.launch {
                            phase = CallVisitPhase.PROCESSING
                            val file = audioRecorder.stopRecording() ?: recordingFile
                            recordingFile = null
                            if (file == null) {
                                alert = "No recording found."
                                phase = CallVisitPhase.IDLE
                                return@launch
                            }
                            visitPipeline.processRecording(
                                file, audioRecorder.elapsedSeconds, visitStore, settingsStore
                            )?.let { onVisitReady(it.id) }
                            phase = CallVisitPhase.IDLE
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("End Call & Transcribe") }
            }
            CallVisitPhase.PROCESSING -> {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
        }

        OutlinedButton(onClick = { importLauncher.launch("audio/*") }, modifier = Modifier.fillMaxWidth()) {
            Text("Import audio / Voice Recorder file")
        }

        step?.let {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                Text(it.label)
            }
        }

        alert?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

private fun formatTime(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
