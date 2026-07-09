package com.vignesh.leetcodechecker.backup

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Exports/restores everything the app persists on-device: all SharedPreferences
 * (converted to typed JSON, not raw XML), the internal files dir (minus the
 * re-downloadable GGUF model weights), and the external revision-export folder.
 *
 * Deliberately reimplements SharedPreferences serialization instead of copying the
 * raw XML files so a) the format is stable across Android versions and b) secrets
 * can be redacted at the field level before anything leaves the device.
 */
object BackupManager {
    private const val TAG = "BackupManager"

    private const val MANIFEST_ENTRY = "manifest.json"
    private const val PREFS_PREFIX = "prefs/"
    private const val FILES_PREFIX = "files/"
    private const val EXTERNAL_PREFIX = "external_files/"

    private const val SETTINGS_PREFS_NAME = "leetcode_settings_prefs"
    private const val SETTINGS_BLOB_KEY = "app_settings_json"
    private val EXCLUDED_FILES_DIRS = setOf("gguf_models")
    private val FLAT_SECRET_KEYS_BY_PREFS = mapOf(
        "ai_news_prefs" to setOf("news_api_key")
    )

    data class BackupResult(val fileName: String, val sizeBytes: Long, val secretsRedacted: Boolean)

    suspend fun createBackup(
        context: Context,
        destinationTreeUri: Uri,
        redactSecrets: Boolean
    ): Result<BackupResult> = withContext(Dispatchers.IO) {
        try {
            val tempFile = File.createTempFile("leetcode_checker_backup", ".zip", context.cacheDir)
            ZipOutputStream(FileOutputStream(tempFile)).use { zip ->
                writeManifest(zip, context, redactSecrets)
                writeAllPrefs(zip, context, redactSecrets)
                writeFilesDir(zip, context)
                writeExternalFilesDir(zip, context)
            }

            val folder = DocumentFile.fromTreeUri(context, destinationTreeUri)
            if (folder == null || !folder.canWrite()) {
                tempFile.delete()
                return@withContext Result.failure(
                    Exception("Backup folder is no longer accessible. Choose it again in Global Settings.")
                )
            }

            val fileName = "leetcode_checker_backup_${timestampForFileName()}.zip"
            val target = folder.createFile("application/zip", fileName)
            if (target == null) {
                tempFile.delete()
                return@withContext Result.failure(Exception("Could not create the backup file in the chosen folder."))
            }

            val outputStream = context.contentResolver.openOutputStream(target.uri)
            if (outputStream == null) {
                tempFile.delete()
                return@withContext Result.failure(Exception("Could not write to the chosen backup folder."))
            }
            outputStream.use { out -> tempFile.inputStream().use { it.copyTo(out) } }

            val size = tempFile.length()
            tempFile.delete()

            Result.success(BackupResult(fileName, size, redactSecrets))
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            Result.failure(e)
        }
    }

