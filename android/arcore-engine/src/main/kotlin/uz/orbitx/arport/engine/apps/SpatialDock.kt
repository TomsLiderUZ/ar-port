package uz.orbitx.arport.engine.apps

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import uz.orbitx.arport.engine.window.ContentSurface
import uz.orbitx.arport.engine.window.SpatialWindow
import uz.orbitx.arport.engine.window.WindowAspect

enum class SpatialDockAction {
    HOME,
    RECENTER,
    VOLUME,
    BRIGHTNESS,
    KEYBOARD,
    EXIT
}

data class DockButton(
    val action: SpatialDockAction,
    val icon: String,
    val title: String
)

/**
 * Vision Pro-inspired 3D floating bottom dock providing instant access to
 * Home launcher, Recenter, Volume, Brightness, Spatial Keyboard, and Exit AR.
 */
class SpatialDock {

    var isVisible: Boolean = true
    var lastTriggeredAction: SpatialDockAction? = null
    var onDockAction: ((SpatialDockAction) -> Unit)? = null

    var dockWindow: SpatialWindow? = null
        private set

    companion object {
        const val SURFACE_WIDTH = 960
        const val SURFACE_HEIGHT = 160

        val DOCK_BUTTONS = listOf(
            DockButton(SpatialDockAction.HOME, "🏠", "Home"),
            DockButton(SpatialDockAction.RECENTER, "🎯", "Markaz"),
            DockButton(SpatialDockAction.VOLUME, "🔊", "Ovoz"),
            DockButton(SpatialDockAction.BRIGHTNESS, "☀️", "Yorug'lik"),
            DockButton(SpatialDockAction.KEYBOARD, "⌨️", "Klaviatura"),
            DockButton(SpatialDockAction.EXIT, "✕", "Chiqish")
        )
    }

    /**
     * Creates and returns the SpatialWindow hosting this dock.
     */
    fun createDockWindow(): SpatialWindow {
        val window = SpatialWindow(
            appId = "spatial_dock",
            title = "Boshqaruv Docki",
            posX = 0.0f,
            posY = -0.42f,
            posZ = -1.30f,
            widthMeters = 0.78f,
            heightMeters = 0.13f,
            aspect = WindowAspect.FREE
        ).apply {
            contentSurface = ContentSurface(SURFACE_WIDTH, SURFACE_HEIGHT)
        }
        dockWindow = window
        renderDockContent()
        return window
    }

    /**
     * Hit tests UV coordinate [0..1, 0..1] against dock action buttons.
     */
    fun hitTest(u: Float, v: Float): SpatialDockAction? {
        val count = DOCK_BUTTONS.size
        val buttonWidth = 1.0f / count
        val index = (u / buttonWidth).toInt().coerceIn(0, count - 1)
        return DOCK_BUTTONS[index].action
    }

    /**
     * Triggers dock action on tap or ray hit.
     */
    fun onDockHit(u: Float, v: Float): SpatialDockAction? {
        val action = hitTest(u, v) ?: return null
        lastTriggeredAction = action
        onDockAction?.invoke(action)
        renderDockContent(highlightAction = action)
        return action
    }

    /**
     * Renders iOS Liquid Glass pill shape and action buttons onto ContentSurface.
     */
    fun renderDockContent(highlightAction: SpatialDockAction? = null) {
        val surface = dockWindow?.contentSurface ?: return

        surface.draw { canvas ->
            // Clear transparent background
            canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

            val pillRect = RectF(12f, 12f, SURFACE_WIDTH - 12f, SURFACE_HEIGHT - 12f)
            val pillRadius = (SURFACE_HEIGHT - 24f) / 2f

            // 1. Frosted Liquid Glass pill background
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E6181920") // Translucent dark glass
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(pillRect, pillRadius, pillRadius, bgPaint)

            // 2. Luminous border highlight
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#44FFFFFF")
                style = Paint.Style.STROKE
                strokeWidth = 2.0f
            }
            canvas.drawRoundRect(pillRect, pillRadius, pillRadius, borderPaint)

            // 3. Render Buttons
            val count = DOCK_BUTTONS.size
            val btnWidth = (SURFACE_WIDTH - 48f) / count
            val margin = 24f

            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = 48f
            }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = 20f
                color = Color.parseColor("#D1D1D6")
                isFakeBoldText = true
            }
            val highlightBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#4D0A84FF")
                style = Paint.Style.FILL
            }

            for (i in 0 until count) {
                val btn = DOCK_BUTTONS[i]
                val centerX = margin + (i + 0.5f) * btnWidth
                val centerY = SURFACE_HEIGHT / 2f

                // Active highlight indicator
                if (btn.action == highlightAction) {
                    val highlightRect = RectF(
                        centerX - (btnWidth * 0.42f),
                        centerY - 52f,
                        centerX + (btnWidth * 0.42f),
                        centerY + 52f
                    )
                    canvas.drawRoundRect(highlightRect, 24f, 24f, highlightBgPaint)
                }

                // Draw Icon
                canvas.drawText(btn.icon, centerX, centerY + 8f, iconPaint)

                // Draw Title
                titlePaint.color = if (btn.action == highlightAction) Color.parseColor("#64D2FF") else Color.parseColor("#D1D1D6")
                canvas.drawText(btn.title, centerX, centerY + 48f, titlePaint)
            }
        }
    }
}

