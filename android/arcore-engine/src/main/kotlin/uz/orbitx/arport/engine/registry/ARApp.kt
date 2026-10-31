package uz.orbitx.arport.engine.registry

import android.content.Context
import uz.orbitx.arport.engine.window.ContentSurface

/**
 * Standard interface implemented by all native and sandboxed applications in AR Port (TZ 11.4).
 */
interface ARApp {
    val id: String
    val manifest: AppManifest

    /**
     * Initializes and returns the offscreen 2D ContentSurface for OpenGL projection.
     */
    fun createSurface(context: Context, width: Int, height: Int): ContentSurface

    fun onStart() {}
    fun onResume() {}
    fun onPause() {}
    fun onDestroy() {}
    fun onResize(width: Int, height: Int) {}

    /**
     * Handles pointer interaction events (laser ray tap, touch, drag).
     * @param u Normalized horizontal coordinate [0..1]
     * @param v Normalized vertical coordinate [0..1]
     * @param action MotionEvent action code (ACTION_DOWN, ACTION_MOVE, ACTION_UP)
     */
    fun onPointerEvent(u: Float, v: Float, action: Int) {}

    /**
     * Continuous per-frame animation and physics tick called from the OpenGL render loop.
     * @param dtSec Delta time in seconds since the last frame.
     */
    fun onUpdate(dtSec: Float) {}
}
