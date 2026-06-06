package com.mednote.data

import android.content.Context
import com.mednote.models.Visit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class VisitStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file = File(context.filesDir, "visits.json")
    val audioDirectory: File = File(context.filesDir, "Recordings").also { it.mkdirs() }

    private val _visits = MutableStateFlow<List<Visit>>(emptyList())
    val visits: StateFlow<List<Visit>> = _visits.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (!file.exists()) return
        runCatching {
            val data = file.readText()
            _visits.value = json.decodeFromString(ListSerializer(Visit.serializer()), data)
        }
    }

    private fun save() {
        file.writeText(json.encodeToString(ListSerializer(Visit.serializer()), _visits.value))
    }

    fun add(visit: Visit) {
        _visits.value = listOf(visit) + _visits.value
        save()
    }

    fun update(visit: Visit) {
        _visits.value = _visits.value.map { if (it.id == visit.id) visit else it }
        save()
    }

    fun delete(visit: Visit) {
        visit.audioFileName?.let { name ->
            File(audioDirectory, name).delete()
        }
        _visits.value = _visits.value.filter { it.id != visit.id }
        save()
    }

    fun audioFile(visit: Visit): File? =
        visit.audioFileName?.let { File(audioDirectory, it) }

    fun storeAudio(source: File, visitId: String): String {
        val ext = source.extension.ifEmpty { "m4a" }
        val name = "$visitId.$ext"
        val dest = File(audioDirectory, name)
        if (source.absolutePath != dest.absolutePath) {
            source.copyTo(dest, overwrite = true)
        }
        return name
    }
}
