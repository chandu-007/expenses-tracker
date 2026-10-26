package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageSniffTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun heif(brand: String) = bytes(0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70) + brand.encodeToByteArray() + ByteArray(8)

    @Test fun commonPicturesAreRecognised() {
        assertTrue(ImageSniff.looksLikeImage(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0)))
        assertTrue(ImageSniff.looksLikeImage(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0)))
        assertTrue(ImageSniff.looksLikeImage("RIFF....WEBPVP8 ".encodeToByteArray()))
    }

    @Test fun anIPhonePhotoSavedAsHeicIsRecognised() {
        listOf("heic", "heix", "mif1", "hevc").forEach { assertTrue(ImageSniff.looksLikeImage(heif(it)), it) }
    }

    @Test fun otherFilesAreNot() {
        assertFalse(ImageSniff.looksLikeImage("%PDF-1.7".encodeToByteArray()))
        assertFalse(ImageSniff.looksLikeImage(heif("mp42")), "an MP4 video is an ISO media file too")
        assertFalse(ImageSniff.looksLikeImage(ByteArray(0)))
        assertFalse(ImageSniff.looksLikeImage(bytes(0, 0, 0, 0x18, 0x66, 0x74)))
    }
}
