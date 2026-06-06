package com.mednote.models

import kotlinx.serialization.Serializable

@Serializable
data class Visit(
    val id: String = "v${System.currentTimeMillis()}",
    val doctor: String = "Phone Visit",
    val specialty: String = "",
    val date: String = VisitFormats.formatDate(),
    val time: String = VisitFormats.formatTime(),
    val duration: String = "0m 0s",
    val originalLang: String = "en",
    val originalTranscript: List<String> = emptyList(),
    val translatedTranscript: List<String>? = null,
    val summary: String? = null,
    val translatedSummary: String? = null,
    val audioFileName: String? = null,
    val extractedItems: List<ExtractedItem> = emptyList(),
    val isProcessing: Boolean = false
)

@Serializable
data class ExtractedItem(
    val id: String,
    val type: String,
    val text: String,
    val time: String = "",
    val suggestedHour: Int = 8,
    val status: String = "provisional"
)

enum class AppLanguage(val code: String, val label: String) {
    EN("en", "English"),
    ZH("zh", "中文"),
    FR("fr", "Français"),
    DE("de", "Deutsch"),
    JA("ja", "日本語"),
    KO("ko", "한국어"),
    ES("es", "Español"),
    HI("hi", "हिंदी"),
    VI("vi", "Tiếng Việt"),
    AR("ar", "العربية");

    companion object {
        fun fromCode(code: String): AppLanguage =
            entries.find { it.code == code } ?: EN
    }
}

enum class ProcessingStep(val label: String) {
    SAVING("Saving recording…"),
    TRANSCRIBING("Transcribing audio…"),
    ANALYZING("Creating summary…"),
    DONE("Done!")
}

enum class CallVisitPhase {
    IDLE, RECORDING, ON_CALL, PROCESSING
}

object VisitFormats {
    fun formatDate(): String {
        val c = java.util.Calendar.getInstance()
        val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        return "${months[c.get(java.util.Calendar.MONTH)]} ${c.get(java.util.Calendar.DAY_OF_MONTH)}, ${c.get(java.util.Calendar.YEAR)}"
    }

    fun formatTime(): String {
        val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
        return sdf.format(java.util.Date())
    }

    fun formatDuration(seconds: Int): String {
        if (seconds <= 0) return "uploaded"
        return "${seconds / 60}m ${seconds % 60}s"
    }
}
