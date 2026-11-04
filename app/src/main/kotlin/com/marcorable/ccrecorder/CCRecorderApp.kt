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
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.marcorable.ccrecorder.core.designsystem.CCRecorderTheme
import com.marcorable.ccrecorder.feature.conversation.ConversationScreen
import com.marcorable.ccrecorder.feature.recorder.RecorderScreen
import com.marcorable.ccrecorder.feature.recordings.RecordingsScreen
import com.marcorable.ccrecorder.feature.settings.SettingsScreen
import kotlinx.serialization.Serializable

private sealed interface TopLevelDestination {
    val label: String

    @Serializable
    data object Recorder : TopLevelDestination {
        override val label = "Record"
    }

    @Serializable
    data object Recordings : TopLevelDestination {
        override val label = "Library"
    }

    @Serializable
    data object Conversation : TopLevelDestination {
        override val label = "Notes"
    }

    @Serializable
    data object Settings : TopLevelDestination {
        override val label = "Settings"
    }
}

private val topLevelDestinations = listOf(
    TopLevelDestination.Recorder,
    TopLevelDestination.Recordings,
    TopLevelDestination.Conversation,
    TopLevelDestination.Settings,
)

private fun NavDestination?.isRoute(destination: TopLevelDestination): Boolean = when (destination) {
    TopLevelDestination.Recorder -> this?.hasRoute<TopLevelDestination.Recorder>() == true
    TopLevelDestination.Recordings -> this?.hasRoute<TopLevelDestination.Recordings>() == true
    TopLevelDestination.Conversation -> this?.hasRoute<TopLevelDestination.Conversation>() == true
    TopLevelDestination.Settings -> this?.hasRoute<TopLevelDestination.Settings>() == true
}

private fun NavHostController.navigateTo(destination: TopLevelDestination) {
    val topLevelNavOptions = navOptions {
        popUpTo(graph.startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }

    when (destination) {
        TopLevelDestination.Recorder -> navigate(TopLevelDestination.Recorder, topLevelNavOptions)
        TopLevelDestination.Recordings -> navigate(TopLevelDestination.Recordings, topLevelNavOptions)
        TopLevelDestination.Conversation -> navigate(TopLevelDestination.Conversation, topLevelNavOptions)
        TopLevelDestination.Settings -> navigate(TopLevelDestination.Settings, topLevelNavOptions)
    }
}

@Composable
fun CCRecorderApp() {
    CCRecorderTheme {
        val navController = rememberNavController()
        val currentDestination = navController.currentBackStackEntryAsState().value?.destination

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            bottomBar = {
                NavigationBar(modifier = Modifier.fillMaxWidth()) {
                    topLevelDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination.isRoute(destination),
                            onClick = { navController.navigateTo(destination) },
                            icon = { Text(destination.label.take(1)) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            },
        ) { contentPadding ->
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.Recorder,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                composable<TopLevelDestination.Recorder> { RecorderScreen() }
                composable<TopLevelDestination.Recordings> { RecordingsScreen() }
                composable<TopLevelDestination.Conversation> { ConversationScreen() }
                composable<TopLevelDestination.Settings> { SettingsScreen() }
            }
        }
    }
}
