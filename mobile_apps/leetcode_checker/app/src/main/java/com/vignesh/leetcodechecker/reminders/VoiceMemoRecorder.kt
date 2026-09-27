package com.vignesh.leetcodechecker.reminders

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Records a short voice memo per reminder to app-private storage (matches this app's
 *  filesDir-over-getExternalFilesDir convention -- see BackupManager). */
object VoiceMemoRecorder {
    private var recorder: MediaRecorder? = null
    private var currentPath: String? = null

    fun memosDir(context: Context): File =
        File(context.filesDir, "reminder_voice_memos").apply { mkdirs() }

    fun pathFor(context: Context, reminderId: String): String =
        File(memosDir(context), "$reminderId.m4a").absolutePath

    fun startRecording(context: Context, reminderId: String) {
        stopRecording() // guard against a stale recorder from an interrupted prior session
        val outputPath = pathFor(context, reminderId)
        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        newRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputPath)
            prepare()
            start()
        }
        recorder = newRecorder
        currentPath = outputPath
    }

    /** Returns the recorded file's path on success, null if nothing was recording or it failed. */
    fun stopRecording(): String? {
        val current = recorder ?: return null
        val path = currentPath
        recorder = null
        currentPath = null
        return runCatching {
            current.stop()
            current.release()
            path
        }.getOrNull()
    }

    fun deleteMemo(path: String) {
        runCatching { File(path).delete() }
    }
}
