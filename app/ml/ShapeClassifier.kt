package com.example.photogrammetryapp.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.NormalizeOp
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.Closeable
import kotlin.math.min

data class ShapePrediction(
    val label: String,
    val confidence: Float
)

class ShapeClassifier(context: Context) : Closeable {

    companion object {
        private const val MODEL_FILE = "shape_classifier.tflite"
        private const val LABELS_FILE = "shape_labels.txt"
        private const val INPUT_SIZE = 224
    }

    private val interpreter: Interpreter by lazy {
        val modelBuffer = FileUtil.loadMappedFile(context, MODEL_FILE)
        Interpreter(modelBuffer)
    }

    private val labels: List<String> = FileUtil.loadLabels(context, LABELS_FILE)

    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(0f, 255f))
        .build()

    fun classifyUri(context: Context, uri: Uri): ShapePrediction {
        val bitmap = loadBitmap(context, uri)
        return classifyBitmap(bitmap)
    }

    fun classifyBitmap(bitmap: Bitmap): ShapePrediction {
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        val processedImage = imageProcessor.process(tensorImage)

        val output = Array(1) { FloatArray(labels.size) }
        interpreter.run(processedImage.buffer, output)

        val probs = output[0]
        var bestIndex = 0
        var bestProb = probs[0]
        for (i in 1 until probs.size) {
            if (probs[i] > bestProb) {
                bestProb = probs[i]
                bestIndex = i
            }
        }

        val safeIndex = min(bestIndex, labels.lastIndex)
        return ShapePrediction(
            label = labels[safeIndex],
            confidence = bestProb
        )
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }

    override fun close() {
        interpreter.close()
    }
}
