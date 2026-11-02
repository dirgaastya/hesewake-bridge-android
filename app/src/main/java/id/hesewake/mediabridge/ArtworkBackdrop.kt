package id.hesewake.mediabridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.View
import android.os.SystemClock
import java.util.concurrent.Executors

/** A full-bleed sleeve fading into colors sampled from the same artwork. */
internal class ArtworkBackdrop(context: Context, private val showCover: Boolean = true) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var key: Bitmap? = null
    private var initialized = false
    private var artwork: Bitmap? = null
    private var blurred: Bitmap? = null
    private var color = Color.rgb(31, 38, 57)
    private var previous: Bitmap? = null
    private var previousBlur: Bitmap? = null
    private var transitionAt = 0L
    private var revision = 0
    fun update(image: Bitmap?) {
        if (initialized && key === image) return
        initialized = true
        key = image
        val ticket = ++revision
        worker.execute {
            val soft = image?.let { smoothBackdrop(it) }
            val tint = image?.let { bitmap ->
                val tiny = Bitmap.createScaledBitmap(bitmap, 1, 1, true)
                val c = tiny.getPixel(0, 0); if (tiny !== bitmap) tiny.recycle()
                Color.rgb((Color.red(c) * .30f).toInt(), (Color.green(c) * .30f).toInt(), (Color.blue(c) * .30f).toInt())
            } ?: Color.rgb(24, 29, 40)
            post {
                if (ticket != revision) return@post
                previous = artwork; previousBlur = blurred
                artwork = image; blurred = soft; color = tint; transitionAt = SystemClock.uptimeMillis()
                invalidate()
            }
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(color)
        val progress = ((SystemClock.uptimeMillis() - transitionAt) / 350f).coerceIn(0f, 1f)
        fun drawLayer(image: Bitmap?, soft: Bitmap?, alpha: Int) {
            paint.alpha = alpha
            soft?.let { canvas.drawBitmap(it, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), paint) }
            if (showCover) image?.let { bitmap ->
                val heroHeight = minOf(width.toFloat() * 1.1f, height * .58f)
                val targetRatio = width / heroHeight.coerceAtLeast(1f)
                val imageRatio = bitmap.width.toFloat() / bitmap.height
                val cropWidth = if (imageRatio > targetRatio) (bitmap.height * targetRatio).toInt() else bitmap.width
                val cropHeight = if (imageRatio < targetRatio) (bitmap.width / targetRatio).toInt() else bitmap.height
                val src = Rect((bitmap.width-cropWidth)/2, (bitmap.height-cropHeight)/2, (bitmap.width+cropWidth)/2, (bitmap.height+cropHeight)/2)
                val layer = canvas.saveLayer(0f, 0f, width.toFloat(), heroHeight, null)
                canvas.drawBitmap(bitmap, src, RectF(0f, 0f, width.toFloat(), heroHeight), paint)
                paint.alpha = 255
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                paint.shader = LinearGradient(0f, heroHeight * .56f, 0f, heroHeight, 0xffffffff.toInt(), 0x00ffffff, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, width.toFloat(), heroHeight, paint)
                paint.shader = null; paint.xfermode = null; canvas.restoreToCount(layer)
            }
        }
        if (progress < 1f) drawLayer(previous, previousBlur, 255)
        drawLayer(artwork, blurred, (255 * progress).toInt())
        paint.alpha = 255
        if (progress < 1f) postInvalidateOnAnimation() else { previous = null; previousBlur = null }
        paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat().coerceAtLeast(1f), intArrayOf(0x66000000, 0x22000000, (color and 0x00ffffff) or 0x99000000.toInt(), 0xe90d0f15.toInt()), floatArrayOf(0f, .25f, .61f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint); paint.shader = null
    }
    override fun onDetachedFromWindow() { revision++; super.onDetachedFromWindow() }
    companion object {
        private val worker = Executors.newSingleThreadExecutor()
        private fun smoothBackdrop(bitmap: Bitmap): Bitmap {
            val tiny = Bitmap.createScaledBitmap(bitmap, 16, 16, true)
            var pixels = IntArray(256); tiny.getPixels(pixels, 0, 16, 0, 0, 16, 16)
            if (tiny !== bitmap) tiny.recycle()
            repeat(5) {
                val next = IntArray(256)
                for (y in 0 until 16) for (x in 0 until 16) {
                    var red = 0; var green = 0; var blue = 0; var count = 0
                    for (dy in -1..1) for (dx in -1..1) {
                        val p = pixels[(y + dy).coerceIn(0, 15) * 16 + (x + dx).coerceIn(0, 15)]
                        red += Color.red(p); green += Color.green(p); blue += Color.blue(p); count++
                    }
                    next[y * 16 + x] = Color.rgb(red / count, green / count, blue / count)
                }
                pixels = next
            }
            return Bitmap.createBitmap(pixels, 16, 16, Bitmap.Config.ARGB_8888)
        }
    }
}
