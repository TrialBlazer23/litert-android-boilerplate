/**
 * MainActivity.kt — Single-activity host for litert-android-boilerplate.
 *
 * Hosts a NavHost with two destinations:
 *   "inference" → InferenceScreen (classical ML demo)
 *   "chat"      → ChatScreen (LLM streaming demo)
 *
 * Navigation between screens is handled by a bottom navigation bar with two tabs.
 * The activity is annotated with @AndroidEntryPoint so Hilt can inject into it
 * (and transitively into the ViewModels of its Compose children).
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */
package com.necessitylabs.litert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.necessitylabs.litert.ui.screens.ChatScreen
import com.necessitylabs.litert.ui.screens.InferenceScreen
import com.necessitylabs.litert.ui.theme.LiteRtTheme
import dagger.hilt.android.AndroidEntryPoint

/** Route strings for the two navigation destinations. */
private object Routes {
    const val INFERENCE = "inference"
    const val CHAT = "chat"
}

/**
 * The single activity that hosts the entire app.
 *
 * Hilt injects ViewModels into composables transitively via [AndroidEntryPoint];
 * no direct injection is performed in this class.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiteRtTheme {
                LiteRtApp()
            }
        }
    }
}

/**
 * Root composable that sets up navigation and the bottom bar.
 *
 * Separated from [MainActivity.onCreate] so it can be previewed and tested
 * independently without a real Activity context.
 */
@Composable
private fun LiteRtApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                // ── Inference tab ─────────────────────────────────────────────
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.BarChart,
                            contentDescription = stringResource(R.string.tab_inference),
                        )
                    },
                    label = { Text(stringResource(R.string.tab_inference)) },
                    selected = currentDestination?.hierarchy?.any {
                        it.route == Routes.INFERENCE
                    } == true,
                    onClick = {
                        navController.navigate(Routes.INFERENCE) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )

                // ── Chat tab ──────────────────────────────────────────────────
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Chat,
                            contentDescription = stringResource(R.string.tab_chat),
                        )
                    },
                    label = { Text(stringResource(R.string.tab_chat)) },
                    selected = currentDestination?.hierarchy?.any {
                        it.route == Routes.CHAT
                    } == true,
                    onClick = {
                        navController.navigate(Routes.CHAT) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.INFERENCE,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.INFERENCE) { InferenceScreen() }
            composable(Routes.CHAT) { ChatScreen() }
        }
    }
}
