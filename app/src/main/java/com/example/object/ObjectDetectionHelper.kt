package com.example.`object`

import android.content.Context
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.task.vision.detector.Detection

/**
 * Helper class to load TensorFlow Lite model and run object detection.
 */
class ObjectDetectionHelper(context: Context) {
    private var objectDetector: ObjectDetector? = null

    init {
        try {
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setMaxResults(5)
                .setScoreThreshold(0.3f)
                .build()

            objectDetector = ObjectDetector.createFromFileAndOptions(
                context,
                "detect.tflite", // Make sure this file exists in assets folder
                options
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // Continue without detection
        }
    }

    /**
     * Runs detection on the input image.
     */
    fun detect(image: TensorImage): List<Detection> {
        return try {
            objectDetector?.detect(image) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
