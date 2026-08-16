package com.openly.shared.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.openly.shared.data.model.ChatMessage
import com.openly.shared.data.model.MessageType
import com.openly.shared.di.AppContainer
import com.openly.shared.platform.formatClockTime
import com.openly.shared.platform.formatDaySeparator
import com.openly.shared.platform.formatDuration
import com.openly.shared.platform.isDifferentDay

/** The reaction palette offered on long-press, same set WhatsApp opens with. */
private val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(container: AppContainer, selfUid: String, matchId: String, onBack: () -> Unit) {
    val viewModel: ChatViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    selfUid = selfUid,
                    matchId = matchId,
                    chatRepository = container.chatRepository,
                    profileRepository = container.profileRepository,
                    prefs = container.prefs,
                    mediaRepository = container.mediaRepository,
                    mediaPicker = container.mediaPicker,
                    voiceRecorder = container.voiceRecorder,
                    voicePlayer = container.voicePlayer
                )
            }
        }
    )
    val messages by viewModel.messages.collectAsState()
    val otherName by viewModel.otherName.collectAsState()
    val otherEmoji by viewModel.otherEmoji.collectAsState()
    val otherPresence by viewModel.otherPresence.collectAsState()
    val isOtherTyping by viewModel.isOtherTyping.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val editing by viewModel.editing.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val sendError by viewModel.sendError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(sendError) {
        sendError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSendError()
        }
    }

    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Entering edit mode preloads the existing text so the field behaves like WhatsApp's editor.
    LaunchedEffect(editing) {
        editing?.let { draft = it.text }
    }

    // Re-pin to the newest message whenever the list grows *or* the keyboard opens/closes. The
    // window is set to adjustResize, so the IME shrinks the list viewport rather than sliding the
    // whole window up — without re-scrolling, the last message would slide out of view behind it.
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(messages.size, imeBottom, isOtherTyping) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("$otherEmoji $otherName")
                        // Typing outranks presence: it's the more immediate signal, and showing
                        // both at once would just make the header jitter between two lines.
                        val subtitle = when {
                            isOtherTyping -> "typing…"
                            else -> otherPresence
                        }
                        if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOtherTyping) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        // `union` takes the max of the two insets rather than stacking them. Chaining
        // .imePadding().navigationBarsPadding() (or letting Scaffold's bottom padding through as
        // well) would add both, leaving a nav-bar-sized strip of dead space above the keyboard.
        // Keyboard open -> ime wins; keyboard closed -> the gesture bar keeps its clearance.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(messages) { index, message ->
                    val previous = messages.getOrNull(index - 1)
                    if (previous == null || isDifferentDay(previous.sentAtMillis, message.sentAtMillis)) {
                        DaySeparator(message.sentAtMillis)
                    }
                    MessageBubble(
                        message = message,
                        isSelf = message.senderUid == selfUid,
                        selfUid = selfUid,
                        onReply = { viewModel.startReply(message) },
                        onEdit = { viewModel.startEdit(message) },
                        onDeleteForMe = { viewModel.deleteForMe(message) },
                        onDeleteForEveryone = { viewModel.deleteForEveryone(message) },
                        onReact = { emoji -> viewModel.toggleReaction(message, emoji) },
                        onPlayVoice = { url -> viewModel.playVoice(url) }
                    )
                }
            }

            replyingTo?.let { target ->
                ComposerContextBar(
                    label = if (target.senderUid == selfUid) "Replying to yourself" else "Replying to $otherName",
                    body = target.previewForQuote(),
                    onDismiss = { viewModel.cancelReply() }
                )
            }
            if (editing != null) {
                ComposerContextBar(
                    label = "Editing message",
                    body = editing!!.text,
                    onDismiss = {
                        viewModel.cancelEdit()
                        draft = ""
                    }
                )
            }

            if (isRecording) {
                RecordingBar(
                    onCancel = { viewModel.cancelRecording() },
                    onSend = { viewModel.stopRecordingAndSend() }
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Attachments are meaningless mid-edit — editing only ever rewrites text.
                    if (editing == null) {
                        IconButton(onClick = { viewModel.pickAndSendImage() }, enabled = !isUploading) {
                            Icon(Icons.Filled.Image, contentDescription = "Send a photo")
                        }
                    }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            viewModel.onDraftChanged(it)
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Say something…") }
                    )
                    when {
                        isUploading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        // Empty draft offers the mic instead of send, the way WhatsApp swaps them.
                        draft.isBlank() && editing == null -> IconButton(onClick = { viewModel.startRecording() }) {
                            Icon(Icons.Filled.Mic, contentDescription = "Record a voice note")
                        }
                        else -> IconButton(onClick = {
                            val text = draft
                            draft = ""
                            viewModel.sendMessage(text)
                        }) {
                            Icon(
                                imageVector = if (editing != null) Icons.Filled.Done else Icons.AutoMirrored.Filled.Send,
                                contentDescription = if (editing != null) "Save edit" else "Send"
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Replaces the composer while recording, so the only two options are discard or send. */
@Composable
private fun RecordingBar(onCancel: () -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Filled.Delete, contentDescription = "Discard recording")
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(MaterialTheme.colorScheme.error, CircleShape)
        )
        Text(
            text = "Recording…",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onSend) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send voice note")
        }
    }
}

