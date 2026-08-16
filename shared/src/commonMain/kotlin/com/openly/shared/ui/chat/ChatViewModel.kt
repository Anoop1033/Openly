package com.openly.shared.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openly.shared.data.local.OpenlyPrefs
import com.openly.shared.data.model.ChatMessage
import com.openly.shared.data.model.MatchInfo
import com.openly.shared.data.model.MessageType
import com.openly.shared.data.repository.ChatRepository
import com.openly.shared.data.repository.MediaRepository
import com.openly.shared.data.repository.ProfileRepository
import com.openly.shared.media.MediaPicker
import com.openly.shared.media.VoicePlayer
import com.openly.shared.media.VoiceRecorder
import com.openly.shared.platform.nowMillis
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val selfUid: String,
    private val matchId: String,
    private val chatRepository: ChatRepository,
    private val profileRepository: ProfileRepository,
    private val prefs: OpenlyPrefs,
    private val mediaRepository: MediaRepository,
    private val mediaPicker: MediaPicker,
    private val voiceRecorder: VoiceRecorder,
    private val voicePlayer: VoicePlayer
) : ViewModel() {

    private val _otherName = MutableStateFlow("")
    val otherName: StateFlow<String> = _otherName.asStateFlow()

    private val _otherEmoji = MutableStateFlow("🙂")
    val otherEmoji: StateFlow<String> = _otherEmoji.asStateFlow()

    private var otherUid: String = ""

    /**
     * Re-emits every second so "online"/"last seen" and the typing indicator can expire on their
     * own. Both are derived from timestamps, so without a ticker they'd stay frozen at whatever
     * they were when the last Firestore update happened.
     */
    private val ticker: StateFlow<Long> = MutableStateFlow(nowMillis()).also { flow ->
        viewModelScope.launch {
            while (true) {
                flow.value = nowMillis()
                delay(1000)
            }
        }
    }

    /** Messages the current user hasn't deleted for themselves. */
    val messages: StateFlow<List<ChatMessage>> = chatRepository.observeMessages(matchId)
        .map { list -> list.filterNot { it.deletedFor.contains(selfUid) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val match: StateFlow<MatchInfo?> = chatRepository.observeMatch(matchId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isOtherTyping: StateFlow<Boolean> = combine(match, ticker) { info, now ->
        info?.isTyping(otherUid, now) == true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _otherLastActiveMillis = MutableStateFlow(0L)

    /** "online" / "last seen …" — empty until the other profile has loaded. */
    val otherPresence: StateFlow<String> = combine(_otherLastActiveMillis, ticker) { lastActive, now ->
        com.openly.shared.platform.formatLastSeen(lastActive, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** Message being replied to, shown as a quote bar above the input. */
    private val _replyingTo = MutableStateFlow<ChatMessage?>(null)
    val replyingTo: StateFlow<ChatMessage?> = _replyingTo.asStateFlow()

    /** Message being edited; non-null puts the input row into edit mode. */
    private val _editing = MutableStateFlow<ChatMessage?>(null)
    val editing: StateFlow<ChatMessage?> = _editing.asStateFlow()

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    init {
        viewModelScope.launch {
            val info = chatRepository.getMatch(matchId) ?: return@launch
            otherUid = info.otherUid(selfUid)
            val profile = profileRepository.getProfile(otherUid) ?: return@launch
            _otherName.value = profile.displayName
            _otherEmoji.value = profile.avatarEmoji
            _otherLastActiveMillis.value = profile.lastActiveMillis
        }

        // Looking at the thread *is* reading it — keep clearing the unread badge as messages land
        // while the screen is open, not just once on entry. The local mark drives our own badge;
        // the Firestore write is what turns the sender's ticks blue.
        viewModelScope.launch {
            messages.collect { list ->
                list.maxOfOrNull { it.sentAtMillis }?.let { prefs.markMatchRead(matchId, it) }
                runCatching { chatRepository.markMessagesRead(matchId, selfUid, list) }
            }
        }

        // Refresh the other person's presence periodically while the thread is open.
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                if (otherUid.isNotEmpty()) {
                    profileRepository.getProfile(otherUid)?.let {
                        _otherLastActiveMillis.value = it.lastActiveMillis
                    }
                }
            }
        }
    }

    // ---- typing ----------------------------------------------------------------

    private var typingJob: Job? = null
    private var lastTypingPingMillis = 0L

    /**
     * Called on every keystroke. Re-pings at most every 2s (the indicator expires after 5s of
     * silence), and schedules a clear so the bubble disappears promptly when typing stops rather
     * than waiting out the full timeout.
     */
    fun onDraftChanged(draft: String) {
        if (draft.isEmpty()) {
            stopTyping()
            return
        }
        val now = nowMillis()
        if (now - lastTypingPingMillis > 2_000) {
            lastTypingPingMillis = now
            viewModelScope.launch { chatRepository.setTyping(matchId, selfUid, true) }
        }
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            delay(3_000)
            chatRepository.setTyping(matchId, selfUid, false)
            lastTypingPingMillis = 0L
        }
    }

    private fun stopTyping() {
        typingJob?.cancel()
        lastTypingPingMillis = 0L
        viewModelScope.launch { chatRepository.setTyping(matchId, selfUid, false) }
    }

    override fun onCleared() {
        super.onCleared()
        // Leaving the screen must clear the indicator, or the other side sees "typing…" forever.
        stopTyping()
    }

    // ---- actions ---------------------------------------------------------------

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val editTarget = _editing.value
        if (editTarget != null) {
            _editing.value = null
            viewModelScope.launch {
                runCatching { chatRepository.editMessage(matchId, editTarget.id, trimmed) }
                    .onFailure { _sendError.value = "Couldn't save the edit" }
            }
            return
        }

        val reply = _replyingTo.value
        _replyingTo.value = null
        stopTyping()
        viewModelScope.launch {
            // A network/permission failure here used to propagate out of the coroutine and kill the
            // process. Surface it in the UI instead so a failed send is recoverable.
            runCatching { chatRepository.sendMessage(matchId, selfUid, trimmed, replyTo = reply) }
                .onFailure { _sendError.value = "Couldn't send — tap to retry" }
        }
    }

    private val _isUploading = MutableStateFlow(false)

    /** Drives the composer's progress state so a slow upload doesn't look like a dropped tap. */
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    /** Opens the system photo picker, uploads what comes back, then posts it as a message. */
    fun pickAndSendImage() {
        if (_isUploading.value) return
        viewModelScope.launch {
            val bytes = runCatching { mediaPicker.pickImage() }.getOrNull() ?: return@launch
            _isUploading.value = true
            runCatching {
                val url = mediaRepository.uploadImage(matchId, selfUid, bytes)
                sendAttachment(MessageType.IMAGE, url)
            }.onFailure { _sendError.value = "Couldn't upload the photo" }
            _isUploading.value = false
        }
    }

    fun startRecording() {
        voiceRecorder.start()
        _isRecording.value = voiceRecorder.isRecording
        if (!_isRecording.value) _sendError.value = "Couldn't start recording"
    }

    fun stopRecordingAndSend() {
        if (!_isRecording.value) return
        _isRecording.value = false
        val recording = voiceRecorder.stop()
        if (recording == null) {
            _sendError.value = "Recording was too short"
            return
        }
        viewModelScope.launch {
            _isUploading.value = true
            runCatching {
                val url = mediaRepository.uploadVoiceNote(matchId, selfUid, recording.bytes)
                sendAttachment(MessageType.VOICE, url, recording.durationSeconds)
            }.onFailure { _sendError.value = "Couldn't upload the voice note" }
            _isUploading.value = false
        }
    }

    fun cancelRecording() {
        if (!_isRecording.value) return
        _isRecording.value = false
        voiceRecorder.cancel()
    }

    fun playVoice(url: String) {
        voicePlayer.play(url) {}
    }

    private fun sendAttachment(type: MessageType, url: String, durationSeconds: Int = 0) {
        val reply = _replyingTo.value
        _replyingTo.value = null
        viewModelScope.launch {
            runCatching {
                chatRepository.sendMessage(
                    matchId = matchId,
                    senderUid = selfUid,
                    text = "",
                    replyTo = reply,
                    type = type,
                    mediaUrl = url,
                    mediaDurationSeconds = durationSeconds
                )
            }.onFailure { _sendError.value = "Couldn't send attachment" }
        }
    }

    fun startReply(message: ChatMessage) {
        _editing.value = null
        _replyingTo.value = message
    }

    fun cancelReply() {
        _replyingTo.value = null
    }

    fun startEdit(message: ChatMessage) {
        _replyingTo.value = null
        _editing.value = message
    }

    fun cancelEdit() {
        _editing.value = null
    }

    fun deleteForEveryone(message: ChatMessage) {
        viewModelScope.launch {
            runCatching { chatRepository.deleteForEveryone(matchId, message.id) }
                .onFailure { _sendError.value = "Couldn't delete" }
        }
    }

    fun deleteForMe(message: ChatMessage) {
        viewModelScope.launch {
            runCatching { chatRepository.deleteForMe(matchId, message.id, selfUid) }
                .onFailure { _sendError.value = "Couldn't delete" }
        }
    }

    fun toggleReaction(message: ChatMessage, emoji: String) {
        val existing = message.reactions[selfUid]
        val next = if (existing == emoji) null else emoji
        viewModelScope.launch {
            runCatching { chatRepository.setReaction(matchId, message.id, selfUid, next) }
                .onFailure { _sendError.value = "Couldn't react" }
        }
    }

    fun clearSendError() {
        _sendError.value = null
    }
}
