package com.mednote.service

import com.mednote.models.AppLanguage
import com.mednote.models.ExtractedItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withContext

object AIService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    data class PipelineResult(
        val transcriptLines: List<String>,
        val summary: String,
        val extractedItems: List<ExtractedItem>,
        val translatedTranscript: List<String>?
    )

    suspend fun runPipeline(
        audioFile: File,
        groqKey: String,
        geminiKey: String,
        userLanguage: AppLanguage,
        recordLanguage: AppLanguage
    ): PipelineResult = withContext(Dispatchers.IO) {
        coroutineScope {
            val gKey = groqKey.trim()
            val cKey = geminiKey.trim()
            val userLangName = userLanguage.label

            var originalText = "[No transcript — add a Groq API key in Settings]"
            if (gKey.isNotEmpty()) {
                groqWhisper(audioFile, gKey, recordLanguage.code)?.let { originalText = it }
            }

            val lines = splitTranscriptLines(originalText)
            val shouldTranslate = userLanguage != recordLanguage && !originalText.startsWith("[")

            val summaryDeferred = async {
                callGemini(
                    system = "Medical appointment summarizer. Warm, concise, plain language.",
                    message = """
                        Brief summary in $userLangName. Headings: 📋 Findings 💊 Medications ⚠️ Watch for 📅 Next steps

                        Transcript:
                        $originalText

                        Write entirely in $userLangName.
                    """.trimIndent(),
                    geminiKey = cKey,
                    groqKey = gKey
                )
            }

            val extractDeferred = async {
                callGemini(
                    system = "Medical data extractor. Return ONLY a valid JSON array, no markdown, no other text.",
                    message = """
                        Extract actionable items from the transcript. Respond ONLY with a valid JSON array (no markdown, no extra text), each item: {"type":"medication"|"appointment"|"action","text":"description in $userLangName","time":"time/date","suggestedHour":8}

                        IMPORTANT: All "text" values MUST be written in $userLangName.

                        Transcript:
                        $originalText
                    """.trimIndent(),
                    geminiKey = cKey,
                    groqKey = gKey
                )
            }

            val translateDeferred = if (shouldTranslate) {
                async {
                    callGemini(
                        system = "Medical transcript translator. Keep drug names, dosages, and numbers accurate. Return ONLY the translated transcript.",
                        message = "Translate into $userLangName:\n\n$originalText",
                        geminiKey = cKey,
                        groqKey = gKey
                    )?.let { splitTranscriptLines(it) }
                }
            } else null

            val summary = summaryDeferred.await()
                ?: "AI summary could not be generated — add a Groq or Gemini key in Settings."
            val extractRaw = extractDeferred.await()
            val translated = translateDeferred?.await()

            PipelineResult(
                transcriptLines = lines.ifEmpty { listOf(originalText) },
                summary = summary,
                extractedItems = parseExtractedItems(extractRaw),
                translatedTranscript = translated
            )
        }
    }

    private fun groqWhisper(file: File, apiKey: String, language: String): String? {
        val ext = file.extension.ifEmpty { "m4a" }
        val mime = if (ext == "wav") "audio/wav" else "audio/mp4"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "audio.$ext", file.asRequestBody(mime.toMediaType()))
            .addFormDataPart("model", "whisper-large-v3-turbo")
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("temperature", "0")
            .addFormDataPart("language", language)
            .build()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string() ?: return null)
            return json.optString("text").ifEmpty { null }
        }
    }

    private fun callGemini(system: String, message: String, geminiKey: String, groqKey: String): String? {
        val prompt = "$system\n\n$message"
        if (geminiKey.isNotEmpty()) {
            for (model in listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-flash-latest")) {
                geminiGenerate(prompt, geminiKey, model)?.let { return it }
            }
        }
        if (groqKey.isNotEmpty()) {
            return groqChat(prompt, groqKey)
        }
        return null
    }

    private fun geminiGenerate(prompt: String, apiKey: String, model: String): String? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val payload = JSONObject().apply {
            put("contents", org.json.JSONArray().put(
                JSONObject().put("role", "user").put("parts", org.json.JSONArray().put(
                    JSONObject().put("text", prompt)
                ))
            ))
            put("generationConfig", JSONObject()
                .put("maxOutputTokens", 1500)
                .put("temperature", 0.3))
        }
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string() ?: return null)
            if (json.has("error")) return null
            return json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.ifEmpty { null }
        }
    }

    private fun groqChat(prompt: String, apiKey: String): String? {
        val payload = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile")
            put("messages", org.json.JSONArray().put(
                JSONObject().put("role", "user").put("content", prompt)
            ))
            put("max_tokens", 1500)
            put("temperature", 0.3)
        }
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string() ?: return null)
            return json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.ifEmpty { null }
        }
    }

    private fun splitTranscriptLines(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val lines = mutableListOf<String>()
        Regex("[^.!?\\n]+[.!?\\n]*").findAll(text).forEach { match ->
            val s = match.value.trim()
            if (s.length > 1) lines.add(s)
        }
        if (lines.isEmpty()) {
            return text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        }
        return lines
    }

    private fun parseExtractedItems(raw: String?): List<ExtractedItem> {
        if (raw.isNullOrBlank()) return emptyList()
        var cleaned = raw.replace("```json", "").replace("```", "")
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start >= 0 && end > start) cleaned = cleaned.substring(start, end + 1)
        return runCatching {
            val array = Json.parseToJsonElement(cleaned).jsonArray
            array.mapIndexedNotNull { idx, element ->
                val obj = element.jsonObject
                val text = obj["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (text.isEmpty()) return@mapIndexedNotNull null
                ExtractedItem(
                    id = "ei${System.currentTimeMillis() + idx}",
                    type = obj["type"]?.jsonPrimitive?.contentOrNull ?: "action",
                    text = text,
                    time = obj["time"]?.jsonPrimitive?.contentOrNull ?: "",
                    suggestedHour = obj["suggestedHour"]?.jsonPrimitive?.intOrNull ?: 8,
                    status = "provisional"
                )
            }
        }.getOrDefault(emptyList())
    }
}
