package com.muthu.disciplinex.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.muthu.disciplinex.alarm.AlarmScheduler
import com.muthu.disciplinex.data.UserPrefs
import com.muthu.disciplinex.ui.challenge.ChallengeScreen
import com.muthu.disciplinex.ui.home.HomeScreen
import com.muthu.disciplinex.ui.onboarding.ExerciseTypeScreen
import com.muthu.disciplinex.ui.onboarding.PermissionsScreen
import com.muthu.disciplinex.ui.onboarding.WakeTimeScreen
import com.muthu.disciplinex.ui.onboarding.WelcomeScreen
import com.muthu.disciplinex.ui.settings.AllowedAppsScreen
import com.muthu.disciplinex.ui.settings.SettingsScreen

private object Routes {
    const val WELCOME = "welcome"
    const val WAKE_TIME = "wake_time"
    const val EXERCISE = "exercise"
    const val PERMISSIONS = "permissions"
    const val HOME = "home"
    const val CHALLENGE = "challenge"
    const val SETTINGS = "settings"
    const val ALLOWED_APPS = "allowed_apps"
}

@Composable
fun DisciplineXNavGraph(startChallenge: Boolean = false, challengeRequest: Int = 0) {
    val navController: NavHostController = rememberNavController()
    val context = LocalContext.current

    // Seeded from UserPrefs so HomeScreen shows real saved data after app restart
    var wakeTime by remember { mutableStateOf(UserPrefs.getWakeTime(context)) }
    var targetReps by remember { mutableStateOf(UserPrefs.getTargetReps(context)) }
    var durationMinutes by remember { mutableStateOf(UserPrefs.getDurationMinutes(context)) }
    var exerciseName by remember { mutableStateOf(UserPrefs.getExercise(context)) }

    val startDestination = when {
        startChallenge && UserPrefs.isOnboarded(context) -> Routes.CHALLENGE
        UserPrefs.isOnboarded(context) -> Routes.HOME
        else -> Routes.WELCOME
    }

    // PermissionsScreen.onFinish only schedules the alarm the first time onboarding
    // completes. On every later app launch (already onboarded), make sure the
    // alarm is (re)armed too — cheap and idempotent.
    LaunchedEffect(Unit) {
        if (UserPrefs.isOnboarded(context)) {
            AlarmScheduler.scheduleWakeAlarm(context)
        }
    }

    // The activity was re-opened with "start challenge" while already running
    // (alarm dismiss / blocked-app redirect) — jump to the challenge screen.
    LaunchedEffect(challengeRequest) {
        if (challengeRequest > 0 && UserPrefs.isOnboarded(context)) {
            navController.navigate(Routes.CHALLENGE) { launchSingleTop = true }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.WELCOME) {
            WelcomeScreen(onGetStarted = { navController.navigate(Routes.WAKE_TIME) })
        }
        composable(Routes.WAKE_TIME) {
            WakeTimeScreen(
                onNext = { time ->
                    wakeTime = time
                    navController.navigate(Routes.EXERCISE)
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.EXERCISE) {
            ExerciseTypeScreen(
                onNext = { option, reps, minutes ->
                    exerciseName = option.name
                    targetReps = reps
                    durationMinutes = minutes
                    navController.navigate(Routes.PERMISSIONS)
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(
                onFinish = {
                    UserPrefs.saveOnboarding(context, wakeTime, exerciseName, targetReps, durationMinutes)
                    AlarmScheduler.scheduleWakeAlarm(context)
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                wakeTime = wakeTime,
                exerciseName = exerciseName,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onSave = { time, exercise, reps, minutes ->
                    UserPrefs.saveOnboarding(context, time, exercise, reps, minutes)
                    wakeTime = time
                    exerciseName = exercise
                    targetReps = reps
                    durationMinutes = minutes
                    // New wake time -> replace the scheduled alarm.
                    AlarmScheduler.cancelWakeAlarm(context)
                    AlarmScheduler.scheduleWakeAlarm(context)
                    navController.popBackStack()
                },
                onOpenAllowedApps = { navController.navigate(Routes.ALLOWED_APPS) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.ALLOWED_APPS) {
            AllowedAppsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CHALLENGE) {
            // Read the target fresh from prefs so a change made on Home is always used.
            ChallengeScreen(
                exerciseName = exerciseName,
                targetReps = UserPrefs.getTargetReps(context),
                durationMinutes = UserPrefs.getDurationMinutes(context),
                onComplete = {
                    // Unlock blocked apps + record today as completed.
                    UserPrefs.completeChallenge(context)
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.CHALLENGE) { inclusive = true }
                    }
                }
            )
        }
    }
}
