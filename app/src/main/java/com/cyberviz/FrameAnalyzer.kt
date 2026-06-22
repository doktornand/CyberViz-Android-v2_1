package com.cyberviz

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class FrameAnalyzer(private val onFrame: (Bitmap) -> Unit) : ImageAnalysis.Analyzer {

    // Buffers de travail réutilisables (évite les allocations répétées)
    private var src: IntArray? = null
        private var dst: IntArray? = null
            private var rawBitmap: Bitmap? = null

                // Bitmap roté (résultat final envoyé à l'overlay)
                private var rotatedBitmap: Bitmap? = null

                    // Mode de traitement (accès concurrent analyze/UI thread)
                    @Volatile private var mode = ProcessingMode.RAW
                    @Volatile private var param = 0f

                    // Matrice de rotation réutilisable
                    private val rotationMatrix = Matrix()
                    private var lastRotation = -1

                    fun setMode(m: ProcessingMode, p: Float) {
                        mode = m
                        param = p
                    }

                    @androidx.camera.core.ExperimentalGetImage
                    override fun analyze(proxy: ImageProxy) {
                        val image = proxy.image ?: run { proxy.close(); return }

                        val w = proxy.width
                        val h = proxy.height
                        val rotationDegrees = proxy.imageInfo.rotationDegrees

                        // Réallocation des buffers uniquement si la résolution change
                        if (rawBitmap == null || rawBitmap!!.width != w || rawBitmap!!.height != h) {
                            rawBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            src = IntArray(w * h)
                            dst = IntArray(w * h)
                        }

                        // ── Conversion YUV_420_888 → ARGB ──────────────────────────────────
                        val yPlane = image.planes[0]
                        val uPlane = image.planes[1]
                        val vPlane = image.planes[2]

                        val yBuffer = yPlane.buffer
                        val uBuffer = uPlane.buffer
                        val vBuffer = vPlane.buffer

                        val yRowStride   = yPlane.rowStride
                        val uvRowStride  = uPlane.rowStride
                        val uvPixelStride = uPlane.pixelStride

                        val srcArr = src!!
                        var pos = 0
                        for (y in 0 until h) {
                            for (x in 0 until w) {
                                val yIdx  = y * yRowStride + x
                                val uvIdx = (y / 2) * uvRowStride + (x / 2) * uvPixelStride

                                val yVal = yBuffer.get(yIdx).toInt() and 0xFF
                                val uVal = (uBuffer.get(uvIdx).toInt() and 0xFF) - 128
                                val vVal = (vBuffer.get(uvIdx).toInt() and 0xFF) - 128

                                val r = clamp((yVal + 1.370705f * vVal).toInt())
                                val g = clamp((yVal - 0.698001f * vVal - 0.337633f * uVal).toInt())
                                val b = clamp((yVal + 1.732446f * uVal).toInt())

                                srcArr[pos++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                            }
                        }

                        // ── Traitement du mode actif ────────────────────────────────────────
                        val curMode  = mode
                        val curParam = param
                        val dstArr = dst!!

                        ImageProcessor.process(srcArr, dstArr, w, h, curMode, curParam)
                        rawBitmap!!.setPixels(dstArr, 0, w, 0, 0, w, h)

                        // ── Correction de la rotation ───────────────────────────────────────
                        val finalBitmap: Bitmap = if (rotationDegrees != 0) {
                            // Reconstruire la matrice uniquement si la rotation a changé
                            if (rotationDegrees != lastRotation) {
                                rotationMatrix.reset()
                                rotationMatrix.postRotate(rotationDegrees.toFloat())
                                lastRotation = rotationDegrees
                            }
                            // Créer (ou recycler) le bitmap roté aux bonnes dimensions
                            val rw = if (rotationDegrees == 90 || rotationDegrees == 270) h else w
                            val rh = if (rotationDegrees == 90 || rotationDegrees == 270) w else h
                            if (rotatedBitmap == null
                                || rotatedBitmap!!.width  != rw
                                || rotatedBitmap!!.height != rh) {
                                rotatedBitmap?.recycle()
                                rotatedBitmap = Bitmap.createBitmap(rawBitmap!!, 0, 0, w, h, rotationMatrix, false)
                                } else {
                                    // Réutiliser le bitmap existant via createBitmap (alloue quand même,
                                    // mais évite le recycle intempestif entre frames)
                                    rotatedBitmap = Bitmap.createBitmap(rawBitmap!!, 0, 0, w, h, rotationMatrix, false)
                                }
                                rotatedBitmap!!
                        } else {
                            rawBitmap!!
                        }

                        onFrame(finalBitmap)
                        proxy.close()
                    }

                    private fun clamp(v: Int) = when {
                        v < 0   -> 0
                        v > 255 -> 255
                        else    -> v
                    }
}
