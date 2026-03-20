package com.phonemoneyai.client.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import com.phonemoneyai.client.model.OcrNode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

class ScreenshotOcrProcessor(private val service: AccessibilityService) {
    suspend fun capture(): ScreenshotCaptureResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ScreenshotCaptureResult()
        val bitmap = captureScreenshot() ?: return ScreenshotCaptureResult()
        val screenshotPath = persistBitmap(bitmap)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val nodes = visionText.textBlocks.flatMap { block ->
                        block.lines.map { line ->
                            val rect = line.boundingBox
                            OcrNode(
                                text = line.text,
                                x = rect?.centerX() ?: 0,
                                y = rect?.centerY() ?: 0,
                                confidence = 0.9,
                            )
                        }
                    }
                    val summary = nodes.groupingBy { it.text }.eachCount().entries.joinToString { "${it.key}:${it.value}" }
                    continuation.resume(ScreenshotCaptureResult(screenshotPath = screenshotPath, ocrNodes = nodes, ocrSummary = summary))
                }
                .addOnFailureListener {
                    continuation.resume(ScreenshotCaptureResult(screenshotPath = screenshotPath))
                }
        }
    }

    private fun persistBitmap(bitmap: Bitmap): String {
        val output = File(service.cacheDir, "pmai-${System.currentTimeMillis()}.png")
        FileOutputStream(output).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        return output.absolutePath
    }

    private suspend fun captureScreenshot(): Bitmap? = suspendCancellableCoroutine { continuation ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                    continuation.resume(Bitmap.wrapHardwareBuffer(screenshotResult.hardwareBuffer, screenshotResult.colorSpace))
                }

                override fun onFailure(errorCode: Int) {
                    continuation.resume(null)
                }
            }
        )
    }
}

data class ScreenshotCaptureResult(
    val screenshotPath: String? = null,
    val ocrNodes: List<OcrNode> = emptyList(),
    val ocrSummary: String = "",
)
