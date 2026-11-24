package com.muthu.disciplinex.ui.challenge

import com.muthu.disciplinex.data.rememberWalkSteps
import com.muthu.disciplinex.data.WalkSteps
import com.muthu.disciplinex.data.AppSettings
import com.muthu.disciplinex.ui.components.tour
import com.muthu.disciplinex.ui.components.LocalTourState
import com.muthu.disciplinex.ui.components.rememberTourState
import com.muthu.disciplinex.ui.components.TourStep
import com.muthu.disciplinex.ui.components.TourOverlay
import com.muthu.disciplinex.ui.components.GuideButton
import com.muthu.disciplinex.data.tr
import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.scale
import com.muthu.disciplinex.data.StreakStore
import java.time.LocalDate

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.muthu.disciplinex.data.ChallengeSession
import com.muthu.disciplinex.data.ExerciseKinds
import com.muthu.disciplinex.data.UserPrefs
import com.muthu.disciplinex.pose.RepUpdate
import com.muthu.disciplinex.pose.PoseCameraView
import com.muthu.disciplinex.ui.components.PrimaryPillButton
import com.muthu.disciplinex.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Small live readout under the camera (angle, "skipped: ..."). Shown only in DEBUGGABLE builds
 * (the Codemagic debug APK); signed release builds hide it. Nothing else about debugging changes.
 */
private fun isDebugBuild(context: android.content.Context): Boolean =
    (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

/**
 * Two kinds of challenge:
 *  - Timed (Reading / Meditation / Walking): a countdown that only runs while
 *    this screen is in front. No button adds progress; it completes only when
 *    the full time has been spent here.
 *  - Rep-based (Push-ups / Squats via camera, Jumping Jacks manual): finish
 *    the number of reps you set.
 */
@Composable
fun ChallengeScreen(
    exerciseName: String,
    targetReps: Int,
    durationMinutes: Int,
    targetMeters: Int = 500,
    onComplete: () -> Unit,
    onMissed: () -> Unit = {}
) {
    val timed = ExerciseKinds.isTimed(exerciseName)
    val distance = ExerciseKinds.isDistance(exerciseName)
    val tour = rememberTourState()
    var showTour by remember { mutableStateOf(false) }
    val steps = remember(showTour, AppSettings.language) {
        (if (distance) challengeTourStepsDistance() else challengeTourSteps(timed)).filter { tour.bounds.containsKey(it.key) }
    }

    CompositionLocalProvider(LocalTourState provides tour) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (distance) {
                WalkChallenge(targetMeters, onComplete, onMissed)
            } else if (timed) {
                TimedChallenge(exerciseName, durationMinutes, onComplete, onMissed)
            } else {
                RepChallenge(exerciseName, targetReps, onComplete, onMissed)
            }
            GuideButton(
                onClick = { showTour = true },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding()
            )
            if (showTour) {
                TourOverlay(steps, tour, onFinish = { showTour = false })
            }
        }
    }
}

