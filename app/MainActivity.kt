package com.example.photogrammetryapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.photogrammetryapp.data.UploadRepository
import com.example.photogrammetryapp.ml.ShapeAggregator
import com.example.photogrammetryapp.ml.ShapeClassifier
import com.example.photogrammetryapp.ml.ShapePrediction
import com.example.photogrammetryapp.utils.UriFileMapper
import com.example.photogrammetryapp.utils.ZipUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val SERVER_URL = "http://192.168.0.25:5000/upload"

    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var webView: WebView
    private lateinit var btnCloseViewer: Button
    private lateinit var controlsLayout: View

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(900, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    private val repository by lazy {
        UploadRepository(client, SERVER_URL)
    }

    private val shapeClassifier by lazy {
        ShapeClassifier(this)
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)
        webView = findViewById(R.id.webView)
        btnCloseViewer = findViewById(R.id.btnCloseViewer)
        controlsLayout = findViewById(R.id.controlsLayout)

        setupWebView()
        setupImagePicker()
        setupCloseButton()
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            setRenderPriority(WebSettings.RenderPriority.HIGH)
        }
        webView.webViewClient = WebViewClient()
    }

    private fun setupImagePicker() {
        val pickLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uris = mutableListOf<Uri>()
                val clip = result.data?.clipData

                if (clip != null) {
                    for (i in 0 until clip.itemCount) {
                        uris.add(clip.getItemAt(i).uri)
                    }
                } else {
                    result.data?.data?.let { uris.add(it) }
                }

                if (uris.isNotEmpty()) {
                    processImages(uris)
                }
            }
        }

        findViewById<Button>(R.id.btnSelectImages).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            pickLauncher.launch(intent)
        }
    }

    private fun setupCloseButton() {
        btnCloseViewer.setOnClickListener {
            webView.visibility = View.GONE
            btnCloseViewer.visibility = View.GONE
            controlsLayout.visibility = View.VISIBLE
            webView.loadUrl("about:blank")
        }
    }

    private fun processImages(uris: List<Uri>) {
        scope.launch {
            try {
                setStatus("Analyzing shapes on selected photos...", true)

                val perPhotoPredictions = withContext(Dispatchers.IO) {
                    uris.map { uri -> shapeClassifier.classifyUri(this@MainActivity, uri) }
                }
                val finalPrediction = ShapeAggregator.aggregate(perPhotoPredictions)

                val objectStatus = buildPredictionMessage(perPhotoPredictions, finalPrediction)
                setStatus("$objectStatus\nPreparing files...", true)

                val files = withContext(Dispatchers.IO) {
                    uris.map { uri ->
                        UriFileMapper.uriToFile(this@MainActivity, uri)
                    }
                }

                setStatus("$objectStatus\nCompressing images...", true)

                val zipFile = withContext(Dispatchers.IO) {
                    ZipUtil.createZip(files)
                }

                setStatus("$objectStatus\nProcessing on server (Wait 5-10m)...", true)

                val result = withContext(Dispatchers.IO) {
                    repository.upload(zipFile)
                }

                if (result.isSuccess) {
                    val bytes = result.getOrNull()
                    if (bytes != null) {
                        val modelFile = saveModel(bytes)
                        setStatus("$objectStatus\nDone!", false)
                        loadModelInWebView(modelFile)
                    }
                } else {
                    setStatus("$objectStatus\nError: ${result.exceptionOrNull()?.message}", false)
                }

            } catch (e: Exception) {
                setStatus("Error: ${e.message}", false)
            }
        }
    }

    private fun buildPredictionMessage(
        perPhotoPredictions: List<ShapePrediction>,
        finalPrediction: ShapePrediction?
    ): String {
        if (perPhotoPredictions.isEmpty() || finalPrediction == null) {
            return "Object recognition: no prediction"
        }

        val votes = perPhotoPredictions.count { it.label == finalPrediction.label }
        val total = perPhotoPredictions.size
        val confidence = String.format(Locale.US, "%.2f", finalPrediction.confidence)

        return "Object recognition: ${finalPrediction.label} ($votes/$total, conf=$confidence)"
    }

    private fun saveModel(bytes: ByteArray): File {
        val modelFile = File(filesDir, "model.glb")
        FileOutputStream(modelFile).use { it.write(bytes) }
        return modelFile
    }

    private fun loadModelInWebView(file: File) {
        val fileBytes = file.readBytes()
        val base64Model = Base64.encodeToString(fileBytes, Base64.NO_WRAP)
        val dataUri = "data:model/gltf-binary;base64,$base64Model"

        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <script type="module" src="https://ajax.googleapis.com/ajax/libs/model-viewer/3.3.0/model-viewer.min.js"></script>
                <style>
                    body { margin: 0; background-color: #f5f5f5; height: 100vh; }
                    model-viewer { width: 100%; height: 100%; }
                </style>
            </head>
            <body>
                <model-viewer
                    src="$dataUri"
                    auto-rotate
                    camera-controls>
                </model-viewer>
            </body>
            </html>
        """.trimIndent()

        controlsLayout.visibility = View.GONE
        webView.visibility = View.VISIBLE
        btnCloseViewer.visibility = View.VISIBLE

        webView.loadDataWithBaseURL(
            "https://modelviewer.dev/",
            htmlContent,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun setStatus(text: String, loading: Boolean) {
        tvStatus.text = text
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        shapeClassifier.close()
        super.onDestroy()
        scope.cancel()
    }
}
