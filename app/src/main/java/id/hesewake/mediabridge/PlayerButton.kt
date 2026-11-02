package id.hesewake.mediabridge

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Icons drawn as paths, with the full View kept as a large touch target. */
internal class PlayerButton(context: Context, private val action: String) : View(context) {
    var playing = false
        set(value) { if (field != value) { field = value; invalidate() } }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    init { isClickable = true; isFocusable = true }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val save = canvas.save()
        val scale = resources.displayMetrics.density * if (action == "play") 1.7f else 1.15f
        canvas.translate(width / 2f, height / 2f); canvas.scale(scale, scale)
        fun triangle(x: Float, reversed: Boolean) {
            val p = Path()
            if (reversed) { p.moveTo(x+6, -9f); p.lineTo(x-5, 0f); p.lineTo(x+6, 9f) }
            else { p.moveTo(x-6, -9f); p.lineTo(x+5, 0f); p.lineTo(x-6, 9f) }
            p.close(); canvas.drawPath(p, ink)
        }
        when (action) {
            "previous" -> { triangle(-5f, true); triangle(5f, true) }
            "next" -> { triangle(-5f, false); triangle(5f, false) }
            else -> if (playing) { canvas.drawRoundRect(-7f, -9f, -2f, 9f, 1f, 1f, ink); canvas.drawRoundRect(2f, -9f, 7f, 9f, 1f, 1f, ink) } else triangle(1f, false)
        }
        canvas.restoreToCount(save)
    }
}
