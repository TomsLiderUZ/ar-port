package uz.orbitx.arport.engine.input

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import uz.orbitx.arport.engine.window.ContentSurface
import uz.orbitx.arport.engine.window.SpatialWindow
import uz.orbitx.arport.engine.window.WindowAspect

enum class KeyboardLanguage {
    UZ,
    EN,
    RU
}

enum class KeyboardMode {
    ALPHA,
    NUMERIC,
    EMOJI
}

sealed class KeyAction {
    data class Text(val value: String) : KeyAction()
    object Backspace : KeyAction()
    object Enter : KeyAction()
    object Space : KeyAction()
    object ShiftToggle : KeyAction()
    object LangCycle : KeyAction()
    object ModeToggle : KeyAction()
    object EmojiToggle : KeyAction()
    object Close : KeyAction()
}

data class KeyDefinition(
    val label: String,
    val action: KeyAction,
    val relativeWidth: Float = 1.0f,
    val isAccent: Boolean = false
)

/**
 * 3D Spatial Floating Glass Keyboard with iOS Liquid Glass aesthetic.
 * Supports Uzbek (Latin with O', G', Sh, Ch), English, Russian, Numbers/Symbols, and Emoji.
 * Responsive to index finger laser tap and direct screen touch.
 */
class SpatialKeyboard(
    var language: KeyboardLanguage = KeyboardLanguage.UZ,
    var mode: KeyboardMode = KeyboardMode.ALPHA
) {
    var isShiftActive: Boolean = false
    var isVisible: Boolean = false
    var lastPressedLabel: String? = null
    var onTextInput: ((String) -> Unit)? = null
    var onKeyAction: ((KeyAction) -> Unit)? = null

    // Spatial window holding keyboard if attached
    var keyboardWindow: SpatialWindow? = null
        private set

    companion object {
        const val SURFACE_WIDTH = 1200
        const val SURFACE_HEIGHT = 480
    }

    /**
     * Initializes a floating spatial window for the keyboard.
     */
    fun createKeyboardWindow(): SpatialWindow {
        val window = SpatialWindow(
            appId = "spatial_keyboard",
            title = "Fazoviy Shisha Klaviatura",
            posX = 0.0f,
            posY = -0.42f,
            posZ = -1.25f,
            widthMeters = 0.96f,
            heightMeters = 0.384f,
            aspect = WindowAspect.FREE
        ).apply {
            contentSurface = ContentSurface(SURFACE_WIDTH, SURFACE_HEIGHT)
        }
        keyboardWindow = window
        isVisible = true
        renderKeyboardContent()
        return window
    }

    /**
     * Retrieves key matrix layout for current language and mode.
     */
    fun getCurrentLayout(): List<List<KeyDefinition>> {
        if (mode == KeyboardMode.NUMERIC) {
            return getNumericLayout()
        }
        if (mode == KeyboardMode.EMOJI) {
            return getEmojiLayout()
        }

        return when (language) {
            KeyboardLanguage.UZ -> getUzbekLayout()
            KeyboardLanguage.EN -> getEnglishLayout()
            KeyboardLanguage.RU -> getRussianLayout()
        }
    }

    private fun getUzbekLayout(): List<List<KeyDefinition>> {
        val s = isShiftActive
        return listOf(
            listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "G'", "O'").map {
                val t = if (s) it.uppercase() else it.lowercase()
                KeyDefinition(t, KeyAction.Text(t))
            },
            listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "SH", "CH").map {
                val t = if (s) it.uppercase() else it.lowercase()
                KeyDefinition(t, KeyAction.Text(t))
            },
            listOf(
                KeyDefinition("⇧", KeyAction.ShiftToggle, relativeWidth = 1.3f, isAccent = isShiftActive),
                KeyDefinition(if (s) "Z" else "z", KeyAction.Text(if (s) "Z" else "z")),
                KeyDefinition(if (s) "X" else "x", KeyAction.Text(if (s) "X" else "x")),
                KeyDefinition(if (s) "C" else "c", KeyAction.Text(if (s) "C" else "c")),
                KeyDefinition(if (s) "V" else "v", KeyAction.Text(if (s) "V" else "v")),
                KeyDefinition(if (s) "B" else "b", KeyAction.Text(if (s) "B" else "b")),
                KeyDefinition(if (s) "N" else "n", KeyAction.Text(if (s) "N" else "n")),
                KeyDefinition(if (s) "M" else "m", KeyAction.Text(if (s) "M" else "m")),
                KeyDefinition("⌫", KeyAction.Backspace, relativeWidth = 1.4f, isAccent = true)
            ),
            listOf(
                KeyDefinition("123", KeyAction.ModeToggle, relativeWidth = 1.2f),
                KeyDefinition("🌐 UZ", KeyAction.LangCycle, relativeWidth = 1.2f),
                KeyDefinition("Masofa", KeyAction.Space, relativeWidth = 4.2f),
                KeyDefinition("😀", KeyAction.EmojiToggle, relativeWidth = 1.0f),
                KeyDefinition("Kiritish ↵", KeyAction.Enter, relativeWidth = 1.8f, isAccent = true)
            )
        )
    }

    private fun getEnglishLayout(): List<List<KeyDefinition>> {
        val s = isShiftActive
        return listOf(
            listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P").map {
                val t = if (s) it.uppercase() else it.lowercase()
                KeyDefinition(t, KeyAction.Text(t))
            },
            listOf("A", "S", "D", "F", "G", "H", "J", "K", "L").map {
                val t = if (s) it.uppercase() else it.lowercase()
                KeyDefinition(t, KeyAction.Text(t))
            },
            listOf(
                KeyDefinition("⇧", KeyAction.ShiftToggle, relativeWidth = 1.4f, isAccent = isShiftActive),
                KeyDefinition(if (s) "Z" else "z", KeyAction.Text(if (s) "Z" else "z")),
                KeyDefinition(if (s) "X" else "x", KeyAction.Text(if (s) "X" else "x")),
                KeyDefinition(if (s) "C" else "c", KeyAction.Text(if (s) "C" else "c")),
                KeyDefinition(if (s) "V" else "v", KeyAction.Text(if (s) "V" else "v")),
                KeyDefinition(if (s) "B" else "b", KeyAction.Text(if (s) "B" else "b")),
                KeyDefinition(if (s) "N" else "n", KeyAction.Text(if (s) "N" else "n")),
                KeyDefinition(if (s) "M" else "m", KeyAction.Text(if (s) "M" else "m")),
                KeyDefinition("⌫", KeyAction.Backspace, relativeWidth = 1.4f, isAccent = true)
            ),
            listOf(
                KeyDefinition("123", KeyAction.ModeToggle, relativeWidth = 1.2f),
                KeyDefinition("🌐 EN", KeyAction.LangCycle, relativeWidth = 1.2f),
                KeyDefinition("Space", KeyAction.Space, relativeWidth = 4.4f),
                KeyDefinition("😀", KeyAction.EmojiToggle, relativeWidth = 1.0f),
                KeyDefinition("Enter ↵", KeyAction.Enter, relativeWidth = 1.8f, isAccent = true)
            )
        )
    }

    private fun getRussianLayout(): List<List<KeyDefinition>> {
        val s = isShiftActive
        return listOf(
            listOf("Й", "Ц", "У", "К", "Е", "Н", "Г", "Ш", "Щ", "З", "Х").map {
                val t = if (s) it.uppercase() else it.lowercase()
                KeyDefinition(t, KeyAction.Text(t))
            },
            listOf("Ф", "Ы", "В", "А", "П", "Р", "О", "Л", "Д", "Ж", "Э").map {
                val t = if (s) it.uppercase() else it.lowercase()
                KeyDefinition(t, KeyAction.Text(t))
            },
            listOf(
                KeyDefinition("⇧", KeyAction.ShiftToggle, relativeWidth = 1.2f, isAccent = isShiftActive),
                KeyDefinition(if (s) "Я" else "я", KeyAction.Text(if (s) "Я" else "я")),
                KeyDefinition(if (s) "Ч" else "ч", KeyAction.Text(if (s) "Ч" else "ч")),
                KeyDefinition(if (s) "С" else "с", KeyAction.Text(if (s) "С" else "с")),
                KeyDefinition(if (s) "М" else "м", KeyAction.Text(if (s) "М" else "м")),
                KeyDefinition(if (s) "И" else "и", KeyAction.Text(if (s) "И" else "и")),
                KeyDefinition(if (s) "Т" else "т", KeyAction.Text(if (s) "Т" else "т")),
                KeyDefinition(if (s) "Ь" else "ь", KeyAction.Text(if (s) "Ь" else "ь")),
                KeyDefinition(if (s) "Б" else "б", KeyAction.Text(if (s) "Б" else "б")),
                KeyDefinition(if (s) "Ю" else "ю", KeyAction.Text(if (s) "Ю" else "ю")),
                KeyDefinition("⌫", KeyAction.Backspace, relativeWidth = 1.3f, isAccent = true)
            ),
            listOf(
                KeyDefinition("123", KeyAction.ModeToggle, relativeWidth = 1.2f),
                KeyDefinition("🌐 RU", KeyAction.LangCycle, relativeWidth = 1.2f),
                KeyDefinition("Пробел", KeyAction.Space, relativeWidth = 4.2f),
                KeyDefinition("😀", KeyAction.EmojiToggle, relativeWidth = 1.0f),
                KeyDefinition("Ввод ↵", KeyAction.Enter, relativeWidth = 1.8f, isAccent = true)
            )
        )
    }

    private fun getNumericLayout(): List<List<KeyDefinition>> {
        return listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map { KeyDefinition(it, KeyAction.Text(it)) },
            listOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "\"").map { KeyDefinition(it, KeyAction.Text(it)) },
            listOf(
                KeyDefinition("#+=", KeyAction.Text("#"), relativeWidth = 1.3f),
                KeyDefinition(".", KeyAction.Text(".")),
                KeyDefinition(",", KeyAction.Text(",")),
                KeyDefinition("?", KeyAction.Text("?")),
                KeyDefinition("!", KeyAction.Text("!")),
                KeyDefinition("'", KeyAction.Text("'")),
                KeyDefinition("⌫", KeyAction.Backspace, relativeWidth = 1.5f, isAccent = true)
            ),
            listOf(
                KeyDefinition("ABC", KeyAction.ModeToggle, relativeWidth = 1.5f),
                KeyDefinition("🌐", KeyAction.LangCycle, relativeWidth = 1.2f),
                KeyDefinition("Space", KeyAction.Space, relativeWidth = 4.5f),
                KeyDefinition("😀", KeyAction.EmojiToggle, relativeWidth = 1.0f),
                KeyDefinition("Enter ↵", KeyAction.Enter, relativeWidth = 1.8f, isAccent = true)
            )
        )
    }

    private fun getEmojiLayout(): List<List<KeyDefinition>> {
        return listOf(
            listOf("😀", "😂", "🥰", "😎", "🤔", "👍", "🔥", "✨", "🚀", "❤️").map { KeyDefinition(it, KeyAction.Text(it)) },
            listOf("👓", "📱", "💻", "🎉", "⭐", "👌", "👏", "💪", "⚡", "☕").map { KeyDefinition(it, KeyAction.Text(it)) },
            listOf(
                KeyDefinition("ABC", KeyAction.ModeToggle, relativeWidth = 1.5f),
                KeyDefinition("🇺🇿", KeyAction.Text("🇺🇿")),
                KeyDefinition("🌟", KeyAction.Text("🌟")),
                KeyDefinition("🎮", KeyAction.Text("🎮")),
                KeyDefinition("🌐", KeyAction.Text("🌐")),
                KeyDefinition("🎵", KeyAction.Text("🎵")),
                KeyDefinition("⌫", KeyAction.Backspace, relativeWidth = 1.5f, isAccent = true)
            ),
            listOf(
                KeyDefinition("ABC", KeyAction.ModeToggle, relativeWidth = 1.5f),
                KeyDefinition("🌐", KeyAction.LangCycle, relativeWidth = 1.2f),
                KeyDefinition("Space", KeyAction.Space, relativeWidth = 4.5f),
                KeyDefinition("123", KeyAction.ModeToggle, relativeWidth = 1.2f),
                KeyDefinition("Enter ↵", KeyAction.Enter, relativeWidth = 1.8f, isAccent = true)
            )
        )
    }

    /**
     * Hit tests UV coordinate [0..1, 0..1] against current keyboard key bounds.
     */
    fun hitKey(u: Float, v: Float): KeyDefinition? {
        val rows = getCurrentLayout()
        val rowCount = rows.size
        // In UV: v=0 is bottom, v=1 is top.
        // Row 0 is at top (v: (rowCount-1)/rowCount .. 1.0)
        // Row rowCount-1 is at bottom (v: 0.0 .. 1/rowCount)
        val invertedV = 1.0f - v.coerceIn(0f, 1f)
        val rowIndex = (invertedV * rowCount).toInt().coerceIn(0, rowCount - 1)
        val row = rows[rowIndex]

        val totalWeight = row.sumOf { it.relativeWidth.toDouble() }.toFloat()
        var accumulatedWeight = 0f
        val targetU = u.coerceIn(0f, 1f)

        for (key in row) {
            val keyStartU = accumulatedWeight / totalWeight
            val keyEndU = (accumulatedWeight + key.relativeWidth) / totalWeight
            if (targetU in keyStartU..keyEndU) {
                return key
            }
            accumulatedWeight += key.relativeWidth
        }
        return null
    }

    /**
     * Handles key press triggered by index laser tap or screen touch.
     */
    fun onKeyHit(u: Float, v: Float): KeyAction? {
        val key = hitKey(u, v) ?: return null
        lastPressedLabel = key.label

        when (val action = key.action) {
            is KeyAction.Text -> {
                onTextInput?.invoke(action.value)
            }
            is KeyAction.Backspace -> {
                onKeyAction?.invoke(action)
            }
            is KeyAction.Enter -> {
                onKeyAction?.invoke(action)
            }
            is KeyAction.Space -> {
                onTextInput?.invoke(" ")
            }
            is KeyAction.ShiftToggle -> {
                isShiftActive = !isShiftActive
                renderKeyboardContent()
            }
            is KeyAction.LangCycle -> {
                language = when (language) {
                    KeyboardLanguage.UZ -> KeyboardLanguage.EN
                    KeyboardLanguage.EN -> KeyboardLanguage.RU
                    KeyboardLanguage.RU -> KeyboardLanguage.UZ
                }
                renderKeyboardContent()
            }
            is KeyAction.ModeToggle -> {
                mode = if (mode == KeyboardMode.ALPHA) KeyboardMode.NUMERIC else KeyboardMode.ALPHA
                renderKeyboardContent()
            }
            is KeyAction.EmojiToggle -> {
                mode = if (mode == KeyboardMode.EMOJI) KeyboardMode.ALPHA else KeyboardMode.EMOJI
                renderKeyboardContent()
            }
            is KeyAction.Close -> {
                isVisible = false
            }
        }

        renderKeyboardContent()
        return key.action
    }

    /**
     * Renders glass keycaps onto ContentSurface.
     */
    fun renderKeyboardContent() {
        val surface = keyboardWindow?.contentSurface ?: return
        val rows = getCurrentLayout()

        surface.draw { canvas ->
            // Clear background with translucent dark glass
            canvas.drawColor(Color.parseColor("#D918191E"))

            val rowCount = rows.size
            val rowHeight = SURFACE_HEIGHT.toFloat() / rowCount
            val margin = 6f

            val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#33FFFFFF")
                style = Paint.Style.FILL
            }
            val accentBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#550A84FF")
                style = Paint.Style.FILL
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#44FFFFFF")
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
            }

            for (r in 0 until rowCount) {
                val row = rows[r]
                val totalWeight = row.sumOf { it.relativeWidth.toDouble() }.toFloat()
                var currentX = margin

                val top = r * rowHeight + margin
                val bottom = (r + 1) * rowHeight - margin

                for (key in row) {
                    val keyWidth = ((SURFACE_WIDTH - (margin * 2)) * (key.relativeWidth / totalWeight)) - margin
                    val rect = RectF(currentX, top, currentX + keyWidth, bottom)

                    // Draw key background
                    val bg = if (key.isAccent) accentBgPaint else keyBgPaint
                    canvas.drawRoundRect(rect, 14f, 14f, bg)
                    canvas.drawRoundRect(rect, 14f, 14f, borderPaint)

                    // Text size calculation
                    textPaint.textSize = if (key.label.length > 2) 26f else 38f
                    val fontMetrics = textPaint.fontMetrics
                    val baseline = rect.centerY() - (fontMetrics.top + fontMetrics.bottom) / 2f
                    canvas.drawText(key.label, rect.centerX(), baseline, textPaint)

                    currentX += keyWidth + margin
                }
            }
        }
    }
}

