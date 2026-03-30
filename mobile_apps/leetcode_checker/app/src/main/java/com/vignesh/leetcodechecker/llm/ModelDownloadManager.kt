package com.vignesh.leetcodechecker.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages local GGUF model downloads and storage.
 */
class ModelDownloadManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ModelDownloadManager"
        private const val MODELS_DIR = "gguf_models"
        
        /**
         * Available models for download - curated for mobile use
         */
        val AVAILABLE_MODELS = listOf(
            // Qwen2.5 series - excellent for coding tasks
            GgufModelInfo(
                id = "qwen2.5-0.5b-q4",
                name = "Qwen2.5 0.5B (Q4)",
                description = "Smallest, fastest. Good for simple tasks.",
                sizeBytes = 400_000_000L,
                ramRequired = "2GB",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
            ),
            GgufModelInfo(
                id = "qwen2.5-0.5b-q8",
                name = "Qwen2.5 0.5B (Q8)",
                description = "Small, better quality than Q4.",
                sizeBytes = 600_000_000L,
                ramRequired = "2GB",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q8_0.gguf",
                fileName = "qwen2.5-0.5b-instruct-q8_0.gguf"
            ),
            GgufModelInfo(
                id = "qwen2.5-1.5b-q4",
                name = "Qwen2.5 1.5B (Q4)",
                description = "Balanced size & quality. Recommended.",
                sizeBytes = 1_000_000_000L,
                ramRequired = "4GB",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
            ),
            GgufModelInfo(
                id = "qwen2.5-3b-q4",
                name = "Qwen2.5 3B (Q4)",
                description = "Best quality for coding. Needs good phone.",
                sizeBytes = 2_000_000_000L,
                ramRequired = "6GB",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-3b-instruct-q4_k_m.gguf"
            ),
            // Qwen2.5 Coder series - specialized for code
            GgufModelInfo(
                id = "qwen2.5-coder-0.5b-q4",
                name = "Qwen2.5 Coder 0.5B (Q4)",
                description = "Tiny coder model. Fast inference.",
                sizeBytes = 400_000_000L,
                ramRequired = "2GB",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-coder-0.5b-instruct-q4_k_m.gguf"
            ),
            GgufModelInfo(
                id = "qwen2.5-coder-1.5b-q4",
                name = "Qwen2.5 Coder 1.5B (Q4)",
                description = "Best coder for mobile. Recommended!",
                sizeBytes = 1_000_000_000L,
                ramRequired = "4GB",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf"
            ),
            // SmolLM2 series - very efficient
            GgufModelInfo(
                id = "smollm2-135m-q8",
                name = "SmolLM2 135M (Q8)",
                description = "Ultra tiny! Instant responses.",
                sizeBytes = 150_000_000L,
                ramRequired = "1GB",
                downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-135M-Instruct-GGUF/resolve/main/smollm2-135m-instruct-q8_0.gguf",
                fileName = "smollm2-135m-instruct-q8_0.gguf"
            ),
            GgufModelInfo(
                id = "smollm2-360m-q8",
                name = "SmolLM2 360M (Q8)",
                description = "Very small, decent quality.",
                sizeBytes = 400_000_000L,
                ramRequired = "2GB",
                downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q8_0.gguf",
                fileName = "smollm2-360m-instruct-q8_0.gguf"
            ),
            GgufModelInfo(
                id = "smollm2-1.7b-q4",
                name = "SmolLM2 1.7B (Q4)",
                description = "Good balance of size and capability.",
                sizeBytes = 1_100_000_000L,
                ramRequired = "4GB",
                downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF/resolve/main/smollm2-1.7b-instruct-q4_k_m.gguf",
                fileName = "smollm2-1.7b-instruct-q4_k_m.gguf"
            )
        )
    }
    
    private val modelsDir: File by lazy {
        File(context.filesDir, MODELS_DIR).also { it.mkdirs() }
    }
    
    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()
    
    /**
     * Get list of downloaded models
     */
    fun getDownloadedModels(): List<DownloadedModel> {
        return modelsDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".gguf") }
            ?.map { file ->
                val modelInfo = AVAILABLE_MODELS.find { it.fileName == file.name }
                DownloadedModel(
                    file = file,
                    name = modelInfo?.name ?: file.nameWithoutExtension,
                    sizeBytes = file.length(),
                    modelInfo = modelInfo
                )
            }
            ?.sortedByDescending { it.file.lastModified() }
            ?: emptyList()
    }
    
    /**
     * Get model file path for use with llama.cpp
     */
    fun getModelPath(fileName: String): String? {
        val file = File(modelsDir, fileName)
        return if (file.exists()) file.absolutePath else null
    }
    
    /**
     * Check if a model is downloaded
     */
    fun isModelDownloaded(modelId: String): Boolean {
        val model = AVAILABLE_MODELS.find { it.id == modelId } ?: return false
        return File(modelsDir, model.fileName).exists()
    }
    
    /**
     * Download a model
     */
    suspend fun downloadModel(modelId: String): Result<String> {
        val modelInfo = AVAILABLE_MODELS.find { it.id == modelId }
            ?: return Result.failure(IllegalArgumentException("Unknown model: $modelId"))
        
        return withContext(Dispatchers.IO) {
            try {
                val destFile = File(modelsDir, modelInfo.fileName)
                val tempFile = File(modelsDir, "${modelInfo.fileName}.tmp")
                
                // Check if already downloaded
                if (destFile.exists()) {
                    Log.i(TAG, "Model already downloaded: ${modelInfo.fileName}")
                    return@withContext Result.success(destFile.absolutePath)
                }
                
                Log.i(TAG, "Starting download: ${modelInfo.downloadUrl}")
                _downloadProgress.value = DownloadProgress(
                    modelId = modelId,
                    modelName = modelInfo.name,
                    bytesDownloaded = 0,
                    totalBytes = modelInfo.sizeBytes,
                    status = DownloadStatus.DOWNLOADING
                )
                
                val url = URL(modelInfo.downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.setRequestProperty("User-Agent", "LeetCodeChecker/1.0")
                
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("HTTP error: $responseCode")
                }
                
                val totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: modelInfo.sizeBytes
                var bytesDownloaded = 0L
                
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var lastUpdateTime = System.currentTimeMillis()
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead
                            
                            // Update progress every 500ms
                            val now = System.currentTimeMillis()
                            if (now - lastUpdateTime > 500) {
                                _downloadProgress.value = DownloadProgress(
                                    modelId = modelId,
                                    modelName = modelInfo.name,
                                    bytesDownloaded = bytesDownloaded,
                                    totalBytes = totalBytes,
                                    status = DownloadStatus.DOWNLOADING
                                )
                                lastUpdateTime = now
                            }
                        }
                    }
                }
                
                // Rename temp to final
                tempFile.renameTo(destFile)
                
                _downloadProgress.value = DownloadProgress(
                    modelId = modelId,
                    modelName = modelInfo.name,
                    bytesDownloaded = totalBytes,
                    totalBytes = totalBytes,
                    status = DownloadStatus.COMPLETED
                )
                
                Log.i(TAG, "Download completed: ${destFile.absolutePath}")
                Result.success(destFile.absolutePath)
                
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                _downloadProgress.value = _downloadProgress.value?.copy(
                    status = DownloadStatus.FAILED,
                    error = e.message
                )
                Result.failure(e)
            }
        }
    }
    
    /**
     * Cancel ongoing download
     */
    fun cancelDownload() {
        _downloadProgress.value = _downloadProgress.value?.copy(status = DownloadStatus.CANCELLED)
    }
    
    /**
     * Delete a downloaded model
     */
    fun deleteModel(fileName: String): Boolean {
        val file = File(modelsDir, fileName)
        return if (file.exists()) {
            file.delete().also {
                if (it) Log.i(TAG, "Deleted model: $fileName")
            }
        } else false
    }
    
    /**
     * Get total storage used by models
     */
    fun getTotalStorageUsed(): Long {
        return modelsDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".gguf") }
            ?.sumOf { it.length() }
            ?: 0L
    }
    
    /**
     * Clear download progress
     */
    fun clearProgress() {
        _downloadProgress.value = null
    }
}

