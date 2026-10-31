package uz.orbitx.arport.engine.tracking

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.Matrix
import android.os.Build
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import uz.orbitx.arport.engine.math.Quaternion

/**
 * High-performance motion tracking manager supporting both 3DoF (Rotation Vector)
 * and 6DoF (ARCore) tracking with low-pass smoothing, predictive extrapolation,
 * and instant recenter (drift correction) functionality.
 */
class TrackingManager(
    private val context: Context,
    private val prefer6DoF: Boolean = false
) : SensorEventListener {

    enum class TrackingState {
        INITIALIZING,
        TRACKING_3DOF,
        TRACKING_6DOF,
        UNAVAILABLE
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    private var rotationVectorSensor: Sensor? = null
    private var isSensorRegistered = false

    // State
    @Volatile
    var trackingState: TrackingState = TrackingState.INITIALIZING
        private set

    // Raw & Filtered Quaternions (Thread-safe)
    private val lock = Any()
    private val rawQuaternion = Quaternion()
    private val smoothedQuaternion = Quaternion()
    private val predictedQuaternion = Quaternion()
    private var recenterOffsetQuaternion = Quaternion.identity()

    // Camera 3D Position in meters
    private var posX: Float = 0f
    private var posY: Float = 0f
    private var posZ: Float = 0f

    // Recenter reference position
    private var recenterPosX: Float = 0f
    private var recenterPosY: Float = 0f
    private var recenterPosZ: Float = 0f

    // Timestamps for predictive tracking
    private var lastSensorTimestampNs: Long = 0
    private var angularVelocity = Quaternion()
    private val smoothingAlpha = 0.65f // Higher = faster response, lower = more smoothing
    private val predictionLookaheadSec = 0.016f // 16 ms lookahead (~1 frame at 60 FPS)

    // Cached temporary matrices
    private val tempSensorMatrix = FloatArray(16)
    private val tempRemappedMatrix = FloatArray(16)
    private val cameraViewMatrix = FloatArray(16)
    private val eyeTranslationMatrix = FloatArray(16)

    init {
        // Find best available rotation sensor
        rotationVectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

        val capabilities = DeviceEvaluator.assessCapabilities(context)
        val arCoreSupported = capabilities["arCoreStatus"] == "SUPPORTED"

        if (prefer6DoF && arCoreSupported) {
            trackingState = TrackingState.TRACKING_6DOF
        } else if (rotationVectorSensor != null) {
            trackingState = TrackingState.TRACKING_3DOF
        } else {
            trackingState = TrackingState.UNAVAILABLE
        }
    }

    fun start() {
        if (!isSensorRegistered && rotationVectorSensor != null) {
            sensorManager?.registerListener(
                this,
                rotationVectorSensor,
                SensorManager.SENSOR_DELAY_FASTEST
            )
            isSensorRegistered = true
        }
    }

    fun stop() {
        if (isSensorRegistered) {
            sensorManager?.unregisterListener(this)
            isSensorRegistered = false
        }
    }

    /**
     * Recenters tracking (drift correction).
     * The current heading (yaw) becomes forward (-Z), and camera position resets to origin.
     */
    fun recenter() {
        synchronized(lock) {
            // Extract yaw angle from current predicted orientation
            val currentYaw = predictedQuaternion.getYaw()
            // Create recenter yaw quaternion around Y axis
            recenterOffsetQuaternion = Quaternion.fromAxisAngle(0f, 1f, 0f, currentYaw)

            // Recenter position to origin
            recenterPosX = posX
            recenterPosY = posY
            recenterPosZ = posZ
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR &&
            event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR) {
            return
        }

        // Convert sensor rotation vector to 4x4 matrix
        SensorManager.getRotationMatrixFromVector(tempSensorMatrix, event.values)

        // Remap coordinate system according to display rotation
        val displayRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.rotation ?: Surface.ROTATION_90
        } else {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_90
        }
        when (displayRotation) {
            Surface.ROTATION_90 -> {
                SensorManager.remapCoordinateSystem(
                    tempSensorMatrix,
                    SensorManager.AXIS_Y,
                    SensorManager.AXIS_MINUS_X,
                    tempRemappedMatrix
                )
            }
            Surface.ROTATION_270 -> {
                SensorManager.remapCoordinateSystem(
                    tempSensorMatrix,
                    SensorManager.AXIS_MINUS_Y,
                    SensorManager.AXIS_X,
                    tempRemappedMatrix
                )
            }
            Surface.ROTATION_180 -> {
                SensorManager.remapCoordinateSystem(
                    tempSensorMatrix,
                    SensorManager.AXIS_MINUS_X,
                    SensorManager.AXIS_MINUS_Y,
                    tempRemappedMatrix
                )
            }
            else -> { // Surface.ROTATION_0 (Portrait)
                System.arraycopy(tempSensorMatrix, 0, tempRemappedMatrix, 0, 16)
            }
        }

        // Convert remapped matrix to quaternion
        val currentQ = Quaternion.fromRotationMatrix(tempRemappedMatrix)
        val nowNs = event.timestamp

        synchronized(lock) {
            val dtSec = if (lastSensorTimestampNs > 0) {
                ((nowNs - lastSensorTimestampNs) / 1_000_000_000.0f).coerceIn(0.001f, 0.1f)
            } else {
                0.016f
            }
            lastSensorTimestampNs = nowNs

            // 1. Low-pass filter (SLERP) to reduce high frequency jitter
            if (rawQuaternion.length() < 0.1f) {
                rawQuaternion.set(currentQ)
                smoothedQuaternion.set(currentQ)
            } else {
                rawQuaternion.set(currentQ)
                val smoothed = Quaternion.slerp(smoothedQuaternion, rawQuaternion, smoothingAlpha)
                smoothedQuaternion.set(smoothed)
            }

            // 2. Predictive tracking: calculate angular delta and extrapolate forward
            val deltaQ = smoothedQuaternion * predictedQuaternion.inverse()
            val lookaheadRatio = (predictionLookaheadSec / dtSec).coerceIn(0f, 2.0f)
            val predicted = Quaternion.slerp(Quaternion.identity(), deltaQ, lookaheadRatio) * smoothedQuaternion
            predictedQuaternion.set(predicted)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    /**
     * Updates 6DoF position and orientation when fed by ARCore or an external pose tracker.
     */
    fun update6DoFPose(tx: Float, ty: Float, tz: Float, qx: Float, qy: Float, qz: Float, qw: Float) {
        synchronized(lock) {
            posX = tx
            posY = ty
            posZ = tz
            val arQ = Quaternion(qx, qy, qz, qw).normalize()
            smoothedQuaternion.set(arQ)
            predictedQuaternion.set(arQ)
            trackingState = TrackingState.TRACKING_6DOF
        }
    }

    /**
     * Computes the 4x4 View Matrix for rendering.
     * @param outMatrix Resulting 16-element column-major matrix
     * @param eyeOffsetMeters Eye offset along camera local X axis (-IPD/2 for Left, +IPD/2 for Right)
     */
    fun getViewMatrix(outMatrix: FloatArray, eyeOffsetMeters: Float = 0f) {
        synchronized(lock) {
            // Apply recenter yaw correction: Q_world = Q_recenter.inverse() * Q_predicted
            val correctedQ = recenterOffsetQuaternion.inverse() * predictedQuaternion

            // Invert orientation for camera view matrix (camera moves opposite to world)
            val viewOrientationQ = correctedQ.conjugate()
            viewOrientationQ.toRotationMatrix(cameraViewMatrix, 0)

            // Relative camera position
            val relX = posX - recenterPosX
            val relY = posY - recenterPosY
            val relZ = posZ - recenterPosZ

            // Apply camera world translation
            Matrix.translateM(cameraViewMatrix, 0, -relX, -relY, -relZ)

            if (eyeOffsetMeters != 0f) {
                // Apply local eye shift: V_eye = T(-eyeOffset, 0, 0) * V_cam
                Matrix.setIdentityM(eyeTranslationMatrix, 0)
                Matrix.translateM(eyeTranslationMatrix, 0, -eyeOffsetMeters, 0f, 0f)
                Matrix.multiplyMM(outMatrix, 0, eyeTranslationMatrix, 0, cameraViewMatrix, 0)
            } else {
                System.arraycopy(cameraViewMatrix, 0, outMatrix, 0, 16)
            }
        }
    }

    /**
     * Computes the 4x4 Perspective Projection Matrix.
     */
    fun getProjectionMatrix(
        outMatrix: FloatArray,
        width: Int,
        height: Int,
        fovYDeg: Float = 60f,
        near: Float = 0.05f,
        far: Float = 50.0f
    ) {
        val aspect = if (height > 0) width.toFloat() / height.toFloat() else 1.0f
        Matrix.perspectiveM(outMatrix, 0, fovYDeg, aspect, near, far)
    }

    fun is6DoF(): Boolean = trackingState == TrackingState.TRACKING_6DOF

    fun getTrackingModeLabel(): String {
        return when (trackingState) {
            TrackingState.TRACKING_6DOF -> "6DoF (ARCore)"
            TrackingState.TRACKING_3DOF -> "3DoF (Giroskop)"
            TrackingState.INITIALIZING -> "Tayyorlanmoqda..."
            TrackingState.UNAVAILABLE -> "Sensor yo'q"
        }
    }
}

