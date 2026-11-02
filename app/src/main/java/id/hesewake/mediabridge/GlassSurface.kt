package id.hesewake.mediabridge

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import java.lang.ref.WeakReference

/** Translucent controls let the screen backdrop show through. */
internal object GlassSurface {
    fun background(view: View, radius: Float = 22f, prominent: Boolean = false): Drawable {
        val glass = GlassDrawable(view, radius, prominent)
        return RippleDrawable(ColorStateList.valueOf(0x44ffffff), glass, null)
    }
    private class GlassDrawable(view: View, radius: Float, private val prominent: Boolean) : Drawable() {
        private val host = WeakReference(view)
        private val corner = radius * view.resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        override fun draw(canvas: Canvas) {
            val v = host.get() ?: return
            val rect = RectF(bounds)
            val save = canvas.save(); canvas.clipPath(android.graphics.Path().apply { addRoundRect(rect, corner, corner, android.graphics.Path.Direction.CW) })
            paint.color = 0x5520252d; canvas.drawRect(rect, paint)
            paint.alpha = 255
            paint.shader = LinearGradient(rect.left, rect.top, rect.right.coerceAtLeast(rect.left + 1), rect.bottom.coerceAtLeast(rect.top + 1), if (prominent) 0x38ffffff else 0x22ffffff, 0x0affffff, Shader.TileMode.CLAMP)
            canvas.drawRect(rect, paint); paint.shader = null; canvas.restoreToCount(save)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = v.resources.displayMetrics.density; paint.color = 0x33ffffff
            canvas.drawRoundRect(rect.left + paint.strokeWidth / 2, rect.top + paint.strokeWidth / 2, rect.right - paint.strokeWidth / 2, rect.bottom - paint.strokeWidth / 2, corner, corner, paint)
            paint.style = Paint.Style.FILL
        }
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Deprecated in Android") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