/**
 * Model info for available downloads
 */
data class GgufModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val sizeBytes: Long,
    val ramRequired: String,
    val downloadUrl: String,
    val fileName: String
) {
    val sizeFormatted: String
        get() = when {
            sizeBytes >= 1_000_000_000 -> "%.1f GB".format(sizeBytes / 1_000_000_000.0)
            sizeBytes >= 1_000_000 -> "%.0f MB".format(sizeBytes / 1_000_000.0)
            else -> "%.0f KB".format(sizeBytes / 1_000.0)
        }
}

/**
 * Downloaded model info
 */
data class DownloadedModel(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val modelInfo: GgufModelInfo?
) {
    val sizeFormatted: String
        get() = when {
            sizeBytes >= 1_000_000_000 -> "%.1f GB".format(sizeBytes / 1_000_000_000.0)
            sizeBytes >= 1_000_000 -> "%.0f MB".format(sizeBytes / 1_000_000.0)
            else -> "%.0f KB".format(sizeBytes / 1_000.0)
        }
}

/**
 * Download progress state
 */
data class DownloadProgress(
    val modelId: String,
    val modelName: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val status: DownloadStatus,
    val error: String? = null
) {
    val progressPercent: Int
        get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
    
    val progressFormatted: String
        get() {
            val downloaded = when {
                bytesDownloaded >= 1_000_000_000 -> "%.1f GB".format(bytesDownloaded / 1_000_000_000.0)
                bytesDownloaded >= 1_000_000 -> "%.0f MB".format(bytesDownloaded / 1_000_000.0)
                else -> "%.0f KB".format(bytesDownloaded / 1_000.0)
            }
            val total = when {
                totalBytes >= 1_000_000_000 -> "%.1f GB".format(totalBytes / 1_000_000_000.0)
                totalBytes >= 1_000_000 -> "%.0f MB".format(totalBytes / 1_000_000.0)
                else -> "%.0f KB".format(totalBytes / 1_000.0)
            }
            return "$downloaded / $total"
        }
}

enum class DownloadStatus {
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}
