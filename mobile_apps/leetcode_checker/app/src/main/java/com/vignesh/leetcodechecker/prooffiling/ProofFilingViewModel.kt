package com.vignesh.leetcodechecker.prooffiling

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * ProofFilingViewModel - Manages UI state and business logic for ProofFiling feature
 */
class ProofFilingViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "ProofFilingVM"
    }
    
    private val repository = ProofFilingRepository(application.applicationContext)
    
    private val _state = MutableStateFlow(ProofFilingUIState())
    val state: StateFlow<ProofFilingUIState> = _state.asStateFlow()
    
    // Suggested learnings from commits (for user to review)
    private val _suggestedLearnings = MutableStateFlow<List<LearningItem>>(emptyList())
    val suggestedLearnings: StateFlow<List<LearningItem>> = _suggestedLearnings.asStateFlow()
    
    init {
        loadInitialData()
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Initialization
    // ═══════════════════════════════════════════════════════════════════════
    
    private fun loadInitialData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            
            try {
                // Load config
                val config = repository.loadConfig()
                
                // Load current week entry
                val currentEntry = repository.getCurrentWeekEntry()
                
                // Load history
                val history = repository.loadEntries()
                
                _state.update { 
                    it.copy(
                        isLoading = false,
                        config = config,
                        currentEntry = currentEntry,
                        historyEntries = history.filter { entry -> entry.id != currentEntry.id }
                    )
                }
                
                // Schedule notification if not already scheduled
                ProofFilingNotificationScheduler.scheduleReminder(
                    getApplication(),
                    config
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load initial data", e)
                _state.update { 
                    it.copy(
                        isLoading = false,
                        error = "Failed to load data: ${e.message}"
                    )
                }
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Entry Management
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Add a win to current entry
     */
    fun addWin(description: String, category: WinCategory = WinCategory.OTHER) {
        val currentEntry = _state.value.currentEntry ?: return
        
        val newWin = WinItem(
            description = description,
            category = category
        )
        
        val updatedEntry = currentEntry.copy(
            wins = currentEntry.wins + newWin
        )
        
        saveEntry(updatedEntry)
    }
    
    /**
     * Remove a win from current entry
     */
    fun removeWin(winId: String) {
        val currentEntry = _state.value.currentEntry ?: return
        
        val updatedEntry = currentEntry.copy(
            wins = currentEntry.wins.filter { it.id != winId }
        )
        
        saveEntry(updatedEntry)
    }
    
    /**
     * Add a learning to current entry
     */
    fun addLearning(
        description: String,
        category: LearningCategory = LearningCategory.OTHER,
        source: LearningSource = LearningSource.MANUAL,
        relatedCommitUrl: String? = null
    ) {
        val currentEntry = _state.value.currentEntry ?: return
        
        val newLearning = LearningItem(
            description = description,
            category = category,
            source = source,
            relatedCommitUrl = relatedCommitUrl
        )
        
        val updatedEntry = currentEntry.copy(
            learnings = currentEntry.learnings + newLearning
        )
        
        saveEntry(updatedEntry)
    }
    
    /**
     * Remove a learning from current entry
     */
    fun removeLearning(learningId: String) {
        val currentEntry = _state.value.currentEntry ?: return
        
        val updatedEntry = currentEntry.copy(
            learnings = currentEntry.learnings.filter { it.id != learningId }
        )
        
        saveEntry(updatedEntry)
    }
    
    /**
     * Add an evidence item to current entry
     */
    fun addEvidence(
        description: String,
        type: EvidenceType,
        link: String? = null,
        metrics: Map<String, String> = emptyMap()
    ) {
        val currentEntry = _state.value.currentEntry ?: return
        
        val newEvidence = EvidenceItem(
            description = description,
            type = type,
            link = link,
            metrics = metrics
        )
        
        val updatedEntry = currentEntry.copy(
            evidence = currentEntry.evidence + newEvidence
        )
        
        saveEntry(updatedEntry)
    }
    
    /**
     * Remove an evidence item from current entry
     */
    fun removeEvidence(evidenceId: String) {
        val currentEntry = _state.value.currentEntry ?: return
        
        val updatedEntry = currentEntry.copy(
            evidence = currentEntry.evidence.filter { it.id != evidenceId }
        )
        
        saveEntry(updatedEntry)
    }
    
    /**
     * Accept a suggested learning (from auto-captured commits)
     */
    fun acceptSuggestedLearning(learning: LearningItem) {
        addLearning(
            description = learning.description,
            category = learning.category,
            source = learning.source,
            relatedCommitUrl = learning.relatedCommitUrl
        )
        
        // Remove from suggestions
        _suggestedLearnings.update { it.filter { l -> l.id != learning.id } }
    }
    
    /**
     * Dismiss a suggested learning
     */
    fun dismissSuggestedLearning(learningId: String) {
        _suggestedLearnings.update { it.filter { l -> l.id != learningId } }
    }
    
    private fun saveEntry(entry: ProofFilingEntry) {
        viewModelScope.launch {
            val savedEntry = repository.saveEntry(entry)
            _state.update { it.copy(currentEntry = savedEntry) }
            
            // Reload history
            val history = repository.loadEntries()
            _state.update { 
                it.copy(historyEntries = history.filter { e -> e.id != savedEntry.id })
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Fetch Integration Stats
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Fetch GitHub stats for current week
     */
    fun fetchGitHubStats() {
        viewModelScope.launch {
            _state.update { it.copy(isFetchingStats = true, error = null) }
            
            val result = repository.fetchGitHubStats()
            
            result.fold(
                onSuccess = { stats ->
                    // Generate summary from commits
                    val summaryResult = repository.generateGitHubSummary(stats.commitMessages)
                    val statsWithSummary = stats.copy(
                        llmSummary = summaryResult.getOrNull()
                    )
                    
                    // Update current entry with stats
                    val currentEntry = _state.value.currentEntry
                    if (currentEntry != null) {
                        val updatedEntry = currentEntry.copy(githubStats = statsWithSummary)
                        saveEntry(updatedEntry)
                    }
                    
                    // Extract suggested learnings from commits
                    val suggestions = repository.extractLearningsFromCommits(stats.commitMessages)
                    _suggestedLearnings.value = suggestions
                    
                    _state.update { 
                        it.copy(
                            isFetchingStats = false,
                            successMessage = "GitHub stats fetched successfully"
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { 
                        it.copy(
                            isFetchingStats = false,
                            error = "Failed to fetch GitHub stats: ${error.message}"
                        )
                    }
                }
            )
        }
    }
    
    /**
     * Fetch LeetCode stats
     */
    fun fetchLeetCodeStats(
        username: String = com.vignesh.leetcodechecker.AppSettingsStore.load(getApplication()).leetcodeUsername
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isFetchingStats = true, error = null) }
            
            // Get previous total from last entry
            val previousTotal = _state.value.historyEntries
                .firstOrNull()
                ?.leetcodeStats
                ?.currentTotalSolved ?: 0
            
            val result = repository.fetchLeetCodeStats(username, previousTotal)
            
            result.fold(
                onSuccess = { stats ->
                    // Update current entry with stats
                    val currentEntry = _state.value.currentEntry
                    if (currentEntry != null) {
                        val updatedEntry = currentEntry.copy(leetcodeStats = stats)
                        saveEntry(updatedEntry)
                        
                        // Auto-add evidence if problems were solved
                        if (stats.problemsSolvedThisWeek > 0) {
                            addEvidence(
                                description = "Solved ${stats.problemsSolvedThisWeek} LeetCode problems",
                                type = EvidenceType.LEETCODE,
                                link = "https://leetcode.com/u/$username/",
                                metrics = mapOf(
                                    "easy" to "+${stats.easyDelta}",
                                    "medium" to "+${stats.mediumDelta}",
                                    "hard" to "+${stats.hardDelta}",
                                    "total" to "${stats.currentTotalSolved}"
                                )
                            )
                        }
                    }
                    
                    _state.update { 
                        it.copy(
                            isFetchingStats = false,
                            successMessage = "LeetCode stats fetched successfully"
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { 
                        it.copy(
                            isFetchingStats = false,
                            error = "Failed to fetch LeetCode stats: ${error.message}"
                        )
                    }
                }
            )
        }
    }
    
    /**
     * Fetch all integration stats
     */
    fun fetchAllStats() {
        fetchGitHubStats()
        // LeetCode is called separately as it's handled after GitHub completes
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Summary & Preview
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Generate weekly summary using LLM
     */
    fun generateWeeklySummary() {
        viewModelScope.launch {
            val currentEntry = _state.value.currentEntry ?: return@launch
            
            _state.update { it.copy(isLoading = true, error = null) }
            
            val result = repository.generateWeeklySummary(currentEntry)
            
            result.fold(
                onSuccess = { summary ->
                    val updatedEntry = currentEntry.copy(weeklySummary = summary)
                    saveEntry(updatedEntry)
                    
                    _state.update { 
                        it.copy(
                            isLoading = false,
                            successMessage = "Weekly summary generated"
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { 
                        it.copy(
                            isLoading = false,
                            error = "Failed to generate summary: ${error.message}"
                        )
                    }
                }
            )
        }
    }
    
    /**
     * Show preview of entry as markdown
     */
    fun showPreview() {
        val currentEntry = _state.value.currentEntry ?: return
        val markdown = currentEntry.toMarkdown()
        
        _state.update { 
            it.copy(
                showPreview = true,
                previewMarkdown = markdown
            )
        }
    }
    
    /**
     * Hide preview
     */
    fun hidePreview() {
        _state.update { 
            it.copy(
                showPreview = false,
                previewMarkdown = null
            )
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // GitHub Push
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Push current entry to GitHub
     */
    fun pushToGitHub() {
        viewModelScope.launch {
            val currentEntry = _state.value.currentEntry ?: return@launch
            
            _state.update { it.copy(isPushing = true, error = null) }
            
            val result = repository.pushToGitHub(currentEntry)
            
            result.fold(
                onSuccess = { updatedEntry ->
                    _state.update { 
                        it.copy(
                            isPushing = false,
                            currentEntry = updatedEntry,
                            successMessage = "Successfully pushed to GitHub!",
                            showPreview = false
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { 
                        it.copy(
                            isPushing = false,
                            error = "Failed to push to GitHub: ${error.message}"
                        )
                    }
                }
            )
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Configuration
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Update reminder time
     */
    fun updateReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            val config = _state.value.config.copy(
                reminderHour = hour,
                reminderMinute = minute
            )
            repository.saveConfig(config)
            _state.update { it.copy(config = config) }
            
            // Reschedule notification with new time
            ProofFilingNotificationScheduler.scheduleReminder(
                getApplication(),
                config
            )
        }
    }
    
    /**
     * Update reminder day
     */
    fun updateReminderDay(dayOfWeek: Int) {
        viewModelScope.launch {
            val config = _state.value.config.copy(reminderDayOfWeek = dayOfWeek)
            repository.saveConfig(config)
            _state.update { it.copy(config = config) }
            
            // Reschedule notification with new day
            ProofFilingNotificationScheduler.scheduleReminder(
                getApplication(),
                config
            )
        }
    }
    
    /**
     * Update integration config
     */
    fun updateIntegration(integration: IntegrationConfig) {
        viewModelScope.launch {
            val config = _state.value.config
            val updatedIntegrations = config.integrations.map {
                if (it.name == integration.name) integration else it
            }
            val updatedConfig = config.copy(integrations = updatedIntegrations)
            repository.saveConfig(updatedConfig)
            _state.update { it.copy(config = updatedConfig) }
        }
    }
    
    /**
     * Add new integration
     */
    fun addIntegration(name: String, profileUrl: String, autoFetch: Boolean) {
        viewModelScope.launch {
            val config = _state.value.config
            val newIntegration = IntegrationConfig(
                name = name,
                profileUrl = profileUrl,
                autoFetch = autoFetch
            )
            val updatedConfig = config.copy(
                integrations = config.integrations + newIntegration
            )
            repository.saveConfig(updatedConfig)
            _state.update { it.copy(config = updatedConfig) }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Utility
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Clear error message
     */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }
    
    /**
     * Clear success message
     */
    fun clearSuccessMessage() {
        _state.update { it.copy(successMessage = null) }
    }
    
    /**
     * Refresh all data
     */
    fun refresh() {
        loadInitialData()
    }
    
    /**
     * Load a specific entry from history
     */
    fun loadHistoryEntry(entryId: String) {
        val entry = _state.value.historyEntries.find { it.id == entryId }
        if (entry != null) {
            _state.update { it.copy(currentEntry = entry) }
        }
    }
    
    /**
     * Return to current week entry
     */
    fun returnToCurrentWeek() {
        viewModelScope.launch {
            val currentEntry = repository.getCurrentWeekEntry()
            _state.update { it.copy(currentEntry = currentEntry) }
        }
    }
    
    /**
     * Get summary for profile card
     */
    fun getProfileSummary(): ProfileFilingSummary {
        val entry = _state.value.currentEntry
        return ProfileFilingSummary(
            winsCount = entry?.wins?.size ?: 0,
            learningsCount = entry?.learnings?.size ?: 0,
            evidenceCount = entry?.evidence?.size ?: 0,
            weekRange = if (entry != null) "${entry.weekStartDate} - ${entry.weekEndDate}" else "",
            isPushed = entry?.isPushedToGitHub ?: false
        )
    }
}

/**
 * Summary data for profile card
 */
data class ProfileFilingSummary(
    val winsCount: Int,
    val learningsCount: Int,
    val evidenceCount: Int,
    val weekRange: String,
    val isPushed: Boolean
)
