package com.mednote

import android.app.Application
import com.mednote.data.SettingsStore
import com.mednote.data.VisitStore
import com.mednote.service.AudioRecorderService
import com.mednote.service.VisitPipeline

class MedNoteApp : Application() {
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var visitStore: VisitStore
        private set
    lateinit var audioRecorder: AudioRecorderService
        private set
    val visitPipeline = VisitPipeline()

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        visitStore = VisitStore(this)
        audioRecorder = AudioRecorderService(this)
    }
}
