package com.muthu.disciplinex.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.muthu.disciplinex.alarm.AlarmScheduler
import com.muthu.disciplinex.data.ChallengeSession
import com.muthu.disciplinex.data.UserPrefs
import com.muthu.disciplinex.ui.alarms.AlarmEditScreen
import com.muthu.disciplinex.ui.challenge.ChallengeGate
import com.muthu.disciplinex.ui.home.HomeScreen
import com.muthu.disciplinex.ui.onboarding.PermissionsScreen
import com.muthu.disciplinex.ui.onboarding.WelcomeScreen
import com.muthu.disciplinex.ui.settings.AllowedAppsScreen
import com.muthu.disciplinex.ui.settings.HistoryScreen
import com.muthu.disciplinex.ui.settings.SettingsScreen

private object Routes {
    const val WELCOME = "welcome"
    const val PERMISSIONS = "permissions"
    const val HOME = "home"
    const val CHALLENGE = "challenge"
    const val SETTINGS = "settings"
    const val ALLOWED_APPS = "allowed_apps"
    const val HISTORY = "history"
    const val ALARM_EDIT = "alarm_edit/{id}"

    fun alarmEdit(id: Int) = "alarm_edit/$id"
}

@Composable
fun DisciplineXNavGraph(startChallenge: Boolean = false, challengeRequest: Int = 0) {
    val navController: NavHostController = rememberNavController()
    val context = LocalContext.current

    // Open straight into the challenge when an alarm sent us here, or when a
    // challenge is waiting / in progress (not when it was already missed).
    val challengePending = remember {
        val phase = ChallengeSession.phase(context)
        phase == ChallengeSession.Phase.WAITING || phase == ChallengeSession.Phase.IN_PROGRESS
    }
    val onboarded = UserPrefs.isOnboarded(context)
    val startDestination = when {
        onboarded && (startChallenge || challengePending) -> Routes.CHALLENGE
        onboarded -> Routes.HOME
        else -> Routes.WELCOME
    }

    // Make sure every enabled alarm is armed on each app launch (cheap and idempotent).
    LaunchedEffect(Unit) {
        if (UserPrefs.isOnboarded(context)) {
            AlarmScheduler.scheduleAll(context)
        }
    }

    // The activity was re-opened with "start challenge" while already running
    // (alarm start button / blocked-app redirect) — jump to the challenge screen.
    LaunchedEffect(challengeRequest) {
        if (challengeRequest > 0 && UserPrefs.isOnboarded(context)) {
            navController.navigate(Routes.CHALLENGE) { launchSingleTop = true }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.WELCOME) {
            WelcomeScreen(onGetStarted = { navController.navigate(Routes.PERMISSIONS) })
        }
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(
                onFinish = {
                    UserPrefs.setOnboarded(context)
                    AlarmScheduler.scheduleAll(context)
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onAddAlarm = { navController.navigate(Routes.alarmEdit(-1)) },
                onEditAlarm = { id -> navController.navigate(Routes.alarmEdit(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onStartChallenge = { navController.navigate(Routes.CHALLENGE) { launchSingleTop = true } }
            )
        }
        composable(
            Routes.ALARM_EDIT,
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { entry ->
            val id = entry.arguments?.getInt("id") ?: -1
            AlarmEditScreen(alarmId = id, onClose = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onOpenAllowedApps = { navController.navigate(Routes.ALLOWED_APPS) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ALLOWED_APPS) {
            AllowedAppsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CHALLENGE) {
            ChallengeGate(
                onComplete = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.CHALLENGE) { inclusive = true }
                    }
                },
                onClose = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.CHALLENGE) { inclusive = true }
                    }
                }
            )
        }
    }
}
