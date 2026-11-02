package id.hesewake.mediabridge

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import android.view.MotionEvent

/** Discovery points are illustrative; mDNS provides no distance or direction. */
internal class DiscoveryRadar(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var names = emptyList<String>()
    var onDevice: ((Int) -> Unit)? = null
    fun update(devices: List<String>) { names = devices.take(3); invalidate() }
    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val radius = minOf(width, height) * .43f
        paint.style = Paint.Style.STROKE; paint.strokeWidth = resources.displayMetrics.density; paint.color = 0x447d92d0
        for (f in floatArrayOf(.34f, .67f, 1f)) canvas.drawCircle(cx, cy, radius * f, paint)
        paint.style = Paint.Style.FILL; paint.color = 0x257d92d0
        val angle = (SystemClock.uptimeMillis() % 6000L) * 360f / 6000f
        canvas.drawArc(cx-radius, cy-radius, cx+radius, cy+radius, angle-40, 40f, true, paint)
        paint.color = 0xffc3ceff.toInt(); canvas.drawCircle(cx, cy, 4f * resources.displayMetrics.density, paint)
        names.forEachIndexed { index, name ->
            val x = cx + radius * floatArrayOf(-.54f, .48f, .05f)[index]
            val y = cy + radius * floatArrayOf(-.32f, -.42f, .52f)[index]
            paint.color = 0xffb9c9ff.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f * resources.displayMetrics.density
            val d = resources.displayMetrics.density
            canvas.drawRoundRect(x-9*d, y-7*d, x+9*d, y+5*d, 2*d, 2*d, paint); canvas.drawLine(x-11*d, y+8*d, x+11*d, y+8*d, paint)
            paint.style = Paint.Style.FILL
            paint.textSize = 10f * resources.displayMetrics.scaledDensity; paint.textAlign = Paint.Align.CENTER
            canvas.drawText(if (name.length > 18) name.take(17) + "…" else name, x, y + 19f * resources.displayMetrics.density, paint)
        }
        if (isShown) postInvalidateDelayed(100)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val radius = minOf(width, height) * .43f
        names.indices.forEach { index ->
            val x = width / 2f + radius * floatArrayOf(-.54f, .48f, .05f)[index]
            val y = height / 2f + radius * floatArrayOf(-.32f, -.42f, .52f)[index]
            if (kotlin.math.abs(event.x - x) < 30 * resources.displayMetrics.density && kotlin.math.abs(event.y - y) < 30 * resources.displayMetrics.density) { performClick(); onDevice?.invoke(index); return true }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
