package uz.orbitx.arport.engine.apps

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import uz.orbitx.arport.engine.registry.AppManifest
import uz.orbitx.arport.engine.registry.AppRegistry
import uz.orbitx.arport.engine.window.ContentSurface
import uz.orbitx.arport.engine.window.SpatialWindow
import uz.orbitx.arport.engine.window.WindowAspect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HomeCategory(val title: String) {
    ALL("Barchasi"),
    WEB("🌐 Web"),
    GAMES("🎮 O'yinlar"),
    TOOLS("🛠️ Asboblar")
}

/**
 * 3D Floating Home Screen launcher inspired by Apple Vision Pro.
 * Displays category tabs, live clock/battery status, and an interactive grid
 * of installed apps that launch into world-locked spatial windows.
 */
class SpatialHomePanel(
    private val appRegistry: AppRegistry
) {
    var isVisible: Boolean = false
    var activeCategory: HomeCategory = HomeCategory.ALL
    var onAppSelected: ((AppManifest) -> Unit)? = null

    var homeWindow: SpatialWindow? = null
        private set

    companion object {
        const val SURFACE_WIDTH = 1280
        const val SURFACE_HEIGHT = 720
        const val GRID_COLS = 5
        const val GRID_ROWS = 3
    }

    /**
     * Creates and configures the SpatialWindow for Home panel.
     */
    fun createHomeWindow(): SpatialWindow {
        val window = SpatialWindow(
            appId = "spatial_home",
            title = "Bosh Ekran",
            posX = 0.0f,
            posY = 0.05f,
            posZ = -1.50f,
            widthMeters = 1.15f,
            heightMeters = 0.647f,
            aspect = WindowAspect.LOCKED_16_9
        ).apply {
            contentSurface = ContentSurface(SURFACE_WIDTH, SURFACE_HEIGHT)
        }
        homeWindow = window
        isVisible = true
        renderHomeContent()
        return window
    }

    fun getFilteredApps(): List<AppManifest> {
        return when (activeCategory) {
            HomeCategory.ALL -> appRegistry.allApps
            HomeCategory.WEB -> appRegistry.getAppsByCategory("media") + appRegistry.getAppsByCategory("social") + appRegistry.allApps.filter { it.id == "google" }
            HomeCategory.GAMES -> appRegistry.getAppsByCategory("games")
            HomeCategory.TOOLS -> appRegistry.getAppsByCategory("tools") + appRegistry.getAppsByCategory("system")
        }
    }

    /**
     * Hit tests UV coordinate against category tabs and app grid.
     */
    fun onHomeHit(u: Float, v: Float): Any? {
        // In UV: v=1 is top, v=0 is bottom
        // Header tabs: v in 0.84 .. 0.94
        if (v in 0.82f..0.94f) {
            val tabAction = hitCategoryTab(u)
            if (tabAction != null) {
                activeCategory = tabAction
                renderHomeContent()
                return tabAction
            }
        }

        // App grid area: v in 0.06 .. 0.80
        if (v in 0.06f..0.80f) {
            val app = hitAppGrid(u, v)
            if (app != null) {
                onAppSelected?.invoke(app)
                return app
            }
        }

        return null
    }

    private fun hitCategoryTab(u: Float): HomeCategory? {
        val categories = HomeCategory.values()
        val tabW = 0.60f / categories.size
        val startU = 0.20f
        if (u < startU || u > startU + (tabW * categories.size)) return null

        val index = ((u - startU) / tabW).toInt().coerceIn(0, categories.size - 1)
        return categories[index]
    }

    fun hitAppGrid(u: Float, v: Float): AppManifest? {
        val apps = getFilteredApps()
        if (apps.isEmpty()) return null

        // Grid boundaries in UV
        // X: 0.05 .. 0.95 (width 0.90)
        // Y: 0.10 .. 0.78 (height 0.68)
        if (u < 0.05f || u > 0.95f || v < 0.08f || v > 0.80f) return null

        val colWidth = 0.90f / GRID_COLS
        val rowHeight = 0.72f / GRID_ROWS

        val col = ((u - 0.05f) / colWidth).toInt().coerceIn(0, GRID_COLS - 1)
        // Note: in UV v=0 is bottom, so row 0 (top) is at highest v
        val invertedV = 0.80f - v
        val row = (invertedV / rowHeight).toInt().coerceIn(0, GRID_ROWS - 1)

        val appIndex = row * GRID_COLS + col
        return if (appIndex in apps.indices) apps[appIndex] else null
    }

    /**
     * Renders Home panel content (tabs, status bar, app cards).
     */
    fun renderHomeContent() {
        val surface = homeWindow?.contentSurface ?: return
        val apps = getFilteredApps()

        surface.draw { canvas ->
            // Background with dark Liquid Glass
            canvas.drawColor(Color.parseColor("#E615161C"))

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // 1. Header Bar: Clock, Title, Battery
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            paint.color = Color.WHITE
            paint.textSize = 34f
            paint.isFakeBoldText = true
            canvas.drawText("AR Port", 50f, 65f, paint)

            paint.color = Color.parseColor("#8E8E93")
            paint.textSize = 22f
            paint.isFakeBoldText = false
            canvas.drawText("• Fazoviy Bosh Ekran", 185f, 65f, paint)

            paint.color = Color.parseColor("#30D158") // Green status
            paint.textSize = 24f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("⚡ 100%  •  $timeStr", SURFACE_WIDTH - 50f, 65f, paint)
            paint.textAlign = Paint.Align.LEFT

            // 2. Category Tabs
            val categories = HomeCategory.values()
            val tabStartX = 260f
            val tabWidth = 190f
            val tabY = 110f

            val tabBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
            val tabBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }

            for (i in categories.indices) {
                val cat = categories[i]
                val rect = RectF(tabStartX + (i * (tabWidth + 12f)), tabY - 32f, tabStartX + ((i + 1) * tabWidth + (i * 12f)), tabY + 20f)
                val isSelected = (cat == activeCategory)

                tabBgPaint.color = if (isSelected) Color.parseColor("#4D0A84FF") else Color.parseColor("#26FFFFFF")
                tabBorderPaint.color = if (isSelected) Color.parseColor("#0A84FF") else Color.parseColor("#33FFFFFF")

                canvas.drawRoundRect(rect, 24f, 24f, tabBgPaint)
                canvas.drawRoundRect(rect, 24f, 24f, tabBorderPaint)

                paint.color = if (isSelected) Color.parseColor("#64D2FF") else Color.WHITE
                paint.textSize = 22f
                paint.isFakeBoldText = isSelected
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(cat.title, rect.centerX(), rect.centerY() + 7f, paint)
            }
            paint.textAlign = Paint.Align.LEFT

            // 3. App Grid Cards
            val gridStartX = 60f
            val gridStartY = 175f
            val gridTotalW = SURFACE_WIDTH - 120f
            val cardW = (gridTotalW / GRID_COLS) - 20f
            val cardH = 155f

            val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2B2C33")
            }
            val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#33FFFFFF")
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }
            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = 52f
            }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                color = Color.WHITE
                textSize = 22f
                isFakeBoldText = true
            }
            val typeBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                color = Color.parseColor("#8E8E93")
                textSize = 17f
            }

            for (i in apps.indices) {
                if (i >= GRID_COLS * GRID_ROWS) break
                val app = apps[i]
                val col = i % GRID_COLS
                val row = i / GRID_COLS

                val left = gridStartX + (col * (cardW + 20f))
                val top = gridStartY + (row * (cardH + 20f))
                val rect = RectF(left, top, left + cardW, top + cardH)

                // Draw Glass Squircle Card
                canvas.drawRoundRect(rect, 24f, 24f, cardBgPaint)
                canvas.drawRoundRect(rect, 24f, 24f, cardBorderPaint)

                val centerX = rect.centerX()

                // Icon
                canvas.drawText(app.icon, centerX, top + 64f, iconPaint)

                // Title
                val name = app.getLocalizedName("uz")
                val shortName = if (name.length > 14) name.take(12) + ".." else name
                canvas.drawText(shortName, centerX, top + 106f, titlePaint)

                // Type Badge
                canvas.drawText(app.type.uppercase(), centerX, top + 134f, typeBadgePaint)
            }
        }
    }
}

