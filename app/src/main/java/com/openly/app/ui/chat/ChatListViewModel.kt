package com.openly.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openly.app.data.local.OpenlyPrefs
import com.openly.app.data.repository.ChatRepository
import com.openly.app.data.repository.ProfileRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ChatPreview(
    val matchId: String,
    val otherUid: String,
    val otherName: String,
    val otherEmoji: String,
    val lastMessage: String,
    val isUnread: Boolean
)

class ChatListViewModel(
    selfUid: String,
    chatRepository: ChatRepository,
    private val profileRepository: ProfileRepository,
    prefs: OpenlyPrefs
) : ViewModel() {

    val chats: StateFlow<List<ChatPreview>> = combine(
        chatRepository.observeMatches(selfUid),
        prefs.lastReadByMatch
    ) { matches, lastReadByMatch ->
        coroutineScope {
            matches.map { match ->
                async {
                    val otherUid = match.otherUid(selfUid)
                    val profile = profileRepository.getProfile(otherUid)
                    val lastRead = lastReadByMatch[match.id] ?: 0L
                    ChatPreview(
                        matchId = match.id,
                        otherUid = otherUid,
                        otherName = profile?.displayName ?: "Someone",
                        otherEmoji = profile?.avatarEmoji ?: "🙂",
                        lastMessage = match.lastMessageText.ifBlank { "Say hi — you matched!" },
                        isUnread = match.lastMessageAtMillis > 0L &&
                            match.lastMessageSenderUid != selfUid &&
                            match.lastMessageAtMillis > lastRead
                    )
                }
            }.awaitAll()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class ChatListViewModelFactory(
    private val selfUid: String,
    private val chatRepository: ChatRepository,
    private val profileRepository: ProfileRepository,
    private val prefs: OpenlyPrefs
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatListViewModel(selfUid, chatRepository, profileRepository, prefs) as T
    }
}
