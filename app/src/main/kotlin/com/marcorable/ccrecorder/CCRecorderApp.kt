package com.marcorable.ccrecorder

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.marcorable.ccrecorder.core.designsystem.CCRecorderTheme
import com.marcorable.ccrecorder.feature.conversation.ConversationScreen
import com.marcorable.ccrecorder.feature.recorder.RecorderScreen
import com.marcorable.ccrecorder.feature.recordings.RecordingsScreen
import com.marcorable.ccrecorder.feature.settings.SettingsScreen

private data class TopLevelDestination(
    val route: String,
    val label: String,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(route = "recorder", label = "Record"),
    TopLevelDestination(route = "recordings", label = "Library"),
    TopLevelDestination(route = "conversation", label = "Notes"),
    TopLevelDestination(route = "settings", label = "Settings"),
)

@Composable
fun CCRecorderApp() {
    CCRecorderTheme {
        val navController = rememberNavController()
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            bottomBar = {
                NavigationBar(modifier = Modifier.fillMaxWidth()) {
                    topLevelDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(destination.label.take(1)) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            },
        ) { contentPadding ->
            NavHost(
                navController = navController,
                startDestination = "recorder",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                composable("recorder") { RecorderScreen() }
                composable("recordings") { RecordingsScreen() }
                composable("conversation") { ConversationScreen() }
                composable("settings") { SettingsScreen() }
            }
        }
    }
}
