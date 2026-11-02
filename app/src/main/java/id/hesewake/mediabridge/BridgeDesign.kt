package id.hesewake.mediabridge

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.TextView

/** Small native View helpers; no animated assets or additional UI runtime. */
internal object BridgeDesign {
    val background = Color.rgb(16, 17, 23)
    val surface = Color.rgb(28, 30, 40)
    val inset = Color.rgb(37, 40, 54)
    val line = Color.rgb(53, 56, 72)
    val primary = Color.rgb(190, 203, 255)
    val white = Color.rgb(245, 246, 252)
    val muted = Color.rgb(164, 168, 187)
    val dim = Color.rgb(111, 116, 137)
    val green = Color.rgb(142, 221, 186)

    fun dp(c: Context, value: Int): Int = (value * c.resources.displayMetrics.density + .5f).toInt()
    fun shape(color: Int, radius: Float, stroke: Int? = null, c: Context): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = radius * c.resources.displayMetrics.density
        if (stroke != null) setStroke(dp(c, 1), stroke)
    }
    /** Translucent layered surface; the softened artwork behind it remains visible. */
    fun glass(c: Context, radius: Float = 22f, prominent: Boolean = false): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        if (prominent) intArrayOf(0x66ffffff, 0x30ffffff, 0x20ffffff) else intArrayOf(0x40ffffff, 0x1effffff, 0x18ffffff)
    ).apply { cornerRadius = radius * c.resources.displayMetrics.density; setStroke(dp(c, 1), 0x55ffffff) }
    fun glassRipple(c: Context, radius: Float = 22f, prominent: Boolean = false): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(0x44ffffff), glass(c, radius, prominent), null)
    fun text(c: Context, value: String, size: Float, color: Int = white, bold: Boolean = false): TextView = TextView(c).apply {
        text = value; textSize = size; setTextColor(color); includeFontPadding = false
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    fun column(c: Context): LinearLayout = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
    fun row(c: Context): LinearLayout = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    fun systemInsets(view: View, left: Int, top: Int, right: Int, bottom: Int) {
        view.setOnApplyWindowInsetsListener { target, insets ->
            val bars = if (android.os.Build.VERSION.SDK_INT >= 30) insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()) else null
            @Suppress("DEPRECATION")
            target.setPadding(left + (bars?.left ?: insets.systemWindowInsetLeft), top + (bars?.top ?: insets.systemWindowInsetTop), right + (bars?.right ?: insets.systemWindowInsetRight), bottom + (bars?.bottom ?: insets.systemWindowInsetBottom))
            insets
        }
        view.requestApplyInsets()
    }
    fun gap(parent: LinearLayout, height: Int) { parent.addView(View(parent.context), LinearLayout.LayoutParams(1, dp(parent.context, height))) }
    fun card(c: Context, color: Int = surface, border: Int? = null, padding: Int = 18): LinearLayout = column(c).apply {
        background = GlassSurface.background(this, 22f); setPadding(dp(c, padding), dp(c, padding), dp(c, padding), dp(c, padding))
    }
    fun action(c: Context, label: String, filled: Boolean = false, onClick: () -> Unit): TextView = text(c, label, 14f, white, true).apply {
        gravity = Gravity.CENTER; minHeight = dp(c, 50); setPadding(dp(c, 14), 0, dp(c, 14), 0)
        background = GlassSurface.background(this, 18f, filled)
        isClickable = true; isFocusable = true; setOnClickListener { onClick() }
    }
    fun rowAction(c: Context, label: String, glyph: String, onClick: () -> Unit): LinearLayout = row(c).apply {
        minimumHeight = dp(c, 54); isClickable = true; isFocusable = true
        background = RippleDrawable(ColorStateList.valueOf(0x22ffffff), shape(Color.TRANSPARENT, 14f, null, c), null)
        addView(Glyph(c, glyph, primary), LinearLayout.LayoutParams(dp(c, 24), dp(c, 24)))
        addView(text(c, label, 14f, white, true), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(c, 12) })
        addView(Glyph(c, "chevron", dim), LinearLayout.LayoutParams(dp(c, 20), dp(c, 20)))
        setOnClickListener { onClick() }
    }
}

