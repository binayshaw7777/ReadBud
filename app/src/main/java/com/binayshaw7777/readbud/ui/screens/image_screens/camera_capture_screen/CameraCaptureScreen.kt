package com.binayshaw7777.readbud.ui.screens.image_screens.camera_capture_screen

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import com.binayshaw7777.readbud.R
import com.binayshaw7777.readbud.model.RecognizedTextItem
import com.binayshaw7777.readbud.viewmodel.ImageSharedViewModel
import com.binayshaw7777.readbud.utils.rotateBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors


@Composable
fun CameraCaptureScreen(
    navController: NavController,
    imageSharedViewModel: ImageSharedViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val extractedText = remember { mutableStateOf("") }
    val recognizedTxtItems = remember { mutableStateOf(RecognizedTextItem()) }
    val isClicked = remember {
        mutableStateOf(false)
    }
    if (isClicked.value) {
        if (recognizedTxtItems.value.extractedText.isNullOrEmpty()) {
            Toast.makeText(context,
                stringResource(R.string.no_text_found_in_the_image), Toast.LENGTH_SHORT).show()
        } else {
            imageSharedViewModel.addRecognizedTextItems(recognizedTxtItems.value)
        }
        isClicked.value = false
        navController.popBackStack()
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TextRecognitionView(
            context = context,
            lifecycleOwner = lifecycleOwner,
            extractedText = extractedText,
            recognizedTextItem = recognizedTxtItems,
            isClicked = isClicked
        )
    }
}

@Composable
fun TextRecognitionView(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    extractedText: MutableState<String>,
    recognizedTextItem: MutableState<RecognizedTextItem>,
    isClicked: MutableState<Boolean>
) {
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var preview by remember { mutableStateOf<Preview?>(null) }
    val executor = ContextCompat.getMainExecutor(context)
    val cameraProvider = cameraProviderFuture.get()
    val textRecognizer =
        remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    Column(
        Modifier
            .background(Color.Black)
            .fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.7f),
            factory = { ctx ->
                val previewView = PreviewView(ctx)

                cameraProviderFuture.addListener({
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .apply {
                            setAnalyzer(
                                cameraExecutor,
                                ObjectDetectorImageAnalyzer(textRecognizer, extractedText, recognizedTextItem)
                            )
                        }
                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build()
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        imageAnalysis,
                        preview
                    )
                }, executor)

                preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)

                }
                previewView
            }
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(20.dp)
                .align(Alignment.CenterHorizontally)
                .background(Color.Transparent)
        ) {
            Button(
                modifier = Modifier
                    .size(65.dp)
                    .align(Alignment.CenterVertically),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                onClick = {
                    cameraProvider.unbindAll()
                    isClicked.value = true
                          },
                border = BorderStroke(4.dp, Color.Gray),
                shape = RoundedCornerShape(100)
            ) {

            }
        }
    }
}

class ObjectDetectorImageAnalyzer(
    private val textRecognizer: TextRecognizer,
    private val extractedText: MutableState<String>,
    private val recognizedTextItem: MutableState<RecognizedTextItem>
) : ImageAnalysis.Analyzer {
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            textRecognizer.process(image)
                .addOnCompleteListener {
                    if (it.isSuccessful) {
                        extractedText.value = it.result?.text ?: ""
                        recognizedTextItem.value = RecognizedTextItem(
                            extractedText = extractedText.value,
                            thumbnail = imageProxy.toBitmap().rotateBitmap(90)
                        )
                    }
                    imageProxy.close()
                }
        }
    }
}