package com.openly.shared.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.rememberNavController
import com.openly.shared.di.AppContainer
import com.openly.shared.ui.chat.ChatListScreen
import com.openly.shared.ui.chat.ChatScreen
import com.openly.shared.ui.onboarding.OnboardingScreen
import com.openly.shared.ui.radar.RadarScreen
import com.openly.shared.ui.requests.RequestsScreen
import com.openly.shared.ui.settings.SettingsScreen

private sealed class Destination(val route: String, val label: String) {
    data object Radar : Destination("radar", "Radar")
    data object Requests : Destination("requests", "Requests")
    data object Chats : Destination("chats", "Chats")
    data object Settings : Destination("settings", "Settings")
}

private val bottomDestinations = listOf(Destination.Radar, Destination.Requests, Destination.Chats, Destination.Settings)

private const val CHAT_ROUTE = "chat/{matchId}"

/**
 * [onSignedIn] fires once the anonymous uid is known. Push-token registration needs that uid but
 * the token itself arrives from a platform SDK, so each host app hooks in here rather than the
 * shared layer trying to own something only Android/iOS can provide.
 */
@Composable
fun AppRoot(container: AppContainer, onSignedIn: suspend (String) -> Unit = {}) {
    var selfUid by remember { mutableStateOf<String?>(null) }
    var signInError by remember { mutableStateOf<String?>(null) }
    var retryToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(retryToken) {
        signInError = null
        try {
            val uid = container.authRepository.ensureSignedIn()
            selfUid = uid
            onSignedIn(uid)
        } catch (e: Exception) {
            signInError = e.message ?: "Couldn't sign in — check your connection and Firebase setup."
        }
    }

    val hasOnboarded by container.prefs.hasOnboarded.collectAsState(initial = null)
    val uid = selfUid

    when {
        signInError != null -> SignInErrorScreen(signInError!!) { retryToken++ }
        uid == null || hasOnboarded == null -> LoadingScreen()
        hasOnboarded == false -> OnboardingScreen(container = container, selfUid = uid)
        else -> MainScaffold(container = container, selfUid = uid)
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SignInErrorScreen(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Couldn't connect to Openly", style = MaterialTheme.typography.titleMedium)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(container: AppContainer, selfUid: String) {
    val navController = rememberNavController()

    val badgeViewModel: BadgeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                BadgeViewModel(
                    selfUid = selfUid,
                    interestRepository = container.interestRepository,
                    chatRepository = container.chatRepository,
                    profileRepository = container.profileRepository,
                    notifier = container.messageNotifier,
                    prefs = container.prefs
                )
            }
        }
    )
    val badges by badgeViewModel.counts.collectAsState()

    // The window is not resized when the keyboard opens, so the bar would otherwise stay laid out
    // underneath it — reserving its height as unusable dead space above the keyboard. Hiding it
    // while typing reclaims that space (and matches how messaging apps behave anyway).
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Scaffold(
        bottomBar = {
            if (imeVisible) return@Scaffold
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                bottomDestinations.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    val badgeCount = when (destination) {
                        Destination.Requests -> badges.pendingRequests
                        Destination.Chats -> badges.unreadChats
                        else -> 0
                    }
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (badgeCount > 0) {
                                        Badge { Text(if (badgeCount > 9) "9+" else badgeCount.toString()) }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = when (destination) {
                                        Destination.Radar -> Icons.Filled.Explore
                                        Destination.Requests -> Icons.Filled.Notifications
                                        Destination.Chats -> Icons.AutoMirrored.Filled.Chat
                                        Destination.Settings -> Icons.Filled.Settings
                                    },
                                    contentDescription = destination.label
                                )
                            }
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Radar.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            composable(Destination.Radar.route) { RadarScreen(container = container, selfUid = selfUid) }
            composable(Destination.Requests.route) { RequestsScreen(container = container, selfUid = selfUid) }
            composable(Destination.Chats.route) {
                ChatListScreen(
                    container = container,
                    selfUid = selfUid,
                    onOpenChat = { matchId -> navController.navigate("chat/$matchId") }
                )
            }
            composable(Destination.Settings.route) { SettingsScreen(container = container, selfUid = selfUid) }
            composable(CHAT_ROUTE) { backStackEntry ->
                val matchId = backStackEntry.arguments?.getString("matchId").orEmpty()
                // Suppress notifications for the thread that's on screen, and resume them on exit.
                DisposableEffect(matchId) {
                    badgeViewModel.setOpenChat(matchId)
                    onDispose { badgeViewModel.setOpenChat(null) }
                }
                ChatScreen(
                    container = container,
                    selfUid = selfUid,
                    matchId = matchId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
