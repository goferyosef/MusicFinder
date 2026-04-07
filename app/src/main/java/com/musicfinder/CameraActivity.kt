package com.musicfinder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.musicfinder.databinding.ActivityCameraBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else showPermissionDeniedDialog()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermission.launch(Manifest.permission.CAMERA)
        }

        binding.buttonCapture.setOnClickListener { captureAndAnalyze() }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("CameraActivity", "Camera bind failed", e)
                Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureAndAnalyze() {
        val capture = imageCapture ?: return
        binding.buttonCapture.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        capture.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(proxy: ImageProxy) {
                val image = InputImage.fromMediaImage(proxy.image!!, proxy.imageInfo.rotationDegrees)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        proxy.close()
                        val recognizedText = visionText.text
                        binding.textOcrPreview.text = recognizedText.take(300).ifBlank { "(no text detected)" }
                        binding.textOcrPreview.visibility = View.VISIBLE

                        if (recognizedText.isBlank()) {
                            Toast.makeText(this@CameraActivity, "No text detected — try again with better lighting", Toast.LENGTH_LONG).show()
                            resetCapture()
                            return@addOnSuccessListener
                        }

                        val mentions = MusicDetector.detect(recognizedText)
                        autoPlayOrShowSheet(mentions, recognizedText)
                    }
                    .addOnFailureListener { e ->
                        proxy.close()
                        Log.e("CameraActivity", "OCR failed", e)
                        Toast.makeText(this@CameraActivity, "OCR failed — try again", Toast.LENGTH_SHORT).show()
                        resetCapture()
                    }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraActivity", "Capture error", exception)
                Toast.makeText(this@CameraActivity, "Capture failed — try again", Toast.LENGTH_SHORT).show()
                resetCapture()
            }
        })
    }

    private fun resetCapture() {
        binding.buttonCapture.isEnabled = true
        binding.progressBar.visibility = View.GONE
        binding.textOcrPreview.visibility = View.GONE
    }

    private fun autoPlayOrShowSheet(mentions: List<MusicMention>, rawText: String) {
        val top = mentions.firstOrNull()

        // Single HIGH-confidence match → launch instantly, reset camera after
        if (mentions.size == 1 && top?.confidence == Confidence.HIGH) {
            SearchLauncher.searchOnYouTube(this, top.searchQuery)
            resetCapture()
            return
        }

        // Multiple matches with a HIGH one on top → auto-launch best, show sheet for the rest
        if (top?.confidence == Confidence.HIGH) {
            SearchLauncher.searchOnYouTube(this, top.searchQuery)
        }

        ResultsBottomSheet.show(supportFragmentManager, mentions, rawText = rawText)
        supportFragmentManager.addFragmentOnAttachListener { _, fragment ->
            if (fragment is com.google.android.material.bottomsheet.BottomSheetDialogFragment) {
                fragment.dialog?.setOnDismissListener { resetCapture() }
            }
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Camera permission required")
            .setMessage("Camera access is needed to scan pages for music mentions.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                })
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        recognizer.close()
    }
}
