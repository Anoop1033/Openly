package com.openly.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.openly.app.data.local.OpenlyPrefs
import com.openly.app.data.model.MatchInfo
import com.openly.app.data.repository.ChatRepository
import com.openly.app.data.repository.InterestRepository
import com.openly.app.data.repository.ProfileRepository
import com.openly.app.notification.MessageNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

data class BadgeCounts(
    val pendingRequests: Int = 0,
    val unreadChats: Int = 0
)

/**
 * Drives the bottom-bar badges. Lives above the individual tabs so the dots stay accurate even
 * while the user is looking at a different screen.
 */
class BadgeViewModel(
    private val selfUid: String,
    interestRepository: InterestRepository,
    chatRepository: ChatRepository,
    private val profileRepository: ProfileRepository,
    private val notifier: MessageNotifier,
    prefs: OpenlyPrefs
) : ViewModel() {

    /** Set by the nav host so we never notify for the conversation already on screen. */
    private val openMatchId = MutableStateFlow<String?>(null)

    fun setOpenChat(matchId: String?) {
        openMatchId.value = matchId
        matchId?.let { notifier.markSeen(it, System.currentTimeMillis()) }
    }

    init {
        // One place watching every conversation, so a message arriving while the user is on Radar
        // (or any other tab) still surfaces.
        viewModelScope.launch {
            combine(chatRepository.observeMatches(selfUid), openMatchId) { matches, openId ->
                matches.filter { match ->
                    match.lastMessageAtMillis > 0L &&
                        match.lastMessageSenderUid != selfUid &&
                        match.id != openId
                }
            }.collect { incoming ->
                incoming.forEach { match ->
                    val profile = profileRepository.getProfile(match.lastMessageSenderUid)
                    notifier.notifyNewMessage(
                        matchId = match.id,
                        senderName = profile?.displayName ?: "Someone",
                        senderEmoji = profile?.avatarEmoji ?: "🙂",
                        text = match.lastMessageText,
                        sentAtMillis = match.lastMessageAtMillis
                    )
                }
            }
        }
    }

    /** A match counts as unread when its newest message came from the other person after our last read. */
    private fun MatchInfo.isUnreadFor(lastRead: Long): Boolean =
        lastMessageAtMillis > 0L &&
            lastMessageSenderUid != selfUid &&
            lastMessageAtMillis > lastRead

    val counts: StateFlow<BadgeCounts> = combine(
        interestRepository.observeIncoming(selfUid).map { it.size },
        chatRepository.observeMatches(selfUid),
        prefs.lastReadByMatch
    ) { pending, matches, lastReadByMatch ->
        val unread = matches.count { match ->
            match.isUnreadFor(lastReadByMatch[match.id] ?: 0L)
        }
        BadgeCounts(pendingRequests = pending, unreadChats = unread)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BadgeCounts())
}

class BadgeViewModelFactory(
    private val selfUid: String,
    private val interestRepository: InterestRepository,
    private val chatRepository: ChatRepository,
    private val profileRepository: ProfileRepository,
    private val notifier: MessageNotifier,
    private val prefs: OpenlyPrefs
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return BadgeViewModel(
            selfUid, interestRepository, chatRepository, profileRepository, notifier, prefs
        ) as T
    }
}
