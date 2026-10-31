package uz.orbitx.arport.engine.input

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import uz.orbitx.arport.engine.gestures.GestureEvent
import uz.orbitx.arport.engine.gestures.GestureType
import uz.orbitx.arport.engine.window.SpatialWindow
import uz.orbitx.arport.engine.window.WindowManager3D
import kotlin.math.abs

/**
 * Translates 3D spatial gestures and ray intersections into 2D Android MotionEvents,
 * and executes window transformations (move, resize, aspect cycle).
 */
class InputInjector {

    private var downTimeMs: Long = 0L
    private var isPointerDown: Boolean = false

    companion object {
        /**
         * Converts UV plane coordinates [0..1, 0..1] to Android 2D View pixel coordinates.
         * Note: v=1 is top in SpatialWindow, whereas y=0 is top in Android View coordinates.
         */
        fun uvToPixelCoordinates(u: Float, v: Float, pixelWidth: Int, pixelHeight: Int): Pair<Float, Float> {
            val clampedU = u.coerceIn(0f, 1f)
            val clampedV = v.coerceIn(0f, 1f)
            val px = clampedU * pixelWidth.toFloat()
            val py = (1.0f - clampedV) * pixelHeight.toFloat()
            return Pair(px, py)
        }

        /**
         * Calculates delta movement in world meters given UV deltas and window dimensions.
         */
        fun uvDeltaToMeters(deltaU: Float, deltaV: Float, windowWidth: Float, windowHeight: Float): Pair<Float, Float> {
            return Pair(deltaU * windowWidth, deltaV * windowHeight)
        }
    }

    /**
     * Injects a tap gesture as a DOWN + UP MotionEvent sequence into the target View.
     */
    fun injectTap(view: View?, u: Float, v: Float, width: Int, height: Int) {
        if (view == null || width <= 0 || height <= 0) return
        val (px, py) = uvToPixelCoordinates(u, v, width, height)
        val now = SystemClock.uptimeMillis()

        try {
            val downEvent = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, px, py, 0)
            view.dispatchTouchEvent(downEvent)
            downEvent.recycle()

            val upEvent = MotionEvent.obtain(now, now + 10L, MotionEvent.ACTION_UP, px, py, 0)
            view.dispatchTouchEvent(upEvent)
            upEvent.recycle()
        } catch (_: Exception) {
            // Safe fallback if mocked environment
        }
    }

    /**
     * Injects a move/scroll gesture as MotionEvent.ACTION_MOVE into the target View.
     */
    fun injectScroll(view: View?, u: Float, v: Float, width: Int, height: Int) {
        if (view == null || width <= 0 || height <= 0) return
        val (px, py) = uvToPixelCoordinates(u, v, width, height)
        val now = SystemClock.uptimeMillis()

        try {
            if (!isPointerDown) {
                downTimeMs = now
                val downEvent = MotionEvent.obtain(downTimeMs, now, MotionEvent.ACTION_DOWN, px, py, 0)
                view.dispatchTouchEvent(downEvent)
                downEvent.recycle()
                isPointerDown = true
            }

            val moveEvent = MotionEvent.obtain(downTimeMs, now, MotionEvent.ACTION_MOVE, px, py, 0)
            view.dispatchTouchEvent(moveEvent)
            moveEvent.recycle()
        } catch (_: Exception) {
            // Safe fallback
        }
    }

    /**
     * Releases pointer if active.
     */
    fun releasePointer(view: View?, u: Float, v: Float, width: Int, height: Int) {
        if (!isPointerDown) return
        val (px, py) = uvToPixelCoordinates(u, v, width, height)
        val now = SystemClock.uptimeMillis()

        try {
            val upEvent = MotionEvent.obtain(downTimeMs, now, MotionEvent.ACTION_UP, px, py, 0)
            view?.dispatchTouchEvent(upEvent)
            upEvent.recycle()
        } catch (_: Exception) {
            // Safe fallback
        } finally {
            isPointerDown = false
        }
    }

    /**
     * Handles 3D spatial window transformations (Drag, Depth Move, Corner Resize).
     */
    fun handleWindowGesture(event: GestureEvent, windowManager: WindowManager3D): Boolean {
        val window = event.window ?: return false

        when (event.type) {
            GestureType.DRAG -> {
                // Translate window in X and Y
                window.setPosition(
                    window.posX + event.deltaX,
                    window.posY + event.deltaY,
                    window.posZ
                )
                return true
            }

            GestureType.DEPTH_MOVE -> {
                // Translate window along Z axis (clamped between -0.8m and -4.0m)
                val newZ = (window.posZ + event.deltaZ).coerceIn(-4.0f, -0.8f)
                window.setPosition(window.posX, window.posY, newZ)
                return true
            }

            GestureType.CORNER_RESIZE -> {
                // Resize window keeping or adjusting aspect
                val newW = (window.widthMeters + event.deltaX).coerceIn(0.4f, 3.5f)
                val newH = (window.heightMeters - event.deltaY).coerceIn(0.25f, 2.5f)
                window.setSize(newW, newH)
                return true
            }

            GestureType.TWO_HAND_ZOOM -> {
                // Zoom window uniformly
                val newW = (window.widthMeters * event.scaleFactor).coerceIn(0.4f, 3.5f)
                val newH = (window.heightMeters * event.scaleFactor).coerceIn(0.25f, 2.5f)
                window.setSize(newW, newH)
                return true
            }

            GestureType.TAP -> {
                when (event.component) {
                    SpatialWindow.WindowComponent.CLOSE_BUTTON -> {
                        windowManager.closeWindow(window.id)
                        return true
                    }
                    SpatialWindow.WindowComponent.MINIMIZE_BUTTON -> {
                        windowManager.minimizeWindow(window.id)
                        return true
                    }
                    SpatialWindow.WindowComponent.RESIZE_HANDLE -> {
                        // Cycle aspect ratio
                        val nextAspect = if (window.aspect == uz.orbitx.arport.engine.window.WindowAspect.LOCKED_16_9) {
                            uz.orbitx.arport.engine.window.WindowAspect.LOCKED_9_16
                        } else {
                            uz.orbitx.arport.engine.window.WindowAspect.LOCKED_16_9
                        }
                        window.updateAspect(nextAspect)
                        return true
                    }
                    else -> {
                        windowManager.setFocus(window.id)
                        return true
                    }
                }
            }

            else -> return false
        }
    }
}

