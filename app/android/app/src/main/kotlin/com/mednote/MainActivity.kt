package com.mednote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mednote.ui.MedNoteRoot
import com.mednote.ui.theme.MedNoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MedNoteApp
        setContent {
            MedNoteTheme {
                MedNoteRoot(
                    settingsStore = app.settingsStore,
                    visitStore = app.visitStore,
                    audioRecorder = app.audioRecorder,
                    visitPipeline = app.visitPipeline
                )
            }
        }
    }
}
