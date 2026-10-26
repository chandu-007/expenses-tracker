package app.expenses.core.domain

/**
 * Whether [bytes] start like a picture the phones can read: JPEG, PNG, WebP, or HEIC/HEIF (what an iPhone and many Android phones save a photo as). Looks at the
 * first bytes only; the platform's decoder has the last word.
 */
object ImageSniff {
    private val HEIF_BRANDS = setOf("heic", "heix", "heim", "heis", "hevc", "hevx", "hevm", "hevs", "mif1", "msf1")

    fun looksLikeImage(bytes: ByteArray): Boolean {
        fun at(i: Int, vararg v: Int) = bytes.size >= i + v.size && v.indices.all { bytes[i + it] == v[it].toByte() }
        val jpeg = at(0, 0xFF, 0xD8, 0xFF)
        val png = at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val webp = at(0, 0x52, 0x49, 0x46, 0x46) && at(8, 0x57, 0x45, 0x42, 0x50)
        // an ISO media file: a box "ftyp" at byte 4, then the major brand
        val heif = at(4, 0x66, 0x74, 0x79, 0x70) && bytes.size >= 12 && HEIF_BRANDS.contains(bytes.decodeToString(8, 12))
        return jpeg || png || webp || heif
    }
}
