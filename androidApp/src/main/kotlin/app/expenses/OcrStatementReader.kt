package app.expenses

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

/**
 * Reads the text in a picture of a statement (a photo or a screenshot), on the phone. This uses ML Kit's BUNDLED Latin text recognizer: its model ships inside
 * the app, so nothing is downloaded and the app needs no network permission (see the note in AndroidManifest.xml). The blocking calls must not run on the main thread.
 *
 * A statement is a table, and the recognizer reports the cells as separate blocks, so the lines it finds are put back into rows by their height on the page:
 * lines whose centres are within half a line's height of each other are one row, left to right.
 */
class OcrStatementReader : AutoCloseable {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** The text of the picture in [bytes] (JPEG, PNG or WebP), a row per line. Throws [IllegalArgumentException] with a reason for a person. */
    fun recognize(bytes: ByteArray): String {
        val bitmap = decode(bytes) ?: throw IllegalArgumentException("That picture could not be opened.")
        try {
            return recognize(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    fun recognize(bitmap: Bitmap): String {
        val result = try {
            Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            throw IllegalArgumentException("The text in that picture could not be read on this phone (${e.cause?.message ?: e.message ?: e::class.simpleName}).")
        }
        return rows(result)
    }

    override fun close() {
        recognizer.close()
    }

    companion object {
        private const val TIMEOUT_SECONDS = 120L
        // Measured on the emulator (not a phone) with three 12-24 MP photos of real bills and an invented receipt at 4000x3000 and 1080x1600: this decode is about
        // 50-250 ms and ML Kit about 250-500 ms per picture; sides of 3000, 2400, 2000 and 1600 were no faster beyond the noise of the runs, and every one of them
        // changed the recognised text (4-13 numbers of a bill differed), so the larger size stays.
        private const val MAX_SIDE = 4096
        private const val MAX_PIXELS = 16_000_000L

        /** The recognised lines as rows of text in reading order. */
        internal fun rows(text: Text): String {
            class Piece(val text: String, val left: Int, val centre: Float, val height: Int)
            val pieces = text.textBlocks.flatMap { it.lines }.mapNotNull { l ->
                val box = l.boundingBox ?: return@mapNotNull null
                Piece(l.text, box.left, box.exactCenterY(), box.height().coerceAtLeast(1))
            }.sortedBy { it.centre }
            if (pieces.isEmpty()) return text.text
            val median = pieces.map { it.height }.sorted()[pieces.size / 2]
            val rows = ArrayList<MutableList<Piece>>()
            var centre = 0f
            for (p in pieces) {
                val row = rows.lastOrNull()
                if (row != null && kotlin.math.abs(p.centre - centre) <= median / 2f) {
                    row += p
                    centre = row.map { it.centre }.average().toFloat()
                } else {
                    rows += mutableListOf(p)
                    centre = p.centre
                }
            }
            return rows.joinToString("\n") { r -> r.sortedBy { it.left }.joinToString(" ") { it.text } }
        }

        /** The picture, turned upright when the file says it was taken sideways, and never larger than the recognizer needs. Null when it is not a picture. */
        internal fun decode(bytes: ByteArray): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE || bounds.outWidth.toLong() * bounds.outHeight / (sample.toLong() * sample) > MAX_PIXELS) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val degrees = try {
                when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (e: Exception) {
                0f // a picture with no readable tags (a PNG, a screenshot) is upright
            }
            if (degrees == 0f) return bitmap
            val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
            if (turned !== bitmap) bitmap.recycle()
            return turned
        }
    }
}