/** Simple, scalable line symbols, all drawn using the same rounded stroke. */
internal class Glyph(context: Context, private val kind: String, private val ink: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val save = canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
        canvas.scale((width - paddingLeft - paddingRight) / 24f, (height - paddingTop - paddingBottom) / 24f)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = canvas.drawLine(x1, y1, x2, y2, paint)
        fun rect(l: Float, t: Float, r: Float, b: Float, radius: Float = 2f) = canvas.drawRoundRect(l, t, r, b, radius, radius, paint)
        fun path(vararg points: Float) { val p = Path(); p.moveTo(points[0], points[1]); for (i in 2 until points.size step 2) p.lineTo(points[i], points[i + 1]); canvas.drawPath(p, paint) }
        when (kind) {
            "phone" -> { rect(7f, 2.5f, 17f, 21.5f, 2.4f); line(10f, 5f, 14f, 5f); line(11f, 19f, 13f, 19f) }
            "laptop" -> { rect(3f, 4.5f, 21f, 16f); line(1.5f, 19f, 22.5f, 19f); line(9f, 19f, 15f, 19f) }
            "music" -> { line(15f, 4f, 15f, 16f); line(15f, 4f, 20f, 3f); line(20f, 3f, 20f, 13f); canvas.drawOval(8f, 14f, 15f, 19f, paint); canvas.drawOval(13f, 11f, 20f, 16f, paint) }
            "qr" -> { rect(3f, 3f, 10f, 10f, 1f); rect(14f, 3f, 21f, 10f, 1f); rect(3f, 14f, 10f, 21f, 1f); line(15f, 15f, 15f, 18f); line(18f, 15f, 21f, 15f); line(18f, 19f, 21f, 19f) }
            "shield" -> { path(12f, 2f, 20f, 5f, 20f, 11f, 18f, 16f, 12f, 21f, 6f, 16f, 4f, 11f, 4f, 5f, 12f, 2f); path(8f, 11f, 11f, 14f, 16f, 9f) }
            "refresh" -> { canvas.drawArc(4f, 4f, 20f, 20f, 35f, 285f, false, paint); path(16f, 3f, 20f, 4f, 19f, 8f) }
            "chevron" -> path(9f, 6f, 15f, 12f, 9f, 18f)
            "disconnect" -> { line(6f, 6f, 18f, 18f); canvas.drawArc(4f, 4f, 20f, 20f, 45f, 270f, false, paint) }
            "dots" -> { canvas.drawCircle(6f, 12f, 1f, paint); canvas.drawCircle(12f, 12f, 1f, paint); canvas.drawCircle(18f, 12f, 1f, paint) }
        }
        canvas.restoreToCount(save)
    }
}

/** Quiet connection diagram used as a static illustration. */
internal class ConnectionArtwork(context: Context) : View(context) {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.2f; strokeCap = Paint.Cap.ROUND; color = 0xff8f9ed2.toInt() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val save = canvas.save(); val scale = minOf(width / 260f, height / 104f); canvas.translate((width - 260f * scale) / 2f, 0f); canvas.scale(scale, scale)
        fill.color = 0xff292d42.toInt(); canvas.drawRoundRect(5f, 8f, 85f, 96f, 19f, 19f, fill); canvas.drawRoundRect(175f, 8f, 255f, 96f, 19f, 19f, fill)
        canvas.drawRoundRect(33f, 29f, 57f, 72f, 5f, 5f, stroke); canvas.drawLine(41f, 66f, 49f, 66f, stroke)
        canvas.drawRoundRect(191f, 31f, 239f, 62f, 4f, 4f, stroke); canvas.drawLine(187f, 68f, 243f, 68f, stroke)
        stroke.color = 0xff65709a.toInt(); stroke.strokeWidth = 2f
        canvas.drawLine(96f, 52f, 108f, 52f, stroke); canvas.drawLine(152f, 52f, 164f, 52f, stroke)
        fill.color = 0xffb5c3ff.toInt(); canvas.drawCircle(117f, 52f, 2.5f, fill); canvas.drawCircle(130f, 52f, 2.5f, fill); canvas.drawCircle(143f, 52f, 2.5f, fill)
        stroke.color = 0xff8f9ed2.toInt(); stroke.strokeWidth = 2.2f; canvas.restoreToCount(save)
    }
}
