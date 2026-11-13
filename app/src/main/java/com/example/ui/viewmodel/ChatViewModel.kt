package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.network.NetworkStatus
import com.example.data.local.*
import com.example.data.repository.PrunelleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatUiState(
    val isLoading: Boolean = true,
    val conversation: ConversationEntity? = null,
    val messages: List<MessageEntity> = emptyList(),
    val totalMessagesCount: Int = 0,
    val currentPageLimit: Int = 20,
    val hasMoreMessages: Boolean = false,
    val replyTarget: MessageEntity? = null,
    val editingTarget: MessageEntity? = null,
    val networkStatus: NetworkStatus = NetworkStatus(),
    val allConversations: List<ConversationEntity> = emptyList(),
    val stickers: List<StickerEntity> = emptyList(),
    val prefs: UserSessionPrefs = UserSessionPrefs()
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val conversationId: String,
    private val repository: PrunelleRepository
) : ViewModel() {

    companion object {
        const val PAGE_SIZE = 20
    }

    private val _pageLimit = MutableStateFlow(PAGE_SIZE)
    private val _isInitialReady = MutableStateFlow(false)
    private val _replyTarget = MutableStateFlow<MessageEntity?>(null)
    private val _editingTarget = MutableStateFlow<MessageEntity?>(null)

    init {
        viewModelScope.launch {
            repository.markConversationRead(conversationId)
            delay(180)
            _isInitialReady.value = true
        }
    }

    private val pagedMessagesFlow = _pageLimit.flatMapLatest { limit ->
        repository.observePagedMessages(conversationId, limit)
    }

    val uiState: StateFlow<ChatUiState> = combine(
        _isInitialReady,
        repository.observeConversation(conversationId),
        pagedMessagesFlow,
        repository.observeMessageCount(conversationId),
        _pageLimit,
        _replyTarget,
        _editingTarget,
        repository.networkStatus,
        repository.observeConversations(),
        repository.observeStickers(),
        repository.userPrefs
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val ready = values[0] as Boolean
        val conv = values[1] as ConversationEntity?
        val msgs = values[2] as List<MessageEntity>
        val totalCount = values[3] as Int
        val limit = values[4] as Int
        ChatUiState(
            isLoading = !ready,
            conversation = conv,
            messages = msgs,
            totalMessagesCount = totalCount,
            currentPageLimit = limit,
            hasMoreMessages = totalCount > msgs.size,
            replyTarget = values[5] as MessageEntity?,
            editingTarget = values[6] as MessageEntity?,
            networkStatus = values[7] as NetworkStatus,
            allConversations = values[8] as List<ConversationEntity>,
            stickers = values[9] as List<StickerEntity>,
            prefs = values[10] as UserSessionPrefs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ChatUiState()
    )

    fun loadMoreMessages() {
        _pageLimit.value += PAGE_SIZE
    }

    fun setReplyTarget(msg: MessageEntity?) {
        _editingTarget.value = null
        _replyTarget.value = msg
    }

    fun setEditingTarget(msg: MessageEntity?) {
        _replyTarget.value = null
        _editingTarget.value = msg
    }

    fun sendTextOrSaveEdit(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val editing = _editingTarget.value
        if (editing != null) {
            viewModelScope.launch {
                repository.editMessage(editing, clean)
                _editingTarget.value = null
            }
            return
        }
        val reply = _replyTarget.value
        viewModelScope.launch {
            repository.sendMessage(
                conversationId = conversationId,
                content = clean,
                type = MessageType.TEXT,
                replyToMessage = reply
            )
            _replyTarget.value = null
        }
    }

    fun sendMediaMessage(
        type: MessageType,
        mediaUri: String?,
        content: String = "",
        fileName: String? = null,
        durationMs: Long = 0L
    ) {
        val reply = _replyTarget.value
        viewModelScope.launch {
            repository.sendMessage(
                conversationId = conversationId,
                content = content,
                type = type,
                mediaUri = mediaUri,
                mediaFileName = fileName,
                mediaDurationMs = durationMs,
                replyToMessage = reply
            )
            _replyTarget.value = null
        }
    }

    fun toggleReaction(message: MessageEntity, emoji: String) {
        viewModelScope.launch { repository.toggleReaction(message, emoji) }
    }

    fun deleteMessage(message: MessageEntity) {
        viewModelScope.launch { repository.deleteMessage(message) }
    }

    fun cycleMessageReadStatusDemoCheck(message: MessageEntity, next: ReadStatus) {
        viewModelScope.launch { repository.updateMessageStatus(message.id, next) }
    }

    fun forwardMessage(message: MessageEntity, targetConversationIds: List<String>) {
        viewModelScope.launch {
            repository.forwardMessageToConversations(message, targetConversationIds)
        }
    }

    fun shareMessageExternally(message: MessageEntity) {
        repository.shareMessageExternally(message)
    }

    fun updateConversationWallpaper(wallpaperId: String?, customUri: String?) {
        viewModelScope.launch {
            repository.setConversationWallpaper(conversationId, wallpaperId, customUri)
        }
    }

    fun updateGroupInfo(
        title: String,
        description: String,
        avatarUri: String?,
        allowMemberInvites: Boolean,
        allowMemberMedia: Boolean
    ) {
        viewModelScope.launch {
            repository.updateGroupInfo(conversationId, title, description, avatarUri, allowMemberInvites, allowMemberMedia)
        }
    }

    fun addGroupMember(memberId: String) {
        viewModelScope.launch { repository.addGroupMember(conversationId, memberId) }
    }

    fun removeGroupMember(memberId: String) {
        viewModelScope.launch { repository.removeGroupMember(conversationId, memberId) }
    }

    fun toggleGroupAdmin(memberId: String) {
        viewModelScope.launch { repository.toggleGroupAdmin(conversationId, memberId) }
    }

    fun leaveOrDeleteConversation(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteOrLeaveConversation(conversationId)
            onDone()
        }
    }

    class Factory(
        private val conversationId: String,
        private val repository: PrunelleRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(conversationId, repository) as T
        }
    }
}
