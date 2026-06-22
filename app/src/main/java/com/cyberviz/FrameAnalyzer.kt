package com.cyberviz

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class FrameAnalyzer(private val onFrame: (Bitmap) -> Unit) : ImageAnalysis.Analyzer {
    private var src: IntArray? = null
        private var dst: IntArray? = null
            private var bitmap: Bitmap? = null
                @Volatile private var mode = ProcessingMode.RAW
                @Volatile private var param = 0f

                @androidx.camera.core.ExperimentalGetImage
                override fun analyze(proxy: ImageProxy) {
                    val image = proxy.image ?: run { proxy.close(); return }
                    val w = proxy.width
                    val h = proxy.height

                    if (bitmap == null || bitmap!!.width != w || bitmap!!.height != h) {
                        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        src = IntArray(w * h)
                        dst = IntArray(w * h)
                    }

                    val yPlane = image.planes[0]
                    val uPlane = image.planes[1]
                    val vPlane = image.planes[2]

                    val yBuffer = yPlane.buffer
                    val uBuffer = uPlane.buffer
                    val vBuffer = vPlane.buffer

                    val yRowStride = yPlane.rowStride
                    val uvRowStride = uPlane.rowStride
                    val uvPixelStride = uPlane.pixelStride

                    var pos = 0
                    for (y in 0 until h) {
                        for (x in 0 until w) {
                            val yIdx = y * yRowStride + x
                            val uvIdx = (y / 2) * uvRowStride + (x / 2) * uvPixelStride

                            val yVal = (yBuffer.get(yIdx).toInt() and 0xFF)
                            val uVal = (uBuffer.get(uvIdx).toInt() and 0xFF) - 128
                            val vVal = (vBuffer.get(uvIdx).toInt() and 0xFF) - 128

                            val r = clamp((yVal + 1.370705f * vVal).toInt())
                            val g = clamp((yVal - 0.698001f * vVal - 0.337633f * uVal).toInt())
                            val b = clamp((yVal + 1.732446f * uVal).toInt())

                            src!![pos++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        }
                    }

                    ImageProcessor.process(src!!, dst!!, w, h, mode, param)
                    bitmap!!.setPixels(dst!!, 0, w, 0, 0, w, h)
                    onFrame(bitmap!!)
                    proxy.close()
                }

                fun setMode(m: ProcessingMode, p: Float) {
                    mode = m
                    param = p
                }

                private fun clamp(v: Int) = if (v < 0) 0 else if (v > 255) 255 else v
}
