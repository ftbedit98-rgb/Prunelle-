package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.repository.AiChatMode
import com.example.data.repository.PrunelleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AiHubUiState(
    val assistantMessages: List<AiMessageEntity> = emptyList(),
    val companionMessages: List<AiMessageEntity> = emptyList(),
    val memories: List<AiMemoryEntity> = emptyList(),
    val creations: List<AiCreationEntity> = emptyList(),
    val tikTokAnalyses: List<TikTokAnalysisEntity> = emptyList(),
    val selectedTikTokAnalysisId: String? = null,
    val selectedTikTokQa: List<TikTokQaEntity> = emptyList(),
    val isGeneratingChat: Boolean = false,
    val isGeneratingStudio: Boolean = false,
    val studioProgress: Float = 0f,
    val isAnalyzingTikTok: Boolean = false,
    val isCloudAiEnabled: Boolean = false,
    val aiMemoryEnabled: Boolean = true,
    val aiError: String? = null
)

class AiViewModel(
    private val repository: PrunelleRepository
) : ViewModel() {

    private val _selectedTikTokId = MutableStateFlow<String?>(null)
    private val _isGeneratingChat = MutableStateFlow(false)
    private val _isGeneratingStudio = MutableStateFlow(false)
    private val _studioProgress = MutableStateFlow(0f)
    private val _isAnalyzingTikTok = MutableStateFlow(false)
    private val _aiError = MutableStateFlow<String?>(null)

    @Suppress("OPT_IN_USAGE")
    private val selectedQaFlow = _selectedTikTokId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeTikTokQa(id)
    }

    val uiState: StateFlow<AiHubUiState> = combine(
        repository.observeAiMessages(AiChatMode.ASSISTANT.name),
        repository.observeAiMessages(AiChatMode.COMPANION.name),
        repository.observeAiMemories(),
        repository.observeAiCreations(),
        repository.observeTikTokAnalyses(),
        _selectedTikTokId,
        selectedQaFlow,
        _isGeneratingChat,
        _isGeneratingStudio,
        _studioProgress,
        _isAnalyzingTikTok,
        repository.userPrefs,
        _aiError
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val analyses = values[4] as List<TikTokAnalysisEntity>
        val selectedId = (values[5] as String?) ?: analyses.firstOrNull()?.id
        val prefs = values[11] as UserSessionPrefs
        AiHubUiState(
            assistantMessages = values[0] as List<AiMessageEntity>,
            companionMessages = values[1] as List<AiMessageEntity>,
            memories = values[2] as List<AiMemoryEntity>,
            creations = values[3] as List<AiCreationEntity>,
            tikTokAnalyses = analyses,
            selectedTikTokAnalysisId = selectedId,
            selectedTikTokQa = values[6] as List<TikTokQaEntity>,
            isGeneratingChat = values[7] as Boolean,
            isGeneratingStudio = values[8] as Boolean,
            studioProgress = values[9] as Float,
            isAnalyzingTikTok = values[10] as Boolean,
            isCloudAiEnabled = repository.aiRepository.isCloudAiEnabled,
            aiMemoryEnabled = prefs.aiMemoryEnabled,
            aiError = values[12] as String?
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = AiHubUiState()
    )

    fun clearError() {
        _aiError.value = null
    }

    fun sendPrompt(prompt: String, mode: AiChatMode) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _aiError.value = null
            _isGeneratingChat.value = true
            try {
                repository.sendAiPromptStream(prompt, mode) { }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AiViewModel", "sendPrompt failed", e)
                _aiError.value = e.localizedMessage ?: "Erreur lors de l'appel à Gemini IA."
            } finally {
                _isGeneratingChat.value = false
            }
        }
    }

    fun clearChat(mode: AiChatMode) {
        viewModelScope.launch { repository.clearAiHistory(mode) }
    }

    fun addMemory(label: String, value: String) {
        if (label.isBlank() || value.isBlank()) return
        viewModelScope.launch { repository.addAiMemory(label, value) }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch { repository.deleteAiMemory(id) }
    }

    fun clearAllMemories() {
        viewModelScope.launch { repository.clearAllAiMemories() }
    }

    fun setAiMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.prefsRepo.setAiMemoryEnabled(enabled) }
    }

    fun generateImage(context: Context, prompt: String, style: String, aspectRatio: String, referenceUri: String?) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _aiError.value = null
            _isGeneratingStudio.value = true
            _studioProgress.value = 0.25f
            try {
                val created = repository.aiRepository.generateStudioImage(context, prompt, style, aspectRatio, referenceUri)
                repository.saveAiCreation(created)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AiViewModel", "generateImage failed", e)
                _aiError.value = e.localizedMessage ?: "Échec de la génération d'image IA."
            } finally {
                _isGeneratingStudio.value = false
                _studioProgress.value = 0f
            }
        }
    }

    fun generateVideo(prompt: String, durationSeconds: Int, style: String, referenceUri: String?) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _aiError.value = null
            _isGeneratingStudio.value = true
            _studioProgress.value = 0.1f
            try {
                val created = repository.aiRepository.generateStudioVideo(
                    prompt = prompt,
                    durationSeconds = durationSeconds,
                    style = style,
                    referenceMediaUri = referenceUri,
                    onProgress = { _studioProgress.value = it }
                )
                repository.saveAiCreation(created)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AiViewModel", "generateVideo error", e)
                _aiError.value = e.localizedMessage ?: "Synthèse vidéo IA indisponible."
            } finally {
                _isGeneratingStudio.value = false
                _studioProgress.value = 0f
            }
        }
    }

    fun toggleCreationFavorite(creation: AiCreationEntity) {
        viewModelScope.launch { repository.toggleCreationFavorite(creation) }
    }

    fun deleteCreation(id: String) {
        viewModelScope.launch { repository.deleteAiCreation(id) }
    }

    fun selectTikTokAnalysis(id: String) {
        _selectedTikTokId.value = id
    }

    fun analyzeTikTokVideo(context: Context, url: String?, localVideoUri: String?) {
        if (url.isNullOrBlank() && localVideoUri.isNullOrBlank()) return
        viewModelScope.launch {
            _aiError.value = null
            _isAnalyzingTikTok.value = true
            try {
                val analysis = repository.analyzeAndSaveTikTokVideo(context, url, localVideoUri)
                _selectedTikTokId.value = analysis.id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AiViewModel", "analyzeTikTokVideo failed", e)
                _aiError.value = e.localizedMessage ?: "L'analyse vidéo TikTok AI a échoué."
            } finally {
                _isAnalyzingTikTok.value = false
            }
        }
    }

    fun askTikTokQuestion(analysis: TikTokAnalysisEntity, question: String) {
        if (question.isBlank()) return
        viewModelScope.launch {
            _aiError.value = null
            try {
                repository.askTikTokFollowUp(analysis, question)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AiViewModel", "askTikTokQuestion failed", e)
                _aiError.value = e.localizedMessage ?: "Échec de l'assistant TikTok AI."
            }
        }
    }

    fun deleteTikTokAnalysis(id: String) {
        viewModelScope.launch {
            if (_selectedTikTokId.value == id) _selectedTikTokId.value = null
            repository.deleteTikTokAnalysis(id)
        }
    }

    class Factory(private val repository: PrunelleRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AiViewModel(repository) as T
        }
    }
}
