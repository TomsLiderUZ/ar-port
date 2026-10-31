package uz.orbitx.arport.engine.hand

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import uz.orbitx.arport.engine.math.Quaternion
import uz.orbitx.arport.engine.render.FpsTracker
import uz.orbitx.arport.engine.tracking.OneEuroFilter3D
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.tan

/**
 * High-performance Hand Tracker utilizing Google MediaPipe Tasks Vision
 * running on GPU delegate with One-Euro smoothing and 3D ray projection.
 */
class HandTracker(
    private val context: Context,
    private val onHandsUpdated: ((List<TrackedHand>) -> Unit)? = null
) : ImageAnalysis.Analyzer {

    private var handLandmarker: HandLandmarker? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    val trackedHands = Array(2) { TrackedHand(it) }
    private val filters = Array(2) { Array(21) { OneEuroFilter3D(minCutoff = 1.0f, beta = 0.007f) } }

    val fpsTracker = FpsTracker()
    private val lock = Any()

    @Volatile
    var isInitialized = false
        private set

    init {
        setupHandLandmarker()
    }

    private fun setupHandLandmarker() {
        executor.execute {
            try {
                // Initialize MediaPipe with robust CPU delegate (XNNPACK) to ensure 100% stability across all chipsets
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath("models/hand_landmarker.task")
                    .setDelegate(Delegate.CPU)
                    .build()
                val options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumHands(2)
                    .setMinHandDetectionConfidence(0.5f)
                    .setMinHandPresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setResultListener { result, _ ->
                        processResult(result)
                    }
                    .setErrorListener { error ->
                        Log.e("HandTracker", "MediaPipe CPU error: ${error.message}")
                    }
                    .build()
                handLandmarker = HandLandmarker.createFromOptions(context, options)
                isInitialized = true
                Log.i("HandTracker", "MediaPipe HandLandmarker initialized successfully with CPU delegate.")
            } catch (e: Throwable) {
                Log.e("HandTracker", "Failed to initialize MediaPipe HandLandmarker: ${e.message}", e)
            }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val landmarker = handLandmarker
        val mediaImage = imageProxy.image

        if (!isInitialized || landmarker == null || mediaImage == null) {
            imageProxy.close()
            return
        }

        try {
            val timestampMs = SystemClock.uptimeMillis()
            val mpImage = MediaImageBuilder(mediaImage).build()
            val imageProcessingOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(imageProxy.imageInfo.rotationDegrees)
                .build()
            landmarker.detectAsync(mpImage, imageProcessingOptions, timestampMs)
            fpsTracker.onFrameRendered(imageProxy.imageInfo.timestamp)
        } catch (e: Exception) {
            Log.e("HandTracker", "Error analyzing frame: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    private fun processResult(result: HandLandmarkerResult) {
        val nowMs = System.currentTimeMillis()
        val timestampSec = nowMs / 1000.0

        synchronized(lock) {
            val detectedHands = result.landmarks()
            val handedness = result.handedness()

            // Reset detection flags
            for (hand in trackedHands) {
                hand.isDetected = false
            }

            for (i in detectedHands.indices) {
                if (i >= trackedHands.size) break

                val hand = trackedHands[i]
                val landmarksList = detectedHands[i]
                val handFilter = filters[i]

                hand.isDetected = true
                hand.lastSeenTimestampMs = nowMs

                // Determine left/right handedness
                if (i < handedness.size && handedness[i].isNotEmpty()) {
                    hand.isRightHand = handedness[i][0].categoryName().equals("Right", ignoreCase = true)
                    hand.confidence = handedness[i][0].score()
                }

                // 1. Filter landmarks with One-Euro Filter
                for (j in landmarksList.indices) {
                    if (j >= hand.landmarks.size) break
                    val rawLm = landmarksList[j]
                    val filtered = handFilter[j].filter(rawLm.x(), rawLm.y(), rawLm.z(), timestampSec)
                    hand.landmarks[j].x = filtered[0]
                    hand.landmarks[j].y = filtered[1]
                    hand.landmarks[j].z = filtered[2]
                }

                // 2. Compute 3D Pointer Ray in camera AR space
                val indexTip = hand.landmarks[TrackedHand.INDEX_FINGER_TIP]
                val indexMcp = hand.landmarks[TrackedHand.INDEX_FINGER_MCP]

                // Normalized Device Coordinates [-1..1]
                val ndcX = (indexTip.x * 2.0f) - 1.0f
                val ndcY = 1.0f - (indexTip.y * 2.0f)

                // Perspective projection ray (~65 deg vertical FOV)
                val tanHalfFovY = tan(Math.toRadians(65.0 * 0.5)).toFloat()
                val aspect = 16.0f / 9.0f
                val tanHalfFovX = tanHalfFovY * aspect

                val dirX = ndcX * tanHalfFovX
                val dirY = ndcY * tanHalfFovY
                val dirZ = -1.0f

                val len = kotlin.math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ)
                hand.rayOriginX = dirX * 0.45f // ~45cm in front of camera
                hand.rayOriginY = dirY * 0.45f
                hand.rayOriginZ = dirZ * 0.45f

                hand.rayDirX = dirX / len
                hand.rayDirY = dirY / len
                hand.rayDirZ = dirZ / len
            }

            onHandsUpdated?.invoke(trackedHands.filter { it.isVisible(nowMs) })
        }
    }

    fun getVisibleHands(): List<TrackedHand> {
        val nowMs = System.currentTimeMillis()
        synchronized(lock) {
            return trackedHands.filter { it.isVisible(nowMs) }
        }
    }

    fun release() {
        executor.execute {
            handLandmarker?.close()
            handLandmarker = null
            isInitialized = false
        }
        executor.shutdown()
    }
}

