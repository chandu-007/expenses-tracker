@file:OptIn(ExperimentalForeignApi::class)

package app.expenses

import app.expenses.core.domain.OcrPiece
import app.expenses.core.domain.OcrRows
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import platform.CoreGraphics.CGImageRef
import platform.Foundation.NSError
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate

/**
 * Reads the text in a picture on the phone with Apple's Vision framework (nothing leaves the phone). Lines are put back into table rows by their height on
 * the page ([OcrRows]), as the Android reader does with ML Kit.
 */
internal object IosOcr {
    fun recognize(bytes: ByteArray): String {
        val handler = VNImageRequestHandler(data = bytes.toNSData(), options = emptyMap<Any?, Any?>())
        return run(handler)
    }

    fun recognize(image: CGImageRef): String = run(VNImageRequestHandler(cGImage = image, options = emptyMap<Any?, Any?>()))

    private fun run(handler: VNImageRequestHandler): String = memScoped {
        val request = VNRecognizeTextRequest()
        request.recognitionLevel = VNRequestTextRecognitionLevelAccurate
        request.usesLanguageCorrection = false // amounts and codes, not prose: a "correction" would change digits
        val error = alloc<ObjCObjectVar<NSError?>>()
        if (!handler.performRequests(listOf(request), error.ptr)) {
            throw IllegalArgumentException("The text in that picture could not be read on this phone (${error.value?.localizedDescription ?: "unknown error"}).")
        }
        val pieces = request.results.orEmpty().filterIsInstance<VNRecognizedTextObservation>().mapNotNull { o ->
            val text = (o.topCandidates(1u).firstOrNull() as? platform.Vision.VNRecognizedText)?.string ?: return@mapNotNull null
            // Vision's boxes are fractions of the picture with the origin at the bottom left; the rows are read top down
            o.boundingBox.useContents { OcrPiece(text, origin.x.toFloat(), (1.0 - (origin.y + size.height / 2)).toFloat(), size.height.toFloat()) }
        }
        OcrRows.group(pieces)
    }
}
