package com.openly.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.openly.app.OpenlyApp
import com.openly.app.ui.chat.ChatListScreen
import com.openly.app.ui.chat.ChatScreen
import com.openly.app.ui.onboarding.OnboardingScreen
import com.openly.app.ui.radar.RadarScreen
import com.openly.app.ui.requests.RequestsScreen
import com.openly.app.ui.settings.SettingsScreen

private sealed class Destination(val route: String, val label: String) {
    data object Radar : Destination("radar", "Radar")
    data object Requests : Destination("requests", "Requests")
    data object Chats : Destination("chats", "Chats")
    data object Settings : Destination("settings", "Settings")
}

private val bottomDestinations = listOf(Destination.Radar, Destination.Requests, Destination.Chats, Destination.Settings)

private const val CHAT_ROUTE = "chat/{matchId}"

@Composable
fun AppRoot(app: OpenlyApp) {
    var selfUid by remember { mutableStateOf<String?>(null) }
    var signInError by remember { mutableStateOf<String?>(null) }
    var retryToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(retryToken) {
        signInError = null
        try {
            selfUid = app.authRepository.ensureSignedIn()
        } catch (e: Exception) {
            signInError = e.message ?: "Couldn't sign in — check your connection and Firebase setup."
        }
    }

    val hasOnboarded by app.prefs.hasOnboarded.collectAsState(initial = null)
    val uid = selfUid

    when {
        signInError != null -> SignInErrorScreen(signInError!!) { retryToken++ }
        uid == null || hasOnboarded == null -> LoadingScreen()
        hasOnboarded == false -> OnboardingScreen(app = app, selfUid = uid)
        else -> MainScaffold(app = app, selfUid = uid)
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
private fun MainScaffold(app: OpenlyApp, selfUid: String) {
    val navController = rememberNavController()

    val badgeViewModel: BadgeViewModel = viewModel(
        factory = BadgeViewModelFactory(
            selfUid = selfUid,
            interestRepository = app.interestRepository,
            chatRepository = app.chatRepository,
            profileRepository = app.profileRepository,
            notifier = app.messageNotifier,
            prefs = app.prefs
        )
    )
    val badges by badgeViewModel.counts.collectAsState()

    Scaffold(
        bottomBar = {
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
            composable(Destination.Radar.route) { RadarScreen(app = app, selfUid = selfUid) }
            composable(Destination.Requests.route) { RequestsScreen(app = app, selfUid = selfUid) }
            composable(Destination.Chats.route) {
                ChatListScreen(
                    app = app,
                    selfUid = selfUid,
                    onOpenChat = { matchId -> navController.navigate("chat/$matchId") }
                )
            }
            composable(Destination.Settings.route) { SettingsScreen(app = app, selfUid = selfUid) }
            composable(CHAT_ROUTE) { backStackEntry ->
                val matchId = backStackEntry.arguments?.getString("matchId").orEmpty()
                // Suppress notifications for the thread that's on screen, and resume them on exit.
                DisposableEffect(matchId) {
                    badgeViewModel.setOpenChat(matchId)
                    onDispose { badgeViewModel.setOpenChat(null) }
                }
                ChatScreen(
                    app = app,
                    selfUid = selfUid,
                    matchId = matchId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
