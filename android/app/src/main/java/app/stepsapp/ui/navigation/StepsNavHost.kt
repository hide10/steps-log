package app.stepsapp.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import app.stepsapp.ui.common.BackBar
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.stepsapp.ui.backup.BackupScreen
import app.stepsapp.ui.body.BodyScreen
import app.stepsapp.ui.home.HomeScreen
import app.stepsapp.ui.settings.SettingsScreen
import app.stepsapp.ui.share.ShareScreen
import app.stepsapp.ui.streak.StreakScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.STEPS, "歩数", Icons.Filled.DirectionsWalk),
    Tab(Routes.BODY, "からだ", Icons.Filled.Favorite),
    Tab(Routes.SETTINGS, "設定", Icons.Filled.Settings),
)

@Composable
fun StepsNavHost() {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val current = entry?.destination?.route

    Scaffold(
        bottomBar = {
            // バックアップ画面はタブの下位なのでタブ自体は出したまま
            NavigationBar {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                // タブを行き来しても履歴が積み上がらないようにする
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.STEPS,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.STEPS) {
                HomeScreen(
                    onOpenStreak = { navController.navigate(Routes.STREAK) },
                    onOpenShare = { navController.navigate(Routes.SHARE) },
                )
            }
            composable(Routes.STREAK) {
                BackBar("連続と記録", onBack = { navController.popBackStack() }) { m ->
                    StreakScreen(modifier = m)
                }
            }
            composable(Routes.BODY) { BodyScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(onOpenBackup = { navController.navigate(Routes.BACKUP) })
            }
            composable(Routes.BACKUP) {
                BackBar("バックアップ", onBack = { navController.popBackStack() }) { m ->
                    BackupScreen(modifier = m)
                }
            }
            composable(Routes.SHARE) {
                BackBar("共有", onBack = { navController.popBackStack() }) { m ->
                    ShareScreen(modifier = m)
                }
            }
        }
    }
}
