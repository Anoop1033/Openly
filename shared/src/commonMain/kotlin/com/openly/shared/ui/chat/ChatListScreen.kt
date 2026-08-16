package com.openly.shared.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.openly.shared.di.AppContainer
import com.openly.shared.platform.formatClockTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(container: AppContainer, selfUid: String, onOpenChat: (String) -> Unit) {
    val viewModel: ChatListViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ChatListViewModel(selfUid, container.chatRepository, container.profileRepository, container.prefs)
            }
        }
    )
    val chats by viewModel.chats.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Chats") }) }) { padding ->
        if (chats.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No chats yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Once you both approve a request, you'll be able to chat here.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(chats, key = { it.matchId }) { chat ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenChat(chat.matchId) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${chat.otherEmoji} ${chat.otherName}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (chat.isTyping) "typing…" else chat.lastMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (chat.isTyping) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (chat.isUnread && !chat.isTyping) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (chat.lastMessageAtMillis > 0L) {
                                Text(
                                    text = formatClockTime(chat.lastMessageAtMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (chat.isUnread) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .size(10.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
