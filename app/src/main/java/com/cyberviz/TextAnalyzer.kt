package com.cyberviz

import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

class TextAnalyzer(
    private val onDetected: (List<TextRegion>) -> Unit,
        private val intervalMs: Long = 500L
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "TextAnalyzer"
        private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    data class TextRegion(
        val box: Rect,
        val text: String,
        val conf: Float,
        val isLine: Boolean = false
    )

    private val busy = AtomicBoolean(false)
    private var lastTime = 0L

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(proxy: ImageProxy) {
        val now = System.currentTimeMillis()

        if (busy.get() || (now - lastTime) < intervalMs) {
            proxy.close()
            return
        }

        val image = proxy.image
        if (image == null) {
            proxy.close()
            return
        }

        busy.set(true)
        lastTime = now

        val input = InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees)

        recognizer.process(input)
        .addOnSuccessListener { text ->
            val regions = mutableListOf<TextRegion>()
            for (block in text.textBlocks) {
                block.boundingBox?.let { box ->
                    if (box.width() > proxy.width / 20 && box.height() > proxy.height / 40) {
                        regions.add(
                            TextRegion(
                                box,
                                block.text,
                                block.lines.mapNotNull { it.confidence }.average().toFloat()
                            )
                        )
                    }
                }
                for (line in block.lines) {
                    line.boundingBox?.let { box ->
                        if ((line.confidence ?: 0f) > 0.7f) {
                            regions.add(TextRegion(box, line.text, line.confidence ?: 0f, true))
                        }
                    }
                }
            }
            onDetected(regions.sortedByDescending { it.conf })
            busy.set(false)
            proxy.close()
        }
        .addOnFailureListener { e ->
            Log.e(TAG, "OCR failed", e)
            busy.set(false)
            proxy.close()
        }
    }
}
