package com.openly.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openly.app.OpenlyApp
import com.openly.app.data.model.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(app: OpenlyApp, selfUid: String, matchId: String, onBack: () -> Unit) {
    val viewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(selfUid, matchId, app.chatRepository, app.profileRepository, app.prefs)
    )
    val messages by viewModel.messages.collectAsState()
    val otherName by viewModel.otherName.collectAsState()
    val otherEmoji by viewModel.otherEmoji.collectAsState()
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

    // Re-pin to the newest message whenever the list grows *or* the keyboard opens/closes. The
    // window is set to adjustResize, so the IME shrinks the list viewport rather than sliding the
    // whole window up — without re-scrolling, the last message would slide out of view behind it.
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(messages.size, imeBottom) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("$otherEmoji $otherName") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message -> MessageBubble(message, isSelf = message.senderUid == selfUid) }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Say something…") }
                )
                IconButton(onClick = {
                    val text = draft
                    draft = ""
                    viewModel.sendMessage(text)
                }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, isSelf: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start) {
        Box(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .background(
                    color = if (isSelf) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(message.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
