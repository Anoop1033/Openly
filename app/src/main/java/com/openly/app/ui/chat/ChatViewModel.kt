package com.openly.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openly.app.data.local.OpenlyPrefs
import com.openly.app.data.model.ChatMessage
import com.openly.app.data.repository.ChatRepository
import com.openly.app.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val selfUid: String,
    private val matchId: String,
    private val chatRepository: ChatRepository,
    profileRepository: ProfileRepository,
    private val prefs: OpenlyPrefs
) : ViewModel() {

    private val _otherName = MutableStateFlow("")
    val otherName: StateFlow<String> = _otherName.asStateFlow()

    private val _otherEmoji = MutableStateFlow("🙂")
    val otherEmoji: StateFlow<String> = _otherEmoji.asStateFlow()

    val messages: StateFlow<List<ChatMessage>> = chatRepository.observeMessages(matchId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val match = chatRepository.getMatch(matchId) ?: return@launch
            val profile = profileRepository.getProfile(match.otherUid(selfUid)) ?: return@launch
            _otherName.value = profile.displayName
            _otherEmoji.value = profile.avatarEmoji
        }

        // Looking at the thread *is* reading it — keep clearing the unread badge as messages land
        // while the screen is open, not just once on entry.
        viewModelScope.launch {
            messages.collect { list ->
                list.maxOfOrNull { it.sentAtMillis }?.let { prefs.markMatchRead(matchId, it) }
            }
        }
    }

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            // A network/permission failure here used to propagate out of the coroutine and kill the
            // process. Surface it in the UI instead so a failed send is recoverable.
            runCatching { chatRepository.sendMessage(matchId, selfUid, trimmed) }
                .onFailure { _sendError.value = "Couldn't send — tap to retry" }
        }
    }

    fun clearSendError() {
        _sendError.value = null
    }
}

class ChatViewModelFactory(
    private val selfUid: String,
    private val matchId: String,
    private val chatRepository: ChatRepository,
    private val profileRepository: ProfileRepository,
    private val prefs: OpenlyPrefs
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(selfUid, matchId, chatRepository, profileRepository, prefs) as T
    }
}
