package id.hesewake.mediabridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.util.Base64
import android.view.View

/** The sweep is decorative: media is supplied by Android's media session listener. */
internal class MediaRadar(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var artwork: Bitmap? = null
    private var artworkKey: String? = null
    private var active = false

    fun update(encoded: String?, mediaActive: Boolean) {
        active = mediaActive
        if (encoded != artworkKey) {
            artworkKey = encoded
            artwork = encoded?.let { runCatching { val bytes = Base64.decode(it, Base64.DEFAULT); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() }
        }
        contentDescription = if (active) "Media aktif" else "Menunggu media aktif"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = minOf(width, height) * .45f
        if (radius <= 0) return
        val cx = width / 2f; val cy = height / 2f
        paint.style = Paint.Style.STROKE; paint.strokeWidth = resources.displayMetrics.density
        paint.color = 0x30aabbff
        for (factor in floatArrayOf(1f, .68f, .36f)) canvas.drawCircle(cx, cy, radius * factor, paint)
        paint.color = 0x18aabbff
        canvas.drawLine(cx - radius, cy, cx + radius, cy, paint); canvas.drawLine(cx, cy - radius, cx, cy + radius, paint)
        if (active) {
            paint.style = Paint.Style.FILL; paint.color = 0x18aabbff
            val angle = (SystemClock.uptimeMillis() % 8000L) * 360f / 8000f
            canvas.drawArc(cx - radius, cy - radius, cx + radius, cy + radius, angle - 35f, 35f, true, paint)
            postInvalidateDelayed(66)
        }
        val side = radius * .86f
        val rect = RectF(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2)
        val path = Path().apply { addRoundRect(rect, side * .17f, side * .17f, Path.Direction.CW) }
        canvas.save(); canvas.clipPath(path)
        paint.style = Paint.Style.FILL; paint.color = 0xff292e43.toInt(); canvas.drawRect(rect, paint)
        artwork?.let { bitmap ->
            val edge = minOf(bitmap.width, bitmap.height)
            val src = android.graphics.Rect((bitmap.width - edge) / 2, (bitmap.height - edge) / 2, (bitmap.width + edge) / 2, (bitmap.height + edge) / 2)
            paint.color = Color.WHITE; paint.isFilterBitmap = true; canvas.drawBitmap(bitmap, src, rect, paint)
        } ?: run {
            paint.color = 0xffc1cdff.toInt(); paint.textAlign = Paint.Align.CENTER; paint.textSize = side * .4f
            canvas.drawText("♫", cx, cy - (paint.ascent() + paint.descent()) / 2, paint)
        }
        canvas.restore()
    }
}
