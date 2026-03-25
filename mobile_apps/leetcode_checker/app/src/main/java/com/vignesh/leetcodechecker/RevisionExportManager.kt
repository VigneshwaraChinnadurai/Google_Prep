package com.vignesh.leetcodechecker

import android.content.Context
import android.util.Base64
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.vignesh.leetcodechecker.data.DailyChallengeUiModel
import com.vignesh.leetcodechecker.data.GitHubApi
import com.vignesh.leetcodechecker.data.GitHubUpsertRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.Locale

data class RevisionFiles(
    val folderDate: String,
    val questionText: String,
    val answerPython: String,
    val explanationText: String
)

data class LocalRevisionHistoryItem(
    val folderDate: String,
    val folderPath: String,
    val questionId: String,
    val title: String,
    val questionText: String,
    val answerPython: String,
    val explanationText: String,
    val lastUpdatedMillis: Long
)

object RevisionExportManager {
    private fun githubApi(): GitHubApi {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubApi::class.java)
    }

    fun buildRevisionFiles(
        challenge: DailyChallengeUiModel,
        aiCode: String,
        aiExplanation: String,
        aiValidation: String
    ): RevisionFiles {
        val folderDate = challenge.date.ifBlank { ConsistencyStorage.istDateKey() }
        val expectedOutput = aiValidation.ifBlank {
            "Expected output not directly available from LeetCode API. Refer explanation and testcase validation after LLM generation."
        }

        val questionText = buildString {
            appendLine("Date: ${challenge.date}")
            appendLine("Question ID: ${challenge.questionId}")
            appendLine("Title: ${challenge.title}")
            appendLine("Difficulty: ${challenge.difficulty}")
            appendLine("Tags: ${challenge.tags.joinToString()}")
            appendLine("URL: ${challenge.url}")
            appendLine()
            appendLine("Question:")
            appendLine(challenge.fullStatement.ifBlank { challenge.descriptionPreview })
            appendLine()
            appendLine("Starter Code (Python3):")
            appendLine(challenge.pythonStarterCode.ifBlank { "class Solution:\n    pass" })
            appendLine()
            appendLine("Testcases:")
            appendLine(challenge.exampleTestcases.ifBlank { "Not provided" })
            appendLine()
            appendLine("Expected Output / Validation:")
            appendLine(expectedOutput)
        }.trim()

        val explanationText = buildString {
            appendLine("Date: ${challenge.date}")
            appendLine("Title: ${challenge.title}")
            appendLine("URL: ${challenge.url}")
            appendLine()
            appendLine("LLM Explanation:")
            appendLine(aiExplanation.ifBlank { "No explanation generated." })
            appendLine()
            appendLine("LLM Testcase Validation:")
            appendLine(aiValidation.ifBlank { "No testcase validation generated." })
        }.trim()

        return RevisionFiles(
            folderDate = folderDate,
            questionText = questionText,
            answerPython = aiCode.ifBlank { "# No code generated" },
            explanationText = explanationText
        )
    }

    suspend fun writeLocalRevisionFiles(context: Context, files: RevisionFiles): String {
        return withContext(Dispatchers.IO) {
            val settings = AppSettingsStore.load(context)
            val rootFolder = settings.revisionFolderName.ifBlank { "Leetcode_QA_Revision" }
            val baseDir = File(context.getExternalFilesDir(null), "$rootFolder/${files.folderDate}")
            if (!baseDir.exists()) {
                baseDir.mkdirs()
            }
            File(baseDir, "question.txt").writeText(files.questionText)
            File(baseDir, "answer.py").writeText(files.answerPython)
            File(baseDir, "explanation.txt").writeText(files.explanationText)
            baseDir.absolutePath
        }
    }

    suspend fun readLocalRevisionHistory(context: Context): List<LocalRevisionHistoryItem> {
        return withContext(Dispatchers.IO) {
            val settings = AppSettingsStore.load(context)
            val rootFolder = settings.revisionFolderName.ifBlank { "Leetcode_QA_Revision" }
            val rootDir = File(context.getExternalFilesDir(null), rootFolder)

            if (!rootDir.exists() || !rootDir.isDirectory) {
                return@withContext emptyList()
            }

            rootDir.listFiles()
                .orEmpty()
                .filter { it.isDirectory }
                .mapNotNull { folder ->
                    val questionFile = File(folder, "question.txt")
                    val answerFile = File(folder, "answer.py")
                    val explanationFile = File(folder, "explanation.txt")

                    if (!questionFile.exists() && !answerFile.exists() && !explanationFile.exists()) {
                        return@mapNotNull null
                    }

                    val questionText = questionFile.takeIf { it.exists() }?.readText().orEmpty()
                    val answerText = answerFile.takeIf { it.exists() }?.readText().orEmpty()
                    val explanationText = explanationFile.takeIf { it.exists() }?.readText().orEmpty()

                    val questionId = extractMetadataValue(questionText, "Question ID")
                    val title = extractMetadataValue(questionText, "Title")

                    val lastModified = listOf(questionFile, answerFile, explanationFile)
                        .filter { it.exists() }
                        .maxOfOrNull { it.lastModified() }
                        ?: folder.lastModified()

                    LocalRevisionHistoryItem(
                        folderDate = folder.name,
                        folderPath = folder.absolutePath,
                        questionId = questionId,
                        title = title,
                        questionText = questionText,
                        answerPython = answerText,
                        explanationText = explanationText,
                        lastUpdatedMillis = lastModified
                    )
                }
                .sortedWith(
                    compareByDescending<LocalRevisionHistoryItem> { it.folderDate }
                        .thenByDescending { it.lastUpdatedMillis }
                )
        }
    }

    private fun extractMetadataValue(text: String, key: String): String {
        if (text.isBlank()) return ""
        val target = "$key:".lowercase(Locale.US)
        val line = text.lineSequence().firstOrNull { it.trim().lowercase(Locale.US).startsWith(target) }
            ?: return ""
        return line.substringAfter(':').trim()
    }

    suspend fun pushToGitHub(
        files: RevisionFiles,
        token: String,
        owner: String,
        repo: String,
        branch: String,
        revisionRootFolder: String
    ) {
        require(token.isNotBlank()) {
            "Missing GitHub token. Configure GITHUB_TOKEN in local.properties."
        }

        val api = githubApi()
        val authHeader = "Bearer ${token.trim()}"
        val folder = "${revisionRootFolder.ifBlank { "Leetcode_QA_Revision" }}/${files.folderDate}"

        val uploadMap = mapOf(
            "$folder/question.txt" to files.questionText,
            "$folder/answer.py" to files.answerPython,
            "$folder/explanation.txt" to files.explanationText
        )

        uploadMap.forEach { (path, content) ->
            val existingSha = try {
                api.getFile(
                    authorization = authHeader,
                    owner = owner,
                    repo = repo,
                    path = path,
                    ref = branch
                ).sha
            } catch (http: HttpException) {
                if (http.code() == 404) null else throw http
            }

            val encoded = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val request = GitHubUpsertRequest(
                message = "Add LeetCode QA revision for ${files.folderDate}",
                content = encoded,
                branch = branch,
                sha = existingSha
            )

            api.upsertFile(
                authorization = authHeader,
                owner = owner,
                repo = repo,
                path = path,
                request = request
            )
        }
    }
}
