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

private object Routes {
    const val WELCOME = "welcome"
    const val WAKE_TIME = "wake_time"
    const val EXERCISE = "exercise"
    const val PERMISSIONS = "permissions"
    const val HOME = "home"
    const val CHALLENGE = "challenge"
}

@Composable
fun DisciplineXNavGraph(startChallenge: Boolean = false) {
    val navController: NavHostController = rememberNavController()
    val context = LocalContext.current

    // Seeded from UserPrefs so HomeScreen shows real saved data after app restart
    var wakeTime by remember { mutableStateOf(UserPrefs.getWakeTime(context)) }
    var duration by remember { mutableStateOf(UserPrefs.getDuration(context)) }
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

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.WELCOME) {
            WelcomeScreen(onGetStarted = { navController.navigate(Routes.WAKE_TIME) })
        }
        composable(Routes.WAKE_TIME) {
            WakeTimeScreen(
                onNext = { time, dur ->
                    wakeTime = time
                    duration = dur
                    navController.navigate(Routes.EXERCISE)
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.EXERCISE) {
            ExerciseTypeScreen(
                onNext = { option ->
                    exerciseName = option.name
                    navController.navigate(Routes.PERMISSIONS)
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(
                onFinish = {
                    UserPrefs.saveOnboarding(context, wakeTime, duration, exerciseName)
                    AlarmScheduler.scheduleWakeAlarm(context)
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(wakeTime = wakeTime, duration = duration, exerciseName = exerciseName)
        }
        composable(Routes.CHALLENGE) {
            ChallengeScreen(
                exerciseName = exerciseName,
                duration = duration,
                onComplete = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.CHALLENGE) { inclusive = true }
                    }
                }
            )
        }
    }
}
