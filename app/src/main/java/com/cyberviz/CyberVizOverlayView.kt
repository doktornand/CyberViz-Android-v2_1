package com.cyberviz

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.cyberviz.TextAnalyzer.TextRegion // ← AJOUTÉ : Import de la classe imbriquée

class CyberVizOverlayView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var bitmap: Bitmap? = null
        private var regions: List<TextRegion> = emptyList()
            private var lux: Float = -1f
                private var mode = ProcessingMode.RAW
                private var param = 0f
                private val regionPaint = Paint().apply { color = Color.CYAN; style = Paint.Style.STROKE; strokeWidth = 4f }
                private val bgPaint = Paint().apply { color = Color.argb(160, 0, 0, 0); style = Paint.Style.FILL }
                private val labelPaint = Paint().apply { color = Color.CYAN; textSize = 32f; typeface = Typeface.DEFAULT_BOLD }
                private val hudPaint = Paint().apply { color = Color.WHITE; textSize = 36f; typeface = Typeface.MONOSPACE }
                private val hudBg = Paint().apply { color = Color.argb(180, 0, 0, 0); style = Paint.Style.FILL }

                fun setBitmap(b: Bitmap) { bitmap = b; invalidate() }
                fun setTextRegions(r: List<TextRegion>) { regions = r; invalidate() }
                fun setAmbientLight(l: Float) { lux = l }
                fun setMode(m: ProcessingMode, p: Float) { mode = m; param = p }

                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    bitmap?.let { bmp ->
                        val sx = width.toFloat() / bmp.width
                        val sy = height.toFloat() / bmp.height
                        val s = maxOf(sx, sy) // ← CHANGÉ: maxOf au lieu de minOf pour remplir l'écran
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
                    // drawHUD(canvas) ← COMMENTÉ: Supprime le HUD redondant qui chevauchait la RecyclerView
                }

                // AJOUTÉ: Permet aux événements tactiles de passer à la RecyclerView et à la SeekBar
                override fun onTouchEvent(event: android.view.MotionEvent?): Boolean {
                    return false
                }

                private fun drawHUD(c: Canvas) {
                    val hh = 80f
                    c.drawRect(0f, height - hh, width.toFloat(), height.toFloat(), hudBg)
                    val idx = ProcessingMode.values().indexOf(mode) + 1
                    val total = ProcessingMode.values().size
                    val ps = if (mode.hasParam()) "${param.toInt()} ${mode.paramLabel}" else ""
                    c.drawText(mode.displayName, 20f, height - 25f, hudPaint)
                    val cw = hudPaint.measureText("$idx/$total")
                    c.drawText("$idx/$total", (width - cw) / 2, height - 25f, hudPaint)
                    if (ps.isNotEmpty()) { val pw = hudPaint.measureText(ps); c.drawText(ps, width - pw - 20f, height - 25f, hudPaint) }
                }
}
