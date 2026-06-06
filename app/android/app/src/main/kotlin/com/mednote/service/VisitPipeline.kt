package com.mednote.service

import com.mednote.data.SettingsStore
import com.mednote.data.VisitStore
import com.mednote.models.AppLanguage
import com.mednote.models.ProcessingStep
import com.mednote.models.Visit
import com.mednote.models.VisitFormats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class VisitPipeline {
    private val _step = MutableStateFlow<ProcessingStep?>(null)
    val step: StateFlow<ProcessingStep?> = _step.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    suspend fun processRecording(
        audioFile: File,
        durationSeconds: Int,
        visitStore: VisitStore,
        settings: SettingsStore,
        doctorLabel: String = "Phone Visit"
    ): Visit? {
        _error.value = null
        _step.value = ProcessingStep.SAVING

        var visit = Visit(
            doctor = doctorLabel,
            duration = VisitFormats.formatDuration(durationSeconds),
            originalLang = settings.recordLanguage.first().code,
            isProcessing = true
        )

        val storedName = runCatching {
            visitStore.storeAudio(audioFile, visit.id)
        }.getOrElse {
            _error.value = it.message
            _step.value = null
            return null
        }
        visit = visit.copy(audioFileName = storedName)
        visitStore.add(visit)

        return runAiPipeline(visit, visitStore, settings)
    }

    suspend fun processImportedFile(
        uriFile: File,
        visitStore: VisitStore,
        settings: SettingsStore
    ): Visit? = processRecording(
        audioFile = uriFile,
        durationSeconds = 0,
        visitStore = visitStore,
        settings = settings,
        doctorLabel = "Imported Recording"
    )

    private suspend fun runAiPipeline(
        initialVisit: Visit,
        visitStore: VisitStore,
        settings: SettingsStore
    ): Visit? {
        _step.value = ProcessingStep.TRANSCRIBING
        val audio = visitStore.audioFile(initialVisit) ?: return fail(initialVisit, visitStore, "Audio file missing")

        return try {
            val groq = settings.groqKey.first()
            val gemini = settings.geminiKey.first()
            val lang = settings.language.first()
            val recLang = settings.recordLanguage.first()

            val result = withContext(Dispatchers.IO) {
                AIService.runPipeline(audio, groq, gemini, lang, recLang)
            }

            _step.value = ProcessingStep.ANALYZING
            val updated = initialVisit.copy(
                originalTranscript = result.transcriptLines,
                summary = result.summary,
                translatedTranscript = result.translatedTranscript,
                translatedSummary = if (lang != AppLanguage.EN) result.summary else null,
                extractedItems = result.extractedItems,
                isProcessing = false
            )
            visitStore.update(updated)
            _step.value = ProcessingStep.DONE
            kotlinx.coroutines.delay(600)
            _step.value = null
            updated
        } catch (e: Exception) {
            fail(initialVisit, visitStore, e.message ?: "Processing failed")
        }
    }

    private fun fail(visit: Visit, visitStore: VisitStore, message: String): Visit {
        _error.value = message
        val updated = visit.copy(
            isProcessing = false,
            summary = "Processing failed: $message"
        )
        visitStore.update(updated)
        _step.value = null
        return updated
    }
}
