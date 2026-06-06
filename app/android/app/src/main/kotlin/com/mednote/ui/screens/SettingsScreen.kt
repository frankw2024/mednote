package com.mednote.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mednote.data.SettingsStore
import com.mednote.models.AppLanguage

@Composable
fun SettingsScreen(settingsStore: SettingsStore) {
    val context = LocalContext.current
    val groq by settingsStore.groqKey.collectAsState()
    val gemini by settingsStore.geminiKey.collectAsState()
    val phone by settingsStore.doctorPhone.collectAsState()
    val language by settingsStore.language.collectAsState()
    val recordLanguage by settingsStore.recordLanguage.collectAsState()

    var groqInput by remember(groq) { mutableStateOf(groq) }
    var geminiInput by remember(gemini) { mutableStateOf(gemini) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("API keys are stored encrypted on device.", style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(
            value = groqInput,
            onValueChange = {
                groqInput = it
                settingsStore.setGroqKey(it)
            },
            label = { Text("Groq API key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            singleLine = true
        )
        OutlinedTextField(
            value = geminiInput,
            onValueChange = {
                geminiInput = it
                settingsStore.setGeminiKey(it)
            },
            label = { Text("Gemini API key (optional)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Text("Language", fontWeight = FontWeight.SemiBold)
        AppLanguage.entries.forEach { lang ->
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { settingsStore.setLanguage(lang) }) {
                    Text(if (language == lang) "✓ ${lang.label}" else lang.label)
                }
            }
        }

        Text("Doctor speaks", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        AppLanguage.entries.forEach { lang ->
            TextButton(onClick = { settingsStore.setRecordLanguage(lang) }) {
                Text(if (recordLanguage == lang) "✓ ${lang.label}" else lang.label)
            }
        }

        OutlinedTextField(
            value = phone,
            onValueChange = settingsStore::setDoctorPhone,
            label = { Text("Default doctor phone") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        StatusRow("Transcription", settingsStore.hasTranscriptionKey())
        StatusRow("AI summaries", settingsStore.hasSummaryKey())

        TextButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com")))
        }) { Text("Get free Groq key") }
        TextButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com")))
        }) { Text("Get Gemini key") }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Text(
            if (ok) "Ready" else "Needs key",
            color = if (ok) Color(0xFF16A34A) else Color(0xFFD97706)
        )
    }
}
