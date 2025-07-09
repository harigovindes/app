package com.example.`object`

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.RectF
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.`object`.ui.theme.ObjectTheme
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import android.os.Bundle
import org.tensorflow.lite.support.image.TensorImage
import com.example.`object`.ObjectDetectionHelper
import com.example.`object`.ImageProcessor
import com.example.`object`.ImageProcessor.Companion.createTensorImage
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var detectionHelper: ObjectDetectionHelper
    private val detectionBoxes = mutableStateListOf<RectF>()
    private val locationText = mutableStateOf("Waiting for location...")
    internal var previewView: PreviewView? = null

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val grantedCamera = permissions[Manifest.permission.CAMERA] == true
        val grantedLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (grantedCamera) startCamera()
        if (grantedLocation) startLocationUpdates()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            detectionHelper = ObjectDetectionHelper(this)
        } catch (e: Exception) {
            e.printStackTrace()
            // Continue without detection if model loading fails
        }
        
        setContent {
            ObjectTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CameraPreview(detectionBoxes)
                        LocationOverlay(
                            text = locationText.value,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
        
        requestPermissions()
    }

    private fun requestPermissions() {
        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    @SuppressLint("UnsafeOptInUsageError")
    internal fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                
                // Connect preview to PreviewView
                previewView?.let { preview.setSurfaceProvider(it.surfaceProvider) }
                
                val analyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImage(imageProxy)
                            imageProxy.close()
                        }
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analyzer
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, mainExecutor)
    }

    private fun processImage(imageProxy: ImageProxy) {
        if (!::detectionHelper.isInitialized) return
        
        imageProxy.image?.let { mediaImage ->
            try {
                val tensorImage = createTensorImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                val results = detectionHelper.detect(tensorImage)
                
                val newBoxes = results.mapNotNull { detection ->
                    detection.boundingBox?.let { box ->
                        RectF(box.left, box.top, box.right, box.bottom)
                    }
                }
                
                // Update UI on main thread
                runOnUiThread {
                    detectionBoxes.clear()
                    detectionBoxes.addAll(newBoxes)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(this)
            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                5000L // Reduced frequency to 5 seconds
            ).build()
            
            fusedClient.requestLocationUpdates(request, object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { loc ->
                        locationText.value = "Lat: ${String.format("%.6f", loc.latitude)}, Lon: ${String.format("%.6f", loc.longitude)}"
                    }
                }
            }, mainLooper)
        } catch (e: Exception) {
            e.printStackTrace()
            locationText.value = "Location unavailable"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

@Composable
fun CameraPreview(boxes: List<RectF>) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val activity = context as MainActivity
    
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }.also { preview ->
                    activity.previewView = preview
                    // Restart camera to connect preview
                    if (activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        activity.startCamera()
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Draw detection boxes
        Canvas(modifier = Modifier.fillMaxSize()) {
            boxes.forEach { box ->
                val left = box.left * size.width
                val top = box.top * size.height
                val width = (box.right - box.left) * size.width
                val height = (box.bottom - box.top) * size.height
                
                drawRect(
                    color = Color.Red.copy(alpha = 0.3f),
                    topLeft = Offset(left, top),
                    size = Size(width, height)
                )
            }
        }
    }
}

@Composable
fun LocationOverlay(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}