package com.muthu.disciplinex.ui.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.muthu.disciplinex.ui.home.HomeScreen
import com.muthu.disciplinex.ui.onboarding.ExerciseOption
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
}

@Composable
fun DisciplineXNavGraph() {
    val navController: NavHostController = rememberNavController()

    // Held in memory only for M1 — persisted via Room/DataStore in a later milestone
    var wakeTime by remember { mutableStateOf("5:00 AM") }
    var duration by remember { mutableStateOf("20 min") }
    var exercise by remember { mutableStateOf(ExerciseOption("Push-ups", true)) }

    NavHost(navController = navController, startDestination = Routes.WELCOME) {
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
            ExerciseTypeScreen(onNext = { option ->
                exercise = option
                navController.navigate(Routes.PERMISSIONS)
            })
        }
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(onFinish = { navController.navigate(Routes.HOME) })
        }
        composable(Routes.HOME) {
            HomeScreen(wakeTime = wakeTime, duration = duration, exerciseName = exercise.name)
        }
    }
}
