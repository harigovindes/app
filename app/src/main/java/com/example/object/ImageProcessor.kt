package com.example.`object`

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import android.media.Image.Plane
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer

class ImageProcessor {
    companion object {
        fun createTensorImage(image: Image, rotation: Int): TensorImage {
            val bitmap = imageToRgbBitmap(image)
            val tensorImage = TensorImage.fromBitmap(bitmap)

            val imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR))
                .build()

            return imageProcessor.process(tensorImage)
        }

        private fun imageToRgbBitmap(image: Image): Bitmap {
            val width = image.width
            val height = image.height

            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            // U and V are swapped
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = android.graphics.YuvImage(
                nv21,
                ImageFormat.NV21,
                width,
                height,
                null
            )

            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
            val imageBytes = out.toByteArray()

            return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }
    }
}