private fun challengeTourSteps(timed: Boolean): List<TourStep> = listOf(
    TourStep(
        "deadline", tr("Time left", "மீதமுள்ள நேரம்"),
        tr("This is how long you have to finish. When it reaches zero the challenge is missed, your streak resets and your apps stay locked for 24 hours.",
            "முடிக்க உங்களுக்கு இருக்கும் நேரம் இது. இது 0 ஆனால் சவால் தவறியதாகிவிடும், தொடர்ச்சியான சாதனை 0 ஆகிவிடும், ஆப்கள் 24 மணி நேரம் பூட்டியே இருக்கும்.")
    ),
    TourStep(
        "ring", tr("Your progress", "உங்கள் முன்னேற்றம்"),
        if (timed) {
            tr("The ring fills while the timer runs. When it is full you can unlock your phone.",
                "டைமர் ஓடும்போது வளையம் நிரம்பும். முழுதாக நிரம்பியதும் போனை திறக்கலாம்.")
        } else {
            tr("The number is your reps so far. The ring fills as you go. When it is full you can unlock your phone.",
                "இந்த எண் இதுவரை நீங்கள் செய்த முறைகள். நீங்கள் செய்யச் செய்ய வளையம் நிரம்பும். முழுதாக நிரம்பியதும் போனை திறக்கலாம்.")
        }
    ),
    TourStep(
        "bottom", if (timed) tr("Stay on this screen", "இந்த திரையிலேயே இருங்கள்") else tr("How counting works", "எப்படி எண்ணப்படுகிறது"),
        if (timed) {
            tr("The timer only runs while this screen is open. Emergency? You can leave for up to 5 minutes and carry on. Longer than that and the timer starts again from zero.",
                "இந்த திரை திறந்திருக்கும்போது மட்டுமே டைமர் ஓடும். அவசரமா? 5 நிமிடம் வரை வெளியே சென்று திரும்பி தொடரலாம். அதைவிட அதிகமானால் டைமர் மீண்டும் 0-விலிருந்து தொடங்கும்.")
        } else {
            tr("Keep your whole body in the camera. Only full reps count. Emergency? You can leave for up to 5 minutes and carry on. Longer than that and your reps start again from zero.",
                "உங்கள் முழு உடலும் கேமராவில் தெரிய வேண்டும். முழுமையான முறைகள் மட்டுமே கணக்கில் வரும். அவசரமா? 5 நிமிடம் வரை வெளியே சென்று திரும்பி தொடரலாம். அதைவிட அதிகமானால் எண்ணிக்கை மீண்டும் 0-விலிருந்து தொடங்கும்.")
        }
    )
)

/**
 * Live "time left" for the alarm's time limit. When the limit runs out the
 * challenge is over: [onMissed] is called and the screen is closed.
 */
