package com.vignesh.leetcodechecker.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.vignesh.leetcodechecker.data.GeminiApi
import com.vignesh.leetcodechecker.data.GeminiContent
import com.vignesh.leetcodechecker.data.GeminiGenerateRequest
import com.vignesh.leetcodechecker.data.GeminiGenerationConfig
import com.vignesh.leetcodechecker.data.GeminiPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Voice Walkthrough Engine - Audio-guided problem solving.
 * 
 * Features:
 * 1. Generate structured walkthroughs using AI
 * 2. Text-to-Speech playback with pause/resume
 * 3. Step-by-step pacing for learning
 * 4. Mock interview mode with Q&A pauses
 * 
 * Usage modes:
 * - Commute mode: Listen while traveling
 * - Study mode: Interactive with pauses for thinking
 * - Interview prep: Simulated interview questions
 */
class VoiceWalkthroughEngine(
    private val context: Context,
    private val geminiApi: GeminiApi? = null,
    private val apiKey: String? = null,
    private val model: String = "gemini-2.5-flash"
) {
    companion object {
        private const val TAG = "VoiceWalkthrough"
    }

    /**
     * A single step in the walkthrough.
     */
    data class WalkthroughStep(
        val stepNumber: Int,
        val title: String,
        val content: String,
        val pauseSeconds: Int = 0, // Time to pause for thinking
        val type: StepType = StepType.EXPLANATION
    )

    enum class StepType {
        INTRODUCTION,
        EXPLANATION,
        EXAMPLE,
        CODE_WALKTHROUGH,
        QUESTION, // Pause and ask user to think
        HINT,
        SOLUTION,
        COMPLEXITY_ANALYSIS,
        SUMMARY
    }

    data class Walkthrough(
        val problemTitle: String,
        val difficulty: String,
        val steps: List<WalkthroughStep>,
        val totalDurationMinutes: Int,
        val mode: WalkthroughMode
    )

    enum class WalkthroughMode {
        COMMUTE,    // Continuous, minimal pauses
        STUDY,      // With pauses for thinking
        INTERVIEW   // Q&A style with longer pauses
    }

    // TTS engine
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var isPaused = false
    private var currentStepIndex = 0
    private var currentWalkthrough: Walkthrough? = null

    // Callbacks
    private var onStepChanged: ((Int, WalkthroughStep) -> Unit)? = null
    private var onComplete: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    /**
     * Initialize TTS engine.
     */
    suspend fun initialize(): Boolean = suspendCancellableCoroutine { cont ->
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.95f) // Slightly slower for comprehension
                tts?.setPitch(1.0f)
                isTtsReady = true
                cont.resume(true)
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
                isTtsReady = false
                cont.resume(false)
            }
        }
    }

    /**
     * Generate a walkthrough for a problem using AI.
     */
    suspend fun generateWalkthrough(
        problemTitle: String,
        problemDescription: String,
        difficulty: String,
        solutionCode: String? = null,
        mode: WalkthroughMode = WalkthroughMode.STUDY
    ): Walkthrough = withContext(Dispatchers.IO) {
        if (geminiApi == null || apiKey.isNullOrBlank()) {
            // Generate a basic walkthrough without AI
            return@withContext generateBasicWalkthrough(problemTitle, problemDescription, difficulty, mode)
        }

        try {
            val prompt = buildString {
                appendLine("Generate a voice-friendly walkthrough for this LeetCode problem.")
                appendLine("The walkthrough should be suitable for audio listening (no code symbols, spell things out).")
                appendLine()
                appendLine("# Problem: $problemTitle")
                appendLine("## Difficulty: $difficulty")
                appendLine()
                appendLine("## Description:")
                appendLine(problemDescription.take(2000))
                
                if (!solutionCode.isNullOrBlank()) {
                    appendLine()
                    appendLine("## Solution Code:")
                    appendLine(solutionCode.take(1500))
                }
                
                appendLine()
                appendLine("## Mode: ${mode.name}")
                val modeInstruction = when (mode) {
                    WalkthroughMode.COMMUTE -> "Short, continuous flow. No pauses."
                    WalkthroughMode.STUDY -> "Include thinking pauses (5-10 seconds) after questions."
                    WalkthroughMode.INTERVIEW -> "Act as an interviewer. Ask questions and give 30 second pauses for answers."
                }
                appendLine("Mode style: $modeInstruction")
                
                appendLine()
                appendLine("""
                    Generate 6-10 steps as JSON array:
                    [
                      {
                        "stepNumber": 1,
                        "title": "Introduction",
                        "content": "Spoken text for this step (2-3 sentences)...",
                        "pauseSeconds": 0,
                        "type": "INTRODUCTION"
                      },
                      ...
                    ]
                    
                    Step types: INTRODUCTION, EXPLANATION, EXAMPLE, CODE_WALKTHROUGH, QUESTION, HINT, SOLUTION, COMPLEXITY_ANALYSIS, SUMMARY
                    
                    Make content conversational and easy to understand when spoken aloud.
                    Spell out symbols: "greater than" not ">", "O of n" not "O(n)".
                """.trimIndent())
            }

            val request = GeminiGenerateRequest(
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.5,
                    maxOutputTokens = 2048,
                    responseMimeType = "application/json"
                )
            )

            val response = geminiApi.generateContent(model, apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts
                ?.mapNotNull { it.text }?.joinToString("") ?: ""

            parseWalkthrough(text, problemTitle, difficulty, mode)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate AI walkthrough", e)
            generateBasicWalkthrough(problemTitle, problemDescription, difficulty, mode)
        }
    }

    private fun parseWalkthrough(json: String, title: String, difficulty: String, mode: WalkthroughMode): Walkthrough {
        return try {
            val arr = JSONArray(json.trim().removePrefix("```json").removeSuffix("```"))
            val steps = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                WalkthroughStep(
                    stepNumber = obj.getInt("stepNumber"),
                    title = obj.getString("title"),
                    content = obj.getString("content"),
                    pauseSeconds = obj.optInt("pauseSeconds", 0),
                    type = try {
                        StepType.valueOf(obj.optString("type", "EXPLANATION"))
                    } catch (e: Exception) {
                        StepType.EXPLANATION
                    }
                )
            }
            
            val totalMinutes = steps.sumOf { 
                (it.content.length / 150) + (it.pauseSeconds / 60) 
            } // ~150 words per minute
            
            Walkthrough(
                problemTitle = title,
                difficulty = difficulty,
                steps = steps,
                totalDurationMinutes = totalMinutes.coerceAtLeast(1),
                mode = mode
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse walkthrough JSON", e)
            throw e
        }
    }

    private fun generateBasicWalkthrough(
        title: String,
        description: String,
        difficulty: String,
        mode: WalkthroughMode
    ): Walkthrough {
        val pauseDuration = when (mode) {
            WalkthroughMode.COMMUTE -> 0
            WalkthroughMode.STUDY -> 5
            WalkthroughMode.INTERVIEW -> 30
        }
        
        val steps = listOf(
            WalkthroughStep(
                1, "Introduction",
                "Let's walk through the problem: $title. This is a $difficulty level problem.",
                type = StepType.INTRODUCTION
            ),
            WalkthroughStep(
                2, "Problem Understanding",
                description.take(500).replace(Regex("[<>\\[\\]{}]"), " "),
                pauseSeconds = pauseDuration,
                type = StepType.EXPLANATION
            ),
            WalkthroughStep(
                3, "Think About It",
                "Before we continue, think about what data structure might be useful here. What's the key insight?",
                pauseSeconds = pauseDuration * 2,
                type = StepType.QUESTION
            ),
            WalkthroughStep(
                4, "Approach",
                "Let's think step by step about how to solve this problem efficiently.",
                pauseSeconds = pauseDuration,
                type = StepType.EXPLANATION
            ),
            WalkthroughStep(
                5, "Summary",
                "That concludes our walkthrough of $title. Practice this problem and the pattern will become natural.",
                type = StepType.SUMMARY
            )
        )
        
        return Walkthrough(
            problemTitle = title,
            difficulty = difficulty,
            steps = steps,
            totalDurationMinutes = 5,
            mode = mode
        )
    }

    /**
     * Start playing a walkthrough.
     */
    fun startWalkthrough(
        walkthrough: Walkthrough,
        onStep: (Int, WalkthroughStep) -> Unit,
        onFinished: () -> Unit,
        onErr: (String) -> Unit
    ) {
        if (!isTtsReady) {
            onErr("Text-to-Speech not initialized")
            return
        }
        
        currentWalkthrough = walkthrough
        currentStepIndex = 0
        isPaused = false
        onStepChanged = onStep
        onComplete = onFinished
        onError = onErr
        
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "Speaking step: $currentStepIndex")
            }

            override fun onDone(utteranceId: String?) {
                val step = currentWalkthrough?.steps?.getOrNull(currentStepIndex)
                
                // Wait for pause duration if specified
                if (step != null && step.pauseSeconds > 0) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (!isPaused) {
                            playNextStep()
                        }
                    }, step.pauseSeconds * 1000L)
                } else {
                    playNextStep()
                }
            }

            @Deprecated("Deprecated in API")
            override fun onError(utteranceId: String?) {
                onError?.invoke("Speech error occurred")
            }
        })
        
        // Start first step
        playCurrentStep()
    }

    private fun playCurrentStep() {
        val walkthrough = currentWalkthrough ?: return
        val step = walkthrough.steps.getOrNull(currentStepIndex) ?: run {
            onComplete?.invoke()
            return
        }
        
        onStepChanged?.invoke(currentStepIndex, step)
        
        val utteranceId = UUID.randomUUID().toString()
        val textToSpeak = "${step.title}. ${step.content}"
        
        tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun playNextStep() {
        if (isPaused) return
        
        val walkthrough = currentWalkthrough ?: return
        currentStepIndex++
        
        if (currentStepIndex >= walkthrough.steps.size) {
            onComplete?.invoke()
        } else {
            playCurrentStep()
        }
    }

    /**
     * Pause playback.
     */
    fun pause() {
        isPaused = true
        tts?.stop()
    }

    /**
     * Resume playback.
     */
    fun resume() {
        isPaused = false
        playCurrentStep()
    }

    /**
     * Skip to next step.
     */
    fun skipToNext() {
        tts?.stop()
        playNextStep()
    }

    /**
     * Skip to previous step.
     */
    fun skipToPrevious() {
        if (currentStepIndex > 0) {
            tts?.stop()
            currentStepIndex--
            playCurrentStep()
        }
    }

    /**
     * Stop playback completely.
     */
    fun stop() {
        isPaused = true
        tts?.stop()
        currentWalkthrough = null
        currentStepIndex = 0
    }

    /**
     * Set speech rate (0.5 = slow, 1.0 = normal, 1.5 = fast).
     */
    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }

    /**
     * Get available voices.
     */
    fun getAvailableVoices(): List<String> {
        return tts?.voices?.map { it.name } ?: emptyList()
    }

    /**
     * Speak a single piece of text (for hints, etc.).
     */
    fun speakText(text: String) {
        if (!isTtsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    /**
     * Flow-based walkthrough playback for compose integration.
     */
    fun playWalkthroughAsFlow(walkthrough: Walkthrough): Flow<Pair<Int, WalkthroughStep>> = callbackFlow {
        startWalkthrough(
            walkthrough = walkthrough,
            onStep = { index, step ->
                trySend(index to step)
            },
            onFinished = {
                close()
            },
            onErr = { error ->
                close(Exception(error))
            }
        )
        
        awaitClose {
            stop()
        }
    }

    /**
     * Release TTS resources.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false
    }
}
