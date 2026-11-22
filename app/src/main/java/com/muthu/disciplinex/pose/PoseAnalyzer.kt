package com.muthu.disciplinex.pose

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

/**
 * Bridges CameraX frames into ML Kit's streaming pose detector and forwards
 * each result (or null on failure/no-body-found) to [onPose] on the main
 * thread (ML Kit's default listener thread). One analyzer per camera session; call [close] when the challenge
 * screen is torn down to release the detector.
 */
class PoseAnalyzer(
    private val onPose: (Pose?) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector: PoseDetector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    @Volatile
    private var closed = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (closed || mediaImage == null) {
            imageProxy.close()
            return
        }
        try {
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            detector.process(inputImage)
                .addOnSuccessListener { pose -> if (!closed) onPose(pose) }
                .addOnFailureListener { if (!closed) onPose(null) }
                .addOnCompleteListener { imageProxy.close() }
        } catch (e: Exception) {
            // The detector was closed between the check above and process(): drop this frame.
            imageProxy.close()
        }
    }

    fun close() {
        closed = true
        detector.close()
    }
}
