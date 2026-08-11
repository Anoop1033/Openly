package com.openly.app.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openly.app.OpenlyApp
import com.openly.app.data.model.Interest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsScreen(app: OpenlyApp, selfUid: String) {
    val viewModel: RequestsViewModel = viewModel(
        factory = RequestsViewModelFactory(selfUid, app.interestRepository, app.profileRepository)
    )
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Requests") }) }) { padding ->
        if (uiState.incoming.isEmpty() && uiState.outgoing.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No requests yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "When you send or receive interest on the radar, it'll show up here.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.incoming.isNotEmpty()) {
                item { Text("Incoming", style = MaterialTheme.typography.titleMedium) }
                items(uiState.incoming, key = { it.id }) { interest ->
                    IncomingRequestCard(
                        interest = interest,
                        onAccept = { viewModel.accept(interest) },
                        onDecline = { viewModel.decline(interest) }
                    )
                }
            }
            if (uiState.outgoing.isNotEmpty()) {
                item { Text("Sent", style = MaterialTheme.typography.titleMedium) }
                items(uiState.outgoing, key = { it.interest.id }) { request -> OutgoingRequestCard(request) }
            }
        }
    }
}

@Composable
private fun IncomingRequestCard(interest: Interest, onAccept: () -> Unit, onDecline: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("${interest.fromEmoji} ${interest.fromName}", style = MaterialTheme.typography.titleMedium)
            Text("wants to say hi", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = onAccept, modifier = Modifier.fillMaxWidth().weight(1f)) { Text("Accept") }
                OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth().weight(1f)) { Text("Decline") }
            }
        }
    }
}

@Composable
private fun OutgoingRequestCard(request: OutgoingRequest) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("To ${request.toEmoji} ${request.toName}", style = MaterialTheme.typography.titleMedium)
            Text("Waiting for a response…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
