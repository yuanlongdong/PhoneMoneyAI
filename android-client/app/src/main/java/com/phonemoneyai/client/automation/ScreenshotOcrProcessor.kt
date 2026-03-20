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
import kotlin.coroutines.resume

class ScreenshotOcrProcessor(private val service: AccessibilityService) {
    suspend fun captureOcr(): List<OcrNode> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val bitmap = captureScreenshot() ?: return emptyList()
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
                    continuation.resume(nodes)
                }
                .addOnFailureListener {
                    continuation.resume(emptyList())
                }
        }
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
