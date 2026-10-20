package com.muthu.disciplinex.pose

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * Live front-camera preview that runs [exerciseName]'s pose-based rep
 * counter on every frame and reports each [RepUpdate] via [onUpdate].
 *
 * Caller (ChallengeScreen) owns the target-rep-reached decision — this
 * composable just streams honest rep counts and tracking state.
 */
@Composable
fun PoseCameraView(
    exerciseName: String,
    onUpdate: (RepUpdate) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Always call the latest callback, even though the camera is bound only once.
    val currentOnUpdate by rememberUpdatedState(onUpdate)
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val repCounter = remember(exerciseName) { repCounterFor(exerciseName) }

    // Don't leak the analysis thread when the camera view leaves the screen.
    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).also { previewView = it }
        }
    )

    DisposableEffect(previewView, exerciseName) {
        val view = previewView
        var poseAnalyzer: PoseAnalyzer? = null
        var cameraProviderRef: ProcessCameraProvider? = null

        if (view != null && repCounter != null) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProviderRef = cameraProvider

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(view.surfaceProvider)
                    }

                    val analyzer = PoseAnalyzer { pose ->
                        currentOnUpdate(repCounter.onFrame(pose))
                    }
                    poseAnalyzer = analyzer

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(analysisExecutor, analyzer) }

                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("PoseCameraView", "Failed to bind camera", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }

        onDispose {
            try {
                cameraProviderRef?.unbindAll()
                poseAnalyzer?.close()
            } catch (e: Exception) {
                Log.w("PoseCameraView", "cleanup failed", e)
            }
        }
    }
}
