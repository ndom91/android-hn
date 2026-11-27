package app.hn2.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import app.hn2.R
import kotlin.math.ceil

/** The design's title font, Inter Bold (SIL Open Font License, see licenses/). */
val InterBold = FontFamily(Font(R.font.inter_bold, FontWeight.Bold))

/** The design's monospace font, JetBrains Mono (SIL Open Font License, see licenses/). */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/** One stretch of text in a [MonoText] image. */
data class MonoRun(val text: String, val color: Color, val bold: Boolean = false)

/** A drawn piece of text and the size, in dp, to show it at. */
class MonoImage(val bitmap: Bitmap, val widthDp: Float, val heightDp: Float)

/**
 * Draws text in JetBrains Mono into bitmaps for the widget. Widgets can only use the phone's own
 * fonts, and a custom system font can replace even "monospace", so the design's monospaced header
 * and rank numbers are shown as images to look the same on every phone.
 */
class MonoText(context: Context) {
    private val regular = ResourcesCompat.getFont(context, R.font.jetbrains_mono_regular)
    private val bold = ResourcesCompat.getFont(context, R.font.jetbrains_mono_bold)
    private val metrics = context.resources.displayMetrics

    /**
     * [mask] draws an alpha-only bitmap, a quarter of the size, for single-color text the widget
     * tints (the rank numbers). A widget's images all travel in one update, so this keeps it small.
     */
    fun draw(runs: List<MonoRun>, sizeSp: Int, mask: Boolean = false): MonoImage {
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat(), metrics)
        val paints = runs.map { run ->
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = if (run.bold) bold else regular
                textSize = px
                color = run.color.toArgb()
            }
        }
        val font = paints.first().fontMetrics
        val width = ceil(paints.zip(runs).sumOf { (paint, run) -> paint.measureText(run.text).toDouble() }).toInt()
        val height = ceil(font.descent - font.ascent).toInt()
        val bitmap = Bitmap.createBitmap(
            width.coerceAtLeast(1),
            height.coerceAtLeast(1),
            if (mask) Bitmap.Config.ALPHA_8 else Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        var x = 0f
        runs.zip(paints).forEach { (run, paint) ->
            canvas.drawText(run.text, x, -font.ascent, paint)
            x += paint.measureText(run.text)
        }
        return MonoImage(bitmap, width / metrics.density, height / metrics.density)
    }
}
