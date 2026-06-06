package com.mednote.service

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class AudioRecorderService(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private val elapsed = AtomicInteger(0)
    @Volatile private var running = false
    private var timerThread: Thread? = null

    val isRecording: Boolean get() = recorder != null
    val elapsedSeconds: Int get() = elapsed.get()

    fun startRecording(directory: File): File {
        stopRecording()
        directory.mkdirs()
        val file = File(directory, "rec_${System.currentTimeMillis()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mediaRecorder
        outputFile = file
        elapsed.set(0)
        running = true
        timerThread = Thread {
            while (running && !Thread.currentThread().isInterrupted) {
                Thread.sleep(1000)
                if (running) elapsed.incrementAndGet()
            }
        }.also { it.start() }
        return file
    }

    fun stopRecording(): File? {
        running = false
        timerThread?.interrupt()
        timerThread = null
        val file = outputFile
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        outputFile = null
        return file
    }
}

object CallService {
    fun normalizedPhone(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("+")) {
            val digits = trimmed.drop(1).filter { it.isDigit() }
            return if (digits.isEmpty()) null else "+$digits"
        }
        val digits = trimmed.filter { it.isDigit() }
        return digits.ifEmpty { null }
    }
}
