package id.hesewake.mediabridge

import android.app.Activity
import android.content.Intent
import android.hardware.Camera
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/** In-app camera preview; no QR contents are logged or retained. */
@Suppress("DEPRECATION")
class QrScannerActivity : Activity(), SurfaceHolder.Callback {
    private var camera: Camera? = null
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var active = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BridgeDesign.background
        window.navigationBarColor = BridgeDesign.background
        val container = FrameLayout(this)
        val view = SurfaceView(this); view.holder.addCallback(this)
        container.addView(view, FrameLayout.LayoutParams(-1, -1))
        container.addView(ScannerOverlay(this), FrameLayout.LayoutParams(-1, -1))
        val controls = BridgeDesign.column(this).apply { setPadding(dp(22), dp(16), dp(22), dp(24)) }
        BridgeDesign.systemInsets(controls, dp(22), dp(16), dp(22), dp(24))
        val header = BridgeDesign.row(this)
        header.addView(BridgeDesign.action(this, "‹") { finish() }, LinearLayout.LayoutParams(dp(50), dp(50)))
        header.addView(BridgeDesign.text(this, "Scan kode QR", 18f, BridgeDesign.white, true), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(14) })
        controls.addView(header)
        controls.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        controls.addView(BridgeDesign.text(this, "Arahkan ke kode QR", 20f, BridgeDesign.white, true).apply { gravity = Gravity.CENTER })
        BridgeDesign.gap(controls, 8)
        controls.addView(BridgeDesign.text(this, "Posisikan kode dari laptop di dalam kotak.", 13f, BridgeDesign.white).apply { gravity = Gravity.CENTER })
        container.addView(controls, FrameLayout.LayoutParams(-1, -1))
        setContentView(container)
    }
    override fun surfaceCreated(holder: SurfaceHolder) {
        try {
            val c = Camera.open(); camera = c; c.setDisplayOrientation(90); c.setPreviewDisplay(holder); c.startPreview(); active = true; scan()
        } catch (_: Exception) { setResult(RESULT_CANCELED); finish() }
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
    override fun surfaceDestroyed(holder: SurfaceHolder) { releaseCamera() }
    private fun scan() {
        val c = camera ?: return
        c.setOneShotPreviewCallback { data, cam ->
            if (!active || data == null) return@setOneShotPreviewCallback
            val size = cam.parameters.previewSize
            worker.execute {
                val result = runCatching {
                    val source = PlanarYUVLuminanceSource(data, size.width, size.height, 0, 0, size.width, size.height, false)
                    MultiFormatReader().apply { setHints(mapOf(DecodeHintType.TRY_HARDER to true)) }.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
                }.getOrNull()
                runOnUiThread {
                    if (!active) return@runOnUiThread
                    if (result != null && result.startsWith("hesewake-media:1:")) {
                        active = false; setResult(RESULT_OK, Intent().putExtra("qr", result)); finish()
                    } else scan()
                }
            }
        }
    }
    private fun releaseCamera() { active = false; camera?.let { runCatching { it.setPreviewCallback(null); it.stopPreview(); it.release() } }; camera = null }
    override fun onPause() { releaseCamera(); super.onPause() }
    override fun onDestroy() { releaseCamera(); worker.shutdownNow(); super.onDestroy() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}

private class ScannerOverlay(context: android.content.Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        val side = minOf(width * .72f, height * .5f)
        val left = (width - side) / 2f; val top = (height - side) / 2f
        paint.color = 0x88000000.toInt(); paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), top, paint)
        canvas.drawRect(0f, top, left, top + side, paint)
        canvas.drawRect(left + side, top, width.toFloat(), top + side, paint)
        canvas.drawRect(0f, top + side, width.toFloat(), height.toFloat(), paint)
        paint.color = 0xffc3ceff.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f * resources.displayMetrics.density
        canvas.drawRoundRect(left, top, left + side, top + side, 18f, 18f, paint)
    }
}