/** Small centred pill separating days, WhatsApp-style. */
@Composable
private fun DaySeparator(millis: Long) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Box(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = formatDaySeparator(millis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The reply/edit context strip that sits directly above the input row. */
@Composable
private fun ComposerContextBar(label: String, body: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(32.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    isSelf: Boolean,
    selfUid: String,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onReact: (String) -> Unit,
    onPlayVoice: (String) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start) {
            Box {
                Box(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .background(
                            color = when {
                                message.isDeleted -> MaterialTheme.colorScheme.surfaceVariant
                                isSelf -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                        // Deleted messages have no actions worth offering.
                        .combinedClickable(
                            enabled = !message.isDeleted,
                            onClick = {},
                            onLongClick = { menuOpen = true }
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column {
                        if (message.replyToId.isNotEmpty() && !message.isDeleted) {
                            QuotedMessage(message.replyToText)
                        }
                        MessageBody(message, onPlayVoice)
                        MessageMeta(message, isSelf)
                    }
                }

                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    ReactionRow(onReact = { emoji ->
                        onReact(emoji)
                        menuOpen = false
                    })
                    DropdownMenuItem(
                        text = { Text("Reply") },
                        onClick = { onReply(); menuOpen = false }
                    )
                    if (message.messageType == MessageType.TEXT) {
                        DropdownMenuItem(
                            text = { Text("Copy") },
                            onClick = {
                                clipboard.setText(AnnotatedString(message.text))
                                menuOpen = false
                            }
                        )
                    }
                    if (isSelf && message.messageType == MessageType.TEXT) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = { onEdit(); menuOpen = false }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete for me") },
                        onClick = { onDeleteForMe(); menuOpen = false }
                    )
                    if (isSelf) {
                        DropdownMenuItem(
                            text = { Text("Delete for everyone") },
                            onClick = { onDeleteForEveryone(); menuOpen = false }
                        )
                    }
                }
            }

            if (message.reactions.isNotEmpty()) {
                ReactionChips(message, selfUid)
            }
        }
    }
}

@Composable
private fun MessageBody(message: ChatMessage, onPlayVoice: (String) -> Unit) {
    when {
        message.isDeleted -> Text(
            text = "🚫 This message was deleted",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        message.messageType == MessageType.IMAGE -> AsyncImage(
            model = message.mediaUrl,
            contentDescription = "Photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        message.messageType == MessageType.VOICE -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { onPlayVoice(message.mediaUrl) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play voice note")
            }
            Text(
                text = formatDuration(message.mediaDurationSeconds),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        else -> Text(message.text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Timestamp + "edited" + delivery ticks, on one trailing line inside the bubble. */
@Composable
private fun MessageMeta(message: ChatMessage, isSelf: Boolean) {
    Row(
        modifier = Modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (message.editedAtMillis > 0 && !message.isDeleted) {
            Text(
                text = "edited",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = formatClockTime(message.sentAtMillis),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Ticks are only meaningful on your own messages — you always "read" your own.
        if (isSelf && !message.isDeleted) {
            val read = message.readAtMillis > 0
            Icon(
                imageVector = if (read) Icons.Filled.DoneAll else Icons.Filled.Done,
                contentDescription = if (read) "Read" else "Sent",
                modifier = Modifier.size(14.dp),
                tint = if (read) Color(0xFF34B7F1) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuotedMessage(text: String) {
    Row(
        modifier = Modifier
            .padding(bottom = 6.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Text(
            text = text.ifEmpty { "Message unavailable" },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun ReactionRow(onReact: (String) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        QUICK_REACTIONS.forEach { emoji ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .combinedClickable(onClick = { onReact(emoji) }),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji)
            }
        }
    }
}

/** The little emoji cluster that hangs off the bottom edge of a reacted-to bubble. */
@Composable
private fun ReactionChips(message: ChatMessage, selfUid: String) {
    val counts = message.reactions.values.groupingBy { it }.eachCount()
    Row(
        modifier = Modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        counts.forEach { (emoji, count) ->
            val mine = message.reactions[selfUid] == emoji
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .border(
                        width = 1.dp,
                        color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(emoji, style = MaterialTheme.typography.labelSmall)
                if (count > 1) {
                    Text(
                        text = " $count",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun ChatMessage.previewForQuote(): String = when (messageType) {
    MessageType.IMAGE -> "📷 Photo"
    MessageType.VOICE -> "🎤 Voice message"
    MessageType.TEXT -> text
}
