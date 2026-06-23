package com.cyberviz

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.cyberviz.TextAnalyzer.TextRegion

class CyberVizOverlayView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    interface OnModeChangeListener {
        fun onModeChanged(mode: ProcessingMode, param: Float)
        fun onParamChanged(param: Float)
    }

    private var bitmap: Bitmap? = null
        private var regions: List<TextRegion> = emptyList()
            private var lux: Float = -1f
                private var mode = ProcessingMode.RAW
                private var param = 0f

                private var listener: OnModeChangeListener? = null

                    // Feedback visuel
                    private var feedbackText: String? = null
                        private var feedbackAlpha: Int = 0
                            private var feedbackHandler: android.os.Handler? = null

                                private val regionPaint = Paint().apply { color = Color.CYAN; style = Paint.Style.STROKE; strokeWidth = 4f }
                                private val bgPaint = Paint().apply { color = Color.argb(160, 0, 0, 0); style = Paint.Style.FILL }
                                private val labelPaint = Paint().apply { color = Color.CYAN; textSize = 32f; typeface = Typeface.DEFAULT_BOLD }
                                private val feedbackPaint = Paint().apply {
                                    color = Color.WHITE
                                    textSize = 48f
                                    typeface = Typeface.DEFAULT_BOLD
                                    isAntiAlias = true
                                    setShadowLayer(8f, 0f, 0f, Color.BLACK)
                                }
                                private val feedbackBgPaint = Paint().apply {
                                    color = Color.argb(200, 0, 0, 0)
                                    style = Paint.Style.FILL
                                }

                                private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                                    private val SWIPE_THRESHOLD = 60
                                    private val SWIPE_VELOCITY_THRESHOLD = 60

                                    // ✅ CORRECTION : paramètre non-nullable
                                    override fun onDown(e: MotionEvent): Boolean {
                                        return true
                                    }

                                    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                                        if (e1 == null) return false

                                            val diffX = e2.x - e1.x
                                            val diffY = e2.y - e1.y

                                            if (Math.abs(diffX) > Math.abs(diffY)) {
                                                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                                                    if (diffX > 0) {
                                                        adjustParam(10f)
                                                    } else {
                                                        adjustParam(-10f)
                                                    }
                                                    return true
                                                }
                                            } else {
                                                if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                                                    if (diffY > 0) {
                                                        changeMode(-1)
                                                    } else {
                                                        changeMode(1)
                                                    }
                                                    return true
                                                }
                                            }
                                            return false
                                    }
                                })

                                    fun setOnModeChangeListener(l: OnModeChangeListener?) {
                                        listener = l
                                    }

                                    fun setBitmap(b: Bitmap) { bitmap = b; invalidate() }
                                    fun setTextRegions(r: List<TextRegion>) { regions = r; invalidate() }
                                    fun setAmbientLight(l: Float) { lux = l }
                                    fun setMode(m: ProcessingMode, p: Float) { mode = m; param = p }

                                    fun showFeedback(text: String) {
                                        feedbackText = text
                                        feedbackAlpha = 255

                                        if (feedbackHandler == null) {
                                            feedbackHandler = android.os.Handler()
                                        }

                                        feedbackHandler?.removeCallbacksAndMessages(null)
                                        feedbackHandler?.postDelayed({
                                            feedbackAlpha = 0
                                            invalidate()
                                        }, 1500)

                                        invalidate()
                                    }

                                    private fun changeMode(direction: Int) {
                                        val modes = ProcessingMode.values()
                                        val currentIndex = modes.indexOf(mode)
                                        val newIndex = (currentIndex + direction + modes.size) % modes.size
                                        mode = modes[newIndex]
                                        param = mode.paramDefault
                                        listener?.onModeChanged(mode, param)
                                    }

                                    private fun adjustParam(delta: Float) {
                                        if (!mode.hasParam()) {
                                            showFeedback("${mode.displayName} - Pas de paramètre")
                                            return
                                        }

                                        param = (param + delta).coerceIn(mode.paramMin, mode.paramMax)
                                        listener?.onParamChanged(param)
                                    }

                                    override fun onDraw(canvas: Canvas) {
                                        super.onDraw(canvas)

                                        bitmap?.let { bmp ->
                                            val sx = width.toFloat() / bmp.width
                                            val sy = height.toFloat() / bmp.height
                                            val s = maxOf(sx, sy)
                                            val dw = bmp.width * s
                                            val dh = bmp.height * s
                                            val dl = (width - dw) / 2
                                            val dt = (height - dh) / 2
                                            canvas.drawBitmap(bmp, null, RectF(dl, dt, dl + dw, dt + dh), null)
                                        }

                                        if (mode == ProcessingMode.LECTURE) {
                                            for (r in regions.take(3)) {
                                                canvas.drawRect(r.box, regionPaint)
                                                val label = if (r.text.length > 20) r.text.take(20) + "..." else r.text
                                                val tw = labelPaint.measureText(label)
                                                canvas.drawRect(r.box.left.toFloat(), r.box.top - 40f, r.box.left + tw + 20, r.box.top.toFloat(), bgPaint)
                                                canvas.drawText(label, r.box.left + 10f, r.box.top - 10f, labelPaint)
                                            }
                                        }

                                        feedbackText?.let { text ->
                                            if (feedbackAlpha > 0) {
                                                feedbackPaint.alpha = feedbackAlpha
                                                feedbackBgPaint.alpha = feedbackAlpha

                                                val textWidth = feedbackPaint.measureText(text)
                                                val padding = 40f
                                                val boxWidth = textWidth + padding * 2
                                                val boxHeight = 120f
                                                val boxLeft = (width - boxWidth) / 2
                                                val boxTop = height / 3f

                                                canvas.drawRoundRect(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight, 20f, 20f, feedbackBgPaint)
                                                canvas.drawText(text, boxLeft + padding, boxTop + boxHeight / 2 + 15f, feedbackPaint)
                                            }
                                        }
                                    }

                                    override fun onTouchEvent(event: MotionEvent?): Boolean {
                                        val handled = event?.let { gestureDetector.onTouchEvent(it) } ?: false
                                        return handled || super.onTouchEvent(event)
                                    }
}
