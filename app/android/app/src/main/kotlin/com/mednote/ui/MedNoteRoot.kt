package com.mednote.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mednote.data.SettingsStore
import com.mednote.data.VisitStore
import com.mednote.service.AudioRecorderService
import com.mednote.service.VisitPipeline
import com.mednote.ui.screens.CallScreen
import com.mednote.ui.screens.HomeScreen
import com.mednote.ui.screens.RecordScreen
import com.mednote.ui.screens.SettingsScreen
import com.mednote.ui.screens.VisitDetailScreen
import com.mednote.ui.screens.VisitsScreen

sealed class Tab(val route: String, val label: String) {
    data object Home : Tab("home", "Home")
    data object Call : Tab("call", "Call")
    data object Record : Tab("record", "Record")
    data object Visits : Tab("visits", "Visits")
    data object Settings : Tab("settings", "Settings")
}

@Composable
fun MedNoteRoot(
    settingsStore: SettingsStore,
    visitStore: VisitStore,
    audioRecorder: AudioRecorderService,
    visitPipeline: VisitPipeline
) {
    val navController = rememberNavController()
    val tabs = listOf(Tab.Home, Tab.Call, Tab.Record, Tab.Visits, Tab.Settings)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in tabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        Tab.Home -> Icons.Default.Home
                                        Tab.Call -> Icons.Default.Phone
                                        Tab.Record -> Icons.Default.Mic
                                        Tab.Visits -> Icons.Default.List
                                        Tab.Settings -> Icons.Default.Settings
                                    },
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Tab.Home.route) {
                HomeScreen(
                    visitStore = visitStore,
                    settingsStore = settingsStore,
                    onOpenCall = { navController.navigate(Tab.Call.route) },
                    onOpenRecord = { navController.navigate(Tab.Record.route) },
                    onOpenVisit = { id -> navController.navigate("visit/$id") }
                )
            }
            composable(Tab.Call.route) {
                CallScreen(
                    settingsStore = settingsStore,
                    visitStore = visitStore,
                    audioRecorder = audioRecorder,
                    visitPipeline = visitPipeline,
                    onVisitReady = { id -> navController.navigate("visit/$id") }
                )
            }
            composable(Tab.Record.route) {
                RecordScreen(
                    settingsStore = settingsStore,
                    visitStore = visitStore,
                    audioRecorder = audioRecorder,
                    visitPipeline = visitPipeline,
                    onVisitReady = { id -> navController.navigate("visit/$id") }
                )
            }
            composable(Tab.Visits.route) {
                VisitsScreen(
                    visitStore = visitStore,
                    onOpenVisit = { id -> navController.navigate("visit/$id") }
                )
            }
            composable(Tab.Settings.route) {
                SettingsScreen(settingsStore = settingsStore)
            }
            composable("visit/{visitId}") { entry ->
                val id = entry.arguments?.getString("visitId") ?: return@composable
                VisitDetailScreen(visitId = id, visitStore = visitStore)
            }
        }
    }
}