@Composable
private fun DeadlineBar(onMissed: () -> Unit) {
    val context = LocalContext.current
    val currentOnMissed by rememberUpdatedState(onMissed)
    var msLeft by remember { mutableStateOf(ChallengeSession.msLeftToFinish(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            if (ChallengeSession.phase(context) == ChallengeSession.Phase.MISSED) {
                currentOnMissed()
                break
            }
            msLeft = ChallengeSession.msLeftToFinish(context)
            delay(1000)
        }
    }

    val sec = (msLeft / 1000).toInt()
    val text = if (sec >= 3600) {
        String.format(Locale.US, "%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)
    } else {
        String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)
    }
    Text(
        text = tr("Time left to finish: $text", "முடிக்க மீதமுள்ள நேரம்: $text"),
        color = if (sec < 60) Danger else TextMuted,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.labelLarge
    )
}

/**
 * Leaving the app mid-challenge is allowed for [ChallengeSession.AWAY_GRACE_MS]
 * (an emergency). Return later than that and the progress is reset to zero
 * (the saved progress is cleared here; [onReset] clears what is on screen).
 */
@Composable
private fun AwayWatcher(onReset: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentOnReset by rememberUpdatedState(onReset)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> ChallengeSession.markLeft(context)
                Lifecycle.Event.ON_RESUME -> if (ChallengeSession.returnedAfterGrace(context)) currentOnReset()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun AwayResetNote(visible: Boolean, onHide: () -> Unit) {
    if (!visible) return
    LaunchedEffect(Unit) {
        delay(8000)
        onHide()
    }
    Text(
        text = tr("You were away for more than 5 minutes, so your progress started again from zero.", "நீங்கள் 5 நிமிடத்துக்கு மேல் வெளியே இருந்ததால், உங்கள் முன்னேற்றம் மீண்டும் 0-விலிருந்து தொடங்கியது."),
        color = Danger,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun TimedChallenge(exerciseName: String, durationMinutes: Int, onComplete: () -> Unit, onMissed: () -> Unit) {
    val context = LocalContext.current
    val totalMs = durationMinutes.coerceAtLeast(1) * 60_000L

    // Saved progress survives the app being closed or killed mid-challenge.
    var elapsedMs by remember {
        mutableStateOf(UserPrefs.getTimedProgressMs(context).coerceIn(0L, totalMs))
    }
    // Walking also needs real steps (if the phone can count them).
    val isWalking = exerciseName == "Walking"
    var permissionTick by remember { mutableStateOf(0) }
    val stepPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) permissionTick++
    }
    LaunchedEffect(isWalking) {
        if (isWalking && !WalkSteps.hasPermission(context)) {
            stepPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }
    val walkSteps = rememberWalkSteps(enabled = isWalking, refresh = permissionTick)
    val stepsNow = walkSteps.value
    val requiredSteps = WalkSteps.required(durationMinutes)
    val stepsMissing = if (isWalking && stepsNow != null) (requiredSteps - stepsNow).coerceAtLeast(0) else 0

    val timerDone = elapsedMs >= totalMs
    val isComplete = timerDone && stepsMissing == 0
    var awayReset by remember { mutableStateOf(false) }

    // Away for more than 5 minutes -> the timer (and the step count) start again from zero.
    if (!isComplete) {
        AwayWatcher(onReset = {
            elapsedMs = 0L
            awayReset = true
            if (walkSteps.value != null) walkSteps.value = 0
        })
    }

    // Keep the screen awake while the timer is on screen.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Time only counts while this screen is actually in front.
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isResumed = true
                Lifecycle.Event.ON_PAUSE -> isResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // A phone can show a second app in a small floating / split window while this
    // screen still counts as "resumed". Only count time while THIS window has focus.
    val activity = remember(context) { context.findActivity() }
    var windowActive by remember { mutableStateOf(true) }

    LaunchedEffect(isResumed, isComplete) {
        if (!isResumed || isComplete) return@LaunchedEffect
        var last = SystemClock.elapsedRealtime()
        var sinceSave = 0L
        try {
            while (true) {
                delay(250)
                val now = SystemClock.elapsedRealtime()
                val step = now - last
                last = now
                val focused = activity?.hasWindowFocus() ?: true
                val multiWindow = activity?.isInMultiWindowMode ?: false
                windowActive = focused && !multiWindow
                if (windowActive) {
                    elapsedMs = (elapsedMs + step).coerceAtMost(totalMs)
                }
                sinceSave += step
                if (sinceSave >= 5_000L) {
                    UserPrefs.setTimedProgressMs(context, elapsedMs)
                    sinceSave = 0L
                }
                if (elapsedMs >= totalMs) break
            }
        } finally {
            // Runs on pause (leaving the app), completion, and screen close.
            UserPrefs.setTimedProgressMs(context, elapsedMs)
        }
    }

    val remainingSec = ((totalMs - elapsedMs + 999) / 1000).coerceAtLeast(0)
    val timeText = String.format(Locale.US, "%02d:%02d", remainingSec / 60, remainingSec % 60)
    val progress by animateFloatAsState(
        targetValue = (elapsedMs.toFloat() / totalMs).coerceIn(0f, 1f),
        animationSpec = tween(250),
        label = "timedProgress"
    )

    Scaffold(containerColor = Background) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = ExerciseKinds.display(exerciseName),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tr("$durationMinutes min \u00b7 stay on this screen until the timer ends", "$durationMinutes நிமிடம் \u00b7 டைமர் முடியும் வரை இந்த திரையிலேயே இருங்கள்"),
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.tour("deadline")) { DeadlineBar(onMissed) }
            AwayResetNote(awayReset) { awayReset = false }
            if (isWalking) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (stepsNow != null) {
                        tr("Steps: $stepsNow / $requiredSteps", "அடிகள்: $stepsNow / $requiredSteps")
                    } else {
                        tr("Allow \"Physical activity\" so your steps can be checked.", "உங்கள் அடிகளை சரிபார்க்க \"Physical activity\" (உடல் செயல்பாடு) அனுமதியை கொடுங்கள்.")
                    },
                    color = TextMuted,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            ProgressRing(
                progress = progress,
                centerText = timeText,
                subText = if (isComplete) tr("done", "முடிந்தது") else tr("remaining", "மீதம்"),
                isComplete = isComplete,
                modifier = Modifier.tour("ring")
            )

            Spacer(modifier = Modifier.weight(1f))

            if (isComplete) {
                CompleteCelebration(modifier = Modifier.tour("bottom").padding(bottom = 16.dp))
                PrimaryPillButton(text = tr("Finish & Unlock Phone", "முடித்து போனை திற"), onClick = onComplete)
            } else {
                Text(
                    text = when {
                        timerDone && stepsMissing > 0 -> tr("Timer done \u2014 keep walking: $stepsMissing more steps.", "டைமர் முடிந்தது \u2014 தொடர்ந்து நடங்கள்: இன்னும் $stepsMissing அடிகள் மீதம்.")
                        !isResumed -> tr("Paused \u2014 come back to this screen to continue.", "இடைநிறுத்தப்பட்டது \u2014 தொடர இந்த திரைக்கு திரும்பி வாருங்கள்.")
                        !windowActive -> tr("Paused \u2014 another window is open on top. Close it to continue.", "இடைநிறுத்தப்பட்டது \u2014 மேலே வேறொரு திரை திறந்துள்ளது. தொடர அதை மூடுங்கள்.")
                        else -> tr("Timer running \u2014 leaving the app pauses it, and there's no skipping.", "டைமர் ஓடுகிறது \u2014 ஆப்பை விட்டு வெளியே சென்றால் அது நின்றுவிடும், தவிர்க்கவும் முடியாது.")
                    },
                    color = if (isResumed && windowActive) TextMuted else OrangeEnd,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.tour("bottom").padding(bottom = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RepChallenge(exerciseName: String, target: Int, onComplete: () -> Unit, onMissed: () -> Unit) {
    val context = LocalContext.current
    val usesCamera = ExerciseKinds.isCamera(exerciseName)

    // Progress is saved, so leaving the app (or the system killing it) mid-set
    // doesn't throw away the reps already done.
    var reps by remember(exerciseName) {
        mutableStateOf(UserPrefs.getRepProgress(context).coerceIn(0, target))
    }
    // The camera counter starts from 0 every time it is (re)created; track its
    // last value so only NEW reps are added on top of the saved total.
    var lastCounterReps by remember(exerciseName) { mutableStateOf(0) }
    var liveProgress by remember(exerciseName) { mutableStateOf(0f) }
    var isTracking by remember(exerciseName) { mutableStateOf(false) }
    var debugText by remember(exerciseName) { mutableStateOf("") }
    val isComplete = reps >= target
    var awayReset by remember { mutableStateOf(false) }
    // Camera exercises start with a short "get ready" countdown so you can place the phone
    // and step back. It is skipped when some reps are already saved (coming back to the screen).
    var ready by remember(exerciseName) { mutableStateOf(!usesCamera || reps > 0) }

    // Away for more than 5 minutes -> the count starts again from zero.
    if (!isComplete) {
        AwayWatcher(onReset = { reps = 0; awayReset = true })
    }

    // Phone is propped on the floor during push-ups: don't let the screen time out.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val displayProgress = if (usesCamera && !isComplete) liveProgress else (reps.toFloat() / target)
    val progress by animateFloatAsState(
        targetValue = displayProgress.coerceIn(0f, 1f),
        animationSpec = tween(200),
        label = "progress"
    )

    Scaffold(containerColor = Background) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = ExerciseKinds.display(exerciseName),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tr("$target reps \u00b7 complete to unlock your phone", "$target முறை \u00b7 முடித்தால் போன் திறக்கும்"),
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.tour("deadline")) { DeadlineBar(onMissed) }
            AwayResetNote(awayReset) { awayReset = false }

            Spacer(modifier = Modifier.height(20.dp))

            if (usesCamera && !isComplete && !ready) {
                GetReadyCountdown(
                    onDone = { ready = true },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else if (usesCamera && !isComplete) {
                CameraRepTracker(
                    exerciseName = exerciseName,
                    onUpdate = { update ->
                        // If the camera counter was restarted it counts from 0 again: don't
                        // let the old total swallow the new reps.
                        val newReps = if (update.reps >= lastCounterReps) update.reps - lastCounterReps else update.reps
                        lastCounterReps = update.reps
                        if (newReps > 0) {
                            reps = (reps + newReps).coerceAtMost(target)
                            UserPrefs.setRepProgress(context, reps)
                        }
                        liveProgress = update.liveProgress
                        isTracking = update.isTracking
                        debugText = update.debug
                    }
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            ProgressRing(
                progress = progress,
                centerText = "$reps",
                subText = tr("of $target reps", "/ $target முறை"),
                isComplete = isComplete,
                modifier = Modifier.tour("ring")
            )

            Spacer(modifier = Modifier.weight(1f))

            when {
                isComplete -> {
                    CompleteCelebration(modifier = Modifier.tour("bottom").padding(bottom = 16.dp))
                    PrimaryPillButton(
                        text = tr("Finish & Unlock Phone", "முடித்து போனை திற"),
                        onClick = onComplete
                    )
                }
                usesCamera -> {
                    if (isDebugBuild(LocalContext.current) && debugText.isNotBlank()) {
                        Text(
                            text = debugText,
                            color = TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    Text(
                        text = if (isTracking) tr("Keep going \u2014 full range of motion counts.", "தொடருங்கள் \u2014 முழு அசைவு மட்டுமே கணக்கில் வரும்.")
                        else tr("Get your whole body in frame to start counting.", "எண்ணத் தொடங்க உங்கள் முழு உடலும் கேமராவில் தெரிய வேண்டும்."),
                        color = if (isTracking) TextMuted else OrangeEnd,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.tour("bottom").padding(bottom = 16.dp)
                    )
                }
                else -> {
                    Text(
                        text = tr("Motion-sensor tracking for this exercise arrives next \u2014 tap to count a rep for now.", "இந்த பயிற்சிக்கான இயக்க சென்சார் கண்காணிப்பு விரைவில் வரும் \u2014 இப்போதைக்கு தட்டி, முறையை எண்ணுங்கள்."),
                        color = TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.tour("bottom").padding(bottom = 16.dp)
                    )
                    PrimaryPillButton(
                        text = tr("+1 Rep", "+1 முறை"),
                        onClick = {
                            reps = (reps + 1).coerceAtMost(target)
                            UserPrefs.setRepProgress(context, reps)
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = {
                        reps = 0
                        UserPrefs.setRepProgress(context, 0)
                    }) {
                        Text(tr("Reset", "மீட்டமை"), color = TextMuted)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Small live camera preview + permission gate. Falls back to a manual
 * "request camera access" prompt if permission was revoked after
 * onboarding, instead of silently showing a blank preview.
 */
@Composable
private fun CameraRepTracker(exerciseName: String, onUpdate: (RepUpdate) -> Unit) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (hasCameraPermission) {
            PoseCameraView(
                exerciseName = exerciseName,
                onUpdate = onUpdate,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    tr("Camera access is needed to count reps", "முறைகளை எண்ண கேமரா அனுமதி தேவை"),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryPillButton(
                    text = tr("Grant Camera Access", "கேமரா அனுமதியை கொடு"),
                    onClick = { launcher.launch(Manifest.permission.CAMERA) }
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(20.dp))
}

/** "Get ready" 5-4-3-2-1 before the camera starts counting reps. */
@Composable
private fun GetReadyCountdown(onDone: () -> Unit, modifier: Modifier = Modifier) {
    var left by remember { mutableStateOf(5) }
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left--
        }
        currentOnDone()
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = tr("Get ready", "தயாராகுங்கள்"),
            color = TextMuted,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "$left",
            color = OrangeEnd,
            fontWeight = FontWeight.Bold,
            fontSize = 72.sp
        )
        Text(
            text = tr("Place your phone and step back so your whole body is in the camera.", "போனை வைத்து, உங்கள் முழு உடலும் கேமராவில் தெரியும்படி பின்னால் நில்லுங்கள்."),
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        TextButton(onClick = { currentOnDone() }) {
            Text(tr("Start now", "இப்போதே தொடங்கு"), color = OrangeEnd, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Shown when the challenge is done: a little pop-in and the streak counting up. */
@Composable
private fun CompleteCelebration(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val streakBefore = remember { StreakStore.currentStreak(context) }
    val alreadyCounted = remember { StreakStore.status(context, LocalDate.now()) == StreakStore.DayStatus.DONE }
    val streakAfter = if (alreadyCounted) streakBefore else streakBefore + 1
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(200)
        shown = true
    }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "celebrateScale"
    )
    val count by animateIntAsState(
        targetValue = if (shown) streakAfter else streakBefore,
        animationSpec = tween(900),
        label = "streakCount"
    )
    Column(
        modifier = modifier.scale(scale),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("\uD83C\uDF89", fontSize = 36.sp)
        Text(
            text = tr("Challenge complete!", "சவால் முடிந்தது!"),
            color = Success,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = tr("\uD83D\uDD25 $count day streak", "\uD83D\uDD25 $count நாள் தொடர் சாதனை"),
            color = TextMuted,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ProgressRing(
    progress: Float,
    centerText: String,
    subText: String,
    isComplete: Boolean,
    modifier: Modifier = Modifier
) {
    val ringColor = if (isComplete) Success else OrangeEnd

    Box(
        modifier = modifier.size(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val stroke = 18.dp.toPx()
            // Track
            drawArc(
                color = Divider,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                size = Size(size.width - stroke, size.height - stroke),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
            )
            // Progress
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                size = Size(size.width - stroke, size.height - stroke),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerText,
                fontWeight = FontWeight.Bold,
                fontSize = 52.sp,
                color = TextPrimary
            )
            Text(
                text = subText,
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}


// ----------------------------------------------------------------------
// Walking: finished by walking a distance (phone step counter x step length).
// ----------------------------------------------------------------------

private fun challengeTourStepsDistance(): List<TourStep> = listOf(
    TourStep(
        "deadline", tr("Time left", "மீதமுள்ள நேரம்"),
        tr("This is how long you have to finish. When it reaches zero the challenge is missed, your streak resets and your apps stay locked for 24 hours.",
            "முடிக்க உங்களுக்கு இருக்கும் நேரம் இது. இது 0 ஆனால் சவால் தவறியதாகிவிடும், தொடர்ச்சியான சாதனை 0 ஆகிவிடும், ஆப்கள் 24 மணி நேரம் பூட்டியே இருக்கும்.")
    ),
    TourStep(
        "ring", tr("Distance walked", "நடந்த தூரம்"),
        tr("The ring fills as you walk. Distance is estimated from your phone's step count. When it is full you can unlock your phone.",
            "நீங்கள் நடக்க நடக்க வளையம் நிரம்பும். தூரம், உங்கள் போன் எண்ணும் அடிகளிலிருந்து தோராயமாக கணக்கிடப்படுகிறது. முழுதாக நிரம்பியதும் போனை திறக்கலாம்.")
    ),
    TourStep(
        "bottom", tr("Keep the phone with you", "போனை உங்களுடன் வைத்திருங்கள்"),
        tr("Steps are counted even when the screen is off. Walk, then come back here to finish.",
            "திரை அணைந்திருந்தாலும் அடிகள் எண்ணப்படும். நடந்துவிட்டு, முடிக்க இங்கே திரும்பி வாருங்கள்.")
    )
)

@Composable
private fun WalkChallenge(targetMeters: Int, onComplete: () -> Unit, onMissed: () -> Unit) {
    val context = LocalContext.current
    val target = targetMeters.coerceAtLeast(10)

    var permissionTick by remember { mutableStateOf(0) }
    val stepPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) permissionTick++
    }
    LaunchedEffect(Unit) {
        if (!WalkSteps.hasPermission(context)) {
            stepPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    val sensorReady = remember(permissionTick) { WalkSteps.isAvailable(context) }
    val walkSteps = rememberWalkSteps(enabled = sensorReady, refresh = permissionTick)
    val steps = walkSteps.value ?: 0
    val strideM = remember { WalkSteps.strideCm(context) / 100f }
    val walkedM = steps * strideM

    // Safety net: a phone with no step counter (or the permission refused) must never lock
    // someone in, so the distance is replaced by a slow-walk timer (1 m per second).
    val fallbackMs = target * 1000L
    var fallbackElapsedMs by remember {
        mutableStateOf(UserPrefs.getTimedProgressMs(context).coerceIn(0L, fallbackMs))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(sensorReady) {
        if (sensorReady) return@LaunchedEffect
        var last = SystemClock.elapsedRealtime()
        var sinceSave = 0L
        try {
            while (fallbackElapsedMs < fallbackMs) {
                delay(500)
                val now = SystemClock.elapsedRealtime()
                val step = now - last
                last = now
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    fallbackElapsedMs = (fallbackElapsedMs + step).coerceAtMost(fallbackMs)
                }
                sinceSave += step
                if (sinceSave >= 5_000L) {
                    UserPrefs.setTimedProgressMs(context, fallbackElapsedMs)
                    sinceSave = 0L
                }
            }
        } finally {
            UserPrefs.setTimedProgressMs(context, fallbackElapsedMs)
        }
    }

    val isComplete = if (sensorReady) walkedM >= target else fallbackElapsedMs >= fallbackMs
    val rawProgress = if (sensorReady) walkedM / target else fallbackElapsedMs.toFloat() / fallbackMs
    val progress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1f),
        animationSpec = tween(250),
        label = "walkProgress"
    )
    val centerText = if (sensorReady) {
        tr("${walkedM.toInt().coerceAtMost(target)} m", "${walkedM.toInt().coerceAtMost(target)} மீ")
    } else {
        val rem = ((fallbackMs - fallbackElapsedMs + 999) / 1000).coerceAtLeast(0)
        String.format(Locale.US, "%02d:%02d", rem / 60, rem % 60)
    }

    Scaffold(containerColor = Background) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = ExerciseKinds.display("Walking"),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tr("Walk ${ExerciseKinds.distanceText(target)} \u00b7 complete to unlock your phone", "${ExerciseKinds.distanceText(target)} நடங்கள் \u00b7 முடித்தால் போன் திறக்கும்"),
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.tour("deadline")) { DeadlineBar(onMissed) }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (sensorReady) {
                    tr("Steps: $steps \u00b7 distance is an estimate", "அடிகள்: $steps \u00b7 தூரம் தோராயமானது")
                } else {
                    if (WalkSteps.hasPermission(context)) {
                        tr("No step counter available \u2014 a timer replaces the distance.", "அடி எண்ணும் சென்சார் கிடைக்கவில்லை \u2014 தூரத்துக்கு பதிலாக டைமர் ஓடும்.")
                    } else {
                        tr("Physical activity is off \u2014 a timer replaces the distance. Tap Allow to count steps.", "உடல் செயல்பாடு அனுமதி இல்லை \u2014 தூரத்துக்கு பதிலாக டைமர் ஓடும். அடிகளை எண்ண அனுமதி என்பதை தட்டுங்கள்.")
                    }
                },
                color = TextMuted,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge
            )
            if (!sensorReady && !WalkSteps.hasPermission(context)) {
                androidx.compose.material3.TextButton(
                    onClick = { stepPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION) }
                ) {
                    Text(tr("Allow physical activity", "உடல் செயல்பாட்டை அனுமதி"), color = OrangeEnd, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            ProgressRing(
                progress = progress,
                centerText = centerText,
                subText = if (sensorReady) tr("of ${ExerciseKinds.distanceText(target)}", "/ ${ExerciseKinds.distanceText(target)}")
                else tr("remaining", "மீதம்"),
                isComplete = isComplete,
                modifier = Modifier.tour("ring")
            )

            Spacer(modifier = Modifier.weight(1f))

            if (isComplete) {
                CompleteCelebration(modifier = Modifier.tour("bottom").padding(bottom = 16.dp))
                PrimaryPillButton(text = tr("Finish & Unlock Phone", "முடித்து போனை திற"), onClick = onComplete)
            } else {
                Text(
                    text = if (sensorReady) {
                        tr("Walk with your phone \u2014 steps count even with the screen off.", "போனுடன் நடங்கள் \u2014 திரை அணைந்திருந்தாலும் அடிகள் எண்ணப்படும்.")
                    } else {
                        tr("Keep this screen open until the timer ends.", "டைமர் முடியும் வரை இந்த திரையை திறந்தே வையுங்கள்.")
                    },
                    color = TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.tour("bottom").padding(bottom = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
