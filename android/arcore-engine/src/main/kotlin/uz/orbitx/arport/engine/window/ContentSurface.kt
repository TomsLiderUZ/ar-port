package uz.orbitx.arport.engine.window

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.opengl.GLES30
import android.opengl.GLUtils

/**
 * Offscreen rendering surface that draws 2D canvas content (views, text, WebView frames)
 * directly into an OpenGL 2D texture for 3D spatial window projection.
 */
class ContentSurface(
    val width: Int = 1280,
    val height: Int = 720
) {
    var textureId: Int = 0
        private set

    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null
    @Volatile
    private var isDirty = true

    init {
        try {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            canvas = Canvas(bitmap!!)
        } catch (_: Exception) {
            // Safe fallback for host JVM tests
        }
    }

    fun onSurfaceCreated() {
        if (textureId == 0) {
            val textures = IntArray(1)
            GLES30.glGenTextures(1, textures, 0)
            textureId = textures[0]

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

            bitmap?.let {
                GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, it, 0)
            }
            isDirty = false
        }
    }

    /**
     * Executes drawing commands on the offscreen canvas and flags texture for upload.
     */
    fun draw(drawBlock: (Canvas) -> Unit) {
        val c = canvas ?: return
        synchronized(this) {
            drawBlock(c)
            isDirty = true
        }
    }

    /**
     * Updates OpenGL texture if canvas content changed. Must be called on GL thread.
     */
    fun updateTextureIfNeeded() {
        if (!isDirty || textureId == 0 || bitmap == null) return

        synchronized(this) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
            GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, bitmap)
            isDirty = false
        }
    }

    fun release() {
        if (textureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }
        bitmap?.recycle()
        bitmap = null
        canvas = null
    }
}