    suspend fun restoreBackup(context: Context, sourceUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(Exception("Could not open the selected backup file."))

            inputStream.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        when {
                            entry.isDirectory -> Unit
                            name == MANIFEST_ENTRY -> Unit
                            name.startsWith(PREFS_PREFIX) && name.endsWith(".json") -> {
                                val prefsName = name.removePrefix(PREFS_PREFIX).removeSuffix(".json")
                                val json = runCatching { JSONObject(zip.readBytes().toString(Charsets.UTF_8)) }.getOrNull()
                                if (json != null) restorePrefsFile(context, prefsName, json)
                            }
                            name.startsWith(FILES_PREFIX) -> {
                                val relative = name.removePrefix(FILES_PREFIX)
                                if (relative.isNotBlank()) {
                                    writeExtractedFile(context.filesDir, relative, zip.readBytes())
                                }
                            }
                            name.startsWith(EXTERNAL_PREFIX) -> {
                                val relative = name.removePrefix(EXTERNAL_PREFIX)
                                val extDir = context.getExternalFilesDir(null)
                                if (relative.isNotBlank() && extDir != null) {
                                    writeExtractedFile(extDir, relative, zip.readBytes())
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            Result.failure(e)
        }
    }

    // ───────────────────────────── writing ─────────────────────────────

    private fun writeManifest(zip: ZipOutputStream, context: Context, redactSecrets: Boolean) {
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"

        val manifest = JSONObject()
            .put("createdAt", Instant.now().toString())
            .put("appVersionName", versionName)
            .put("secretsRedacted", redactSecrets)

        zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
        zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun writeAllPrefs(zip: ZipOutputStream, context: Context, redactSecrets: Boolean) {
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val prefsFiles = prefsDir.listFiles { f -> f.extension == "xml" } ?: return

        for (file in prefsFiles) {
            val prefsName = file.nameWithoutExtension
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val json = prefsToJson(prefs)
            if (redactSecrets) redactSecretsFromPrefsJson(prefsName, json)

            zip.putNextEntry(ZipEntry("$PREFS_PREFIX$prefsName.json"))
            zip.write(json.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    private fun writeFilesDir(zip: ZipOutputStream, context: Context) {
        val root = context.filesDir ?: return
        addDirectoryToZip(zip, root, root, FILES_PREFIX) { relativePath ->
            EXCLUDED_FILES_DIRS.none { excluded -> relativePath.startsWith(excluded) }
        }
    }

    private fun writeExternalFilesDir(zip: ZipOutputStream, context: Context) {
        val root = context.getExternalFilesDir(null) ?: return
        addDirectoryToZip(zip, root, root, EXTERNAL_PREFIX) { true }
    }

    private fun addDirectoryToZip(
        zip: ZipOutputStream,
        root: File,
        current: File,
        entryPrefix: String,
        includeFilter: (relativePath: String) -> Boolean
    ) {
        val children = current.listFiles() ?: return
        for (child in children) {
            val relativePath = child.relativeTo(root).path.replace(File.separatorChar, '/')
            if (!includeFilter(relativePath)) continue
            if (child.isDirectory) {
                addDirectoryToZip(zip, root, child, entryPrefix, includeFilter)
            } else {
                zip.putNextEntry(ZipEntry("$entryPrefix$relativePath"))
                child.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    // ───────────────────────── prefs <-> JSON ─────────────────────────

    private fun prefsToJson(prefs: SharedPreferences): JSONObject {
        val out = JSONObject()
        for ((key, value) in prefs.all) {
            val entry = JSONObject()
            when (value) {
                is Boolean -> entry.put("type", "boolean").put("value", value)
                is Int -> entry.put("type", "int").put("value", value)
                is Long -> entry.put("type", "long").put("value", value)
                is Float -> entry.put("type", "float").put("value", value.toDouble())
                is String -> entry.put("type", "string").put("value", value)
                is Set<*> -> entry.put("type", "stringset").put("value", JSONArray(value.toList()))
                else -> continue
            }
            out.put(key, entry)
        }
        return out
    }

    private fun restorePrefsFile(context: Context, prefsName: String, json: JSONObject) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

        // A redacted backup ships blank secrets -- don't let restoring one wipe out
        // credentials that are already configured and working on this device.
        var preservedGithubToken: String? = null
        var preservedGeminiKey: String? = null
        if (prefsName == SETTINGS_PREFS_NAME) {
            val currentSettings = prefs.getString(SETTINGS_BLOB_KEY, null)
                ?.let { runCatching { JSONObject(it) }.getOrNull() }
            preservedGithubToken = currentSettings?.optString("globalGithubToken")?.takeIf { it.isNotBlank() }
            preservedGeminiKey = currentSettings?.optString("globalGeminiApiKey")?.takeIf { it.isNotBlank() }
        }

        val editor = prefs.edit()
        editor.clear()

        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = json.optJSONObject(key) ?: continue
            when (entry.optString("type")) {
                "boolean" -> editor.putBoolean(key, entry.optBoolean("value"))
                "int" -> editor.putInt(key, entry.optInt("value"))
                "long" -> editor.putLong(key, entry.optLong("value"))
                "float" -> editor.putFloat(key, entry.optDouble("value").toFloat())
                "string" -> editor.putString(
                    key,
                    restoredStringValue(key, entry.optString("value"), preservedGithubToken, preservedGeminiKey)
                )
                "stringset" -> {
                    val arr = entry.optJSONArray("value") ?: JSONArray()
                    val set = (0 until arr.length()).map { arr.getString(it) }.toSet()
                    editor.putStringSet(key, set)
                }
            }
        }
        editor.apply()
    }

    private fun restoredStringValue(
        key: String,
        value: String,
        preservedGithubToken: String?,
        preservedGeminiKey: String?
    ): String {
        if (key != SETTINGS_BLOB_KEY || (preservedGithubToken == null && preservedGeminiKey == null)) return value
        val restoredSettings = runCatching { JSONObject(value) }.getOrNull() ?: return value

        if (restoredSettings.optString("globalGithubToken").isBlank() && preservedGithubToken != null) {
            restoredSettings.put("globalGithubToken", preservedGithubToken)
        }
        if (restoredSettings.optString("globalGeminiApiKey").isBlank() && preservedGeminiKey != null) {
            restoredSettings.put("globalGeminiApiKey", preservedGeminiKey)
        }
        return restoredSettings.toString()
    }

    private fun redactSecretsFromPrefsJson(prefsName: String, json: JSONObject) {
        FLAT_SECRET_KEYS_BY_PREFS[prefsName]?.forEach { key -> json.remove(key) }

        if (prefsName == SETTINGS_PREFS_NAME) {
            val settingsEntry = json.optJSONObject(SETTINGS_BLOB_KEY) ?: return
            if (settingsEntry.optString("type") != "string") return
            val settingsJson = runCatching { JSONObject(settingsEntry.optString("value")) }.getOrNull() ?: return
            settingsJson.put("globalGithubToken", "")
            settingsJson.put("globalGeminiApiKey", "")
            settingsEntry.put("value", settingsJson.toString())
        }
    }

    // Restore reads a zip the user picked via the file browser, not just ones this
    // class wrote -- resolve against the canonical root so a malicious/corrupt zip
    // with a "../" entry name can't write outside filesDir/external files dir.
    private fun writeExtractedFile(root: File, relativePath: String, bytes: ByteArray) {
        val canonicalRoot = root.canonicalFile
        val target = File(root, relativePath).canonicalFile
        if (!target.path.startsWith(canonicalRoot.path + File.separator)) {
            Log.e(TAG, "Refusing to restore entry outside target directory: $relativePath")
            return
        }
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { it.write(bytes) }
    }

    private fun timestampForFileName(): String =
        SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
}
