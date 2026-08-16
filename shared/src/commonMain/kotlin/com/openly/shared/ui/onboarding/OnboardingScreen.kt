package com.openly.shared.ui.onboarding

import com.openly.shared.platform.nowMillis
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openly.shared.di.AppContainer
import com.openly.shared.data.model.UserProfile
import kotlinx.coroutines.launch

private val avatarChoices = listOf("🙂", "😎", "🎧", "📚", "☕", "🎨", "🎮", "🌱")

@Composable
fun OnboardingScreen(container: AppContainer, selfUid: String) {
    var displayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf(avatarChoices.first()) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Welcome to Openly", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Set your status to open and you'll show up on the radar for others " +
                    "nearby who are open too — nobody can message you until you both approve.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))

            Text("Pick an avatar", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                avatarChoices.forEach { emoji ->
                    val isSelected = emoji == selectedEmoji
                    Text(
                        text = emoji,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .selectable(selected = isSelected) { selectedEmoji = emoji }
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            )
                            .padding(8.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("First name or nickname") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("What are you up for? (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))

            Button(
                enabled = displayName.isNotBlank() && !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        container.profileRepository.saveProfile(
                            UserProfile(
                                uid = selfUid,
                                displayName = displayName.trim(),
                                avatarEmoji = selectedEmoji,
                                bio = bio.trim(),
                                isOpen = false,
                                lastActiveMillis = nowMillis()
                            )
                        )
                        container.prefs.setOnboarded(true)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Get started")
            }
        }
    }
}
