package com.openly.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openly.app.OpenlyApp
import com.openly.app.data.local.DEFAULT_RADAR_RADIUS_METERS
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: OpenlyApp, selfUid: String) {
    val profile by app.profileRepository.observeOwnProfile(selfUid).collectAsState(initial = null)
    val radiusMeters by app.prefs.radarRadiusMeters.collectAsState(initial = DEFAULT_RADAR_RADIUS_METERS)
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var profileLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(profile) {
        val current = profile
        if (!profileLoaded && current != null) {
            name = current.displayName
            bio = current.bio
            profileLoaded = true
        }
    }

    var sliderPosition by remember(radiusMeters) { mutableStateOf(radiusMeters.toFloat()) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Status", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (profile?.isOpen == true) "Visible on the radar" else "Invisible",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = profile?.isOpen == true,
                        onCheckedChange = { checked ->
                            scope.launch { app.profileRepository.setOpenStatus(selfUid, checked) }
                        }
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Profile", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text("What are you up for?") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = name.isNotBlank(),
                        onClick = {
                            val current = profile ?: return@Button
                            scope.launch {
                                app.profileRepository.saveProfile(
                                    current.copy(displayName = name.trim(), bio = bio.trim())
                                )
                            }
                        }
                    ) {
                        Text("Save profile")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Radar range: ${sliderPosition.roundToInt()}m", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "How far away someone can be and still show up as open.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = sliderPosition,
                        onValueChange = { sliderPosition = it },
                        onValueChangeFinished = {
                            scope.launch { app.prefs.setRadarRadiusMeters(sliderPosition.toDouble()) }
                        },
                        valueRange = 50f..500f
                    )
                }
            }
        }
    }
}
