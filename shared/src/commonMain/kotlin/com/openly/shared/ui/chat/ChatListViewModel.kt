package com.openly.shared.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openly.shared.data.local.OpenlyPrefs
import com.openly.shared.data.repository.ChatRepository
import com.openly.shared.data.repository.ProfileRepository
import com.openly.shared.platform.nowMillis
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatPreview(
    val matchId: String,
    val otherUid: String,
    val otherName: String,
    val otherEmoji: String,
    val lastMessage: String,
    val isUnread: Boolean,
    /** Empty when there are no messages yet; drives the trailing timestamp in the row. */
    val lastMessageAtMillis: Long = 0L,
    /** True while the other person is actively typing — replaces the preview text, as in WhatsApp. */
    val isTyping: Boolean = false
)

class ChatListViewModel(
    selfUid: String,
    chatRepository: ChatRepository,
    private val profileRepository: ProfileRepository,
    prefs: OpenlyPrefs
) : ViewModel() {

    /** Ticks so the typing flag can expire on its own, the same way it does inside a thread. */
    private val ticker: StateFlow<Long> = MutableStateFlow(nowMillis()).also { flow ->
        viewModelScope.launch {
            while (true) {
                flow.value = nowMillis()
                delay(1000)
            }
        }
    }

    val chats: StateFlow<List<ChatPreview>> = combine(
        chatRepository.observeMatches(selfUid),
        prefs.lastReadByMatch,
        ticker
    ) { matches, lastReadByMatch, now ->
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
                            match.lastMessageAtMillis > lastRead,
                        lastMessageAtMillis = match.lastMessageAtMillis,
                        isTyping = match.isTyping(otherUid, now)
                    )
                }
            }.awaitAll()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
