package com.cyberviz

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyberviz.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "CyberViz"
        private const val REQUEST_CODE = 10
        private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    private lateinit var binding: ActivityMainBinding
        private lateinit var executor: ExecutorService
            private var imageAnalysis: ImageAnalysis? = null
                private var textAnalysis: ImageAnalysis? = null
                    private var textAnalyzer: TextAnalyzer? = null
                        private var frameAnalyzer: FrameAnalyzer? = null
                            private var sensorManager: SensorManager? = null
                                private var lightSensor: Sensor? = null

                                    private var currentMode = ProcessingMode.RAW
                                    private var currentParam = 0f

                                    private val lightListener = object : SensorEventListener {
                                        override fun onSensorChanged(e: SensorEvent) {
                                            binding.overlayView.setAmbientLight(e.values[0])
                                        }
                                        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
                                    }

                                    override fun onCreate(savedInstanceState: Bundle?) {
                                        super.onCreate(savedInstanceState)
                                        binding = ActivityMainBinding.inflate(layoutInflater)
                                        setContentView(binding.root)

                                        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager?
                                        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
                                        executor = Executors.newSingleThreadExecutor()

                                        setupModeSelector()
                                        setupParamSeekBar()

                                        if (allGranted()) startCamera()
                                            else ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_CODE)
                                    }

                                    private fun setupModeSelector() {
                                        val modes = ProcessingMode.values().toList()
                                        val adapter = ModeAdapter(modes, currentMode) { selectedMode ->
                                            currentMode = selectedMode
                                            currentParam = selectedMode.paramDefault
                                            frameAnalyzer?.setMode(currentMode, currentParam)
                                            binding.overlayView.setMode(currentMode, currentParam)
                                            updateParamSeekBar(selectedMode)
                                        }
                                        binding.modeRecycler.layoutManager =
                                        LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                                        binding.modeRecycler.adapter = adapter
                                    }

                                    private fun updateParamSeekBar(mode: ProcessingMode) {
                                        if (mode.hasParam()) {
                                            binding.paramSeekBar.visibility = View.VISIBLE
                                            val range = mode.paramMax - mode.paramMin
                                            val progress = ((currentParam - mode.paramMin) / range * 100).toInt()
                                            binding.paramSeekBar.progress = progress.coerceIn(0, 100)
                                        } else {
                                            binding.paramSeekBar.visibility = View.GONE
                                        }
                                    }

                                    private fun setupParamSeekBar() {
                                        binding.paramSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                                            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                                                val mode = currentMode
                                                if (mode.hasParam()) {
                                                    val range = mode.paramMax - mode.paramMin
                                                    currentParam = mode.paramMin + (progress / 100f) * range
                                                    frameAnalyzer?.setMode(mode, currentParam)
                                                    binding.overlayView.setMode(mode, currentParam)
                                                }
                                            }
                                            override fun onStartTrackingTouch(sb: SeekBar) {}
                                            override fun onStopTrackingTouch(sb: SeekBar) {}
                                        })
                                    }

                                    private fun startCamera() {
                                        val future = ProcessCameraProvider.getInstance(this)
                                        future.addListener({
                                            try {
                                                val provider = future.get()

                                                // Rotation de l'écran pour corriger l'orientation des frames
                                                val rotation = windowManager.defaultDisplay.rotation

                                                // PreviewView SUPPRIMÉ - Plus besoin de créer le Preview use case

                                                frameAnalyzer = FrameAnalyzer { bitmap ->
                                                    runOnUiThread { binding.overlayView.setBitmap(bitmap) }
                                                }

                                                imageAnalysis = ImageAnalysis.Builder()
                                                .setTargetRotation(rotation) // ← clé pour la rotation
                                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                                                .build()
                                                .also { it.setAnalyzer(executor, frameAnalyzer!!) }

                                                textAnalyzer = TextAnalyzer(
                                                    onDetected = { regions ->
                                                        runOnUiThread { binding.overlayView.setTextRegions(regions) }
                                                    },
                                                    intervalMs = 500L
                                                )

                                                textAnalysis = ImageAnalysis.Builder()
                                                .setTargetRotation(rotation)
                                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                                                .build()
                                                .also { textAnalyzer?.let { a -> it.setAnalyzer(executor, a as ImageAnalysis.Analyzer) } }

                                                provider.unbindAll()
                                                provider.bindToLifecycle(
                                                    this,
                                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                                    // preview SUPPRIMÉ - Seulement les ImageAnalysis
                                                    imageAnalysis,
                                                    textAnalysis
                                                )

                                                Log.d(TAG, "Camera liée avec rotation=$rotation")

                                            } catch (e: Exception) {
                                                Log.e(TAG, "Camera binding failed", e)
                                                Toast.makeText(this, "Erreur caméra: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }, ContextCompat.getMainExecutor(this))
                                    }

                                    private fun allGranted() = PERMISSIONS.all {
                                        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
                                    }

                                    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
                                        super.onRequestPermissionsResult(requestCode, permissions, results)
                                        if (requestCode == REQUEST_CODE) {
                                            if (allGranted()) startCamera()
                                                else { Toast.makeText(this, "Permissions requises", Toast.LENGTH_SHORT).show(); finish() }
                                        }
                                    }

                                    override fun onResume() {
                                        super.onResume()
                                        lightSensor?.let { sensorManager?.registerListener(lightListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
                                    }

                                    override fun onPause() {
                                        super.onPause()
                                        sensorManager?.unregisterListener(lightListener)
                                    }

                                    override fun onDestroy() {
                                        super.onDestroy()
                                        executor.shutdown()
                                    }
}
