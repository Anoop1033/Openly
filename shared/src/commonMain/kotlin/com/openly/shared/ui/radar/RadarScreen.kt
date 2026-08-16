package com.openly.shared.ui.radar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.openly.shared.di.AppContainer
import com.openly.shared.data.model.NearbyUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(container: AppContainer, selfUid: String) {
    val viewModel: RadarViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                RadarViewModel(
                    selfUid = selfUid,
                    profileRepository = container.profileRepository,
                    radarRepository = container.radarRepository,
                    interestRepository = container.interestRepository,
                    locationTracker = container.locationTracker,
                    prefs = container.prefs
                )
            }
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    var selectedUser by remember { mutableStateOf<NearbyUser?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Android and iOS surface a permission grant completely differently (a launcher callback vs.
    // a delegate method), so rather than share one callback shape, this just asks again shortly
    // after a request and stops once it's granted — cheap, and identical on both platforms.
    suspend fun requestAndAwaitPermission() {
        container.permissionRequester.request()
        while (!container.locationTracker.hasLocationPermission) {
            delay(800)
        }
        viewModel.onPermissionResult(true)
    }

    LaunchedEffect(Unit) {
        if (container.locationTracker.hasLocationPermission) {
            viewModel.onPermissionResult(true)
        } else {
            requestAndAwaitPermission()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Radar") },
                actions = {
                    Text(if (uiState.selfProfile?.isOpen == true) "Open" else "Closed", modifier = Modifier.padding(end = 4.dp))
                    Switch(
                        checked = uiState.selfProfile?.isOpen == true,
                        onCheckedChange = { viewModel.toggleOpen(it) },
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                !uiState.hasLocationPermission -> PermissionRationaleCard {
                    coroutineScope.launch { requestAndAwaitPermission() }
                }
                uiState.selfProfile?.isOpen != true -> ClosedBanner { viewModel.toggleOpen(true) }
                else -> Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                    RadarCanvas(
                        nearbyUsers = uiState.nearbyUsers,
                        radiusMeters = uiState.radiusMeters,
                        onBlipClick = { selectedUser = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (uiState.hasLocationPermission && uiState.selfProfile?.isOpen == true) {
                Text(
                    text = if (uiState.nearbyUsers.isEmpty()) {
                        "No one open within ${uiState.radiusMeters.roundToInt()}m yet — the radar updates live."
                    } else {
                        "${uiState.nearbyUsers.size} nearby within ${uiState.radiusMeters.roundToInt()}m. Tap a blip to say hi."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }

    selectedUser?.let { user ->
        ModalBottomSheet(onDismissRequest = { selectedUser = null }) {
            ProfilePreview(
                user = user,
                alreadySent = uiState.sentInterestTo.contains(user.profile.uid),
                onSendInterest = {
                    viewModel.sendInterest(user)
                    selectedUser = null
                }
            )
        }
    }
}

@Composable
private fun ProfilePreview(user: NearbyUser, alreadySent: Boolean, onSendInterest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(user.profile.avatarEmoji, style = MaterialTheme.typography.displaySmall)
        Text(user.profile.displayName, style = MaterialTheme.typography.headlineSmall)
        if (user.profile.bio.isNotBlank()) {
            Text(user.profile.bio, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "${user.distanceMeters.roundToInt()}m away",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
        Button(onClick = onSendInterest, enabled = !alreadySent, modifier = Modifier.fillMaxWidth()) {
            Text(if (alreadySent) "Request sent" else "Send interest")
        }
    }
}

@Composable
private fun PermissionRationaleCard(onRequest: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Openly needs your location", style = MaterialTheme.typography.titleMedium)
            Text(
                "Only used to find people open nearby — never shared beyond an approximate distance.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(onClick = onRequest) { Text("Grant location access") }
        }
    }
}

@Composable
private fun ClosedBanner(onGoOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("You're invisible right now", style = MaterialTheme.typography.titleMedium)
            Text(
                "Switch to open to appear on the radar and see who else nearby is open too.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onGoOpen) { Text("Go open") }
        }
    }
}
