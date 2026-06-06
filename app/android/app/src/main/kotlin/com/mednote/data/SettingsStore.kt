package com.mednote.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mednote.models.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("mednote_settings", Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "mednote_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _groqKey = MutableStateFlow(securePrefs.getString(KEY_GROQ, "") ?: "")
    val groqKey: StateFlow<String> = _groqKey.asStateFlow()

    private val _geminiKey = MutableStateFlow(securePrefs.getString(KEY_GEMINI, "") ?: "")
    val geminiKey: StateFlow<String> = _geminiKey.asStateFlow()

    private val _doctorPhone = MutableStateFlow(prefs.getString(KEY_PHONE, "") ?: "")
    val doctorPhone: StateFlow<String> = _doctorPhone.asStateFlow()

    private val _language = MutableStateFlow(AppLanguage.fromCode(prefs.getString(KEY_LANG, "en") ?: "en"))
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _recordLanguage = MutableStateFlow(AppLanguage.fromCode(prefs.getString(KEY_REC_LANG, "en") ?: "en"))
    val recordLanguage: StateFlow<AppLanguage> = _recordLanguage.asStateFlow()

    fun setGroqKey(value: String) {
        securePrefs.edit().putString(KEY_GROQ, value).apply()
        _groqKey.value = value
    }

    fun setGeminiKey(value: String) {
        securePrefs.edit().putString(KEY_GEMINI, value).apply()
        _geminiKey.value = value
    }

    fun setDoctorPhone(value: String) {
        prefs.edit().putString(KEY_PHONE, value).apply()
        _doctorPhone.value = value
    }

    fun setLanguage(value: AppLanguage) {
        prefs.edit().putString(KEY_LANG, value.code).apply()
        _language.value = value
    }

    fun setRecordLanguage(value: AppLanguage) {
        prefs.edit().putString(KEY_REC_LANG, value.code).apply()
        _recordLanguage.value = value
    }

    fun hasTranscriptionKey(): Boolean = _groqKey.value.trim().isNotEmpty()
    fun hasSummaryKey(): Boolean =
        _groqKey.value.trim().isNotEmpty() || _geminiKey.value.trim().isNotEmpty()

    companion object {
        private const val KEY_GROQ = "recallmd_groq"
        private const val KEY_GEMINI = "recallmd_gemini"
        private const val KEY_PHONE = "recallmd_call_phone"
        private const val KEY_LANG = "recallmd_lang"
        private const val KEY_REC_LANG = "recallmd_rec_lang"
    }
}
