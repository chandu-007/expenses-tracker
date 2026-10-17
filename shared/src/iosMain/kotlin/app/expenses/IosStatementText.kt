package app.expenses

/** Statement bytes to text, as on Android: UTF-8 (with or without BOM), UTF-16 with BOM, else Windows-1252 (which accepts any byte). */
internal object IosStatementText {
    fun decode(bytes: ByteArray): String {
        fun utf16(from: Int, bigEndian: Boolean): String = buildString {
            var i = from
            while (i + 1 < bytes.size) {
                val a = bytes[i].toInt() and 0xFF; val b = bytes[i + 1].toInt() and 0xFF
                append((if (bigEndian) (a shl 8) or b else (b shl 8) or a).toChar())
                i += 2
            }
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return utf16(2, bigEndian = false)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return utf16(2, bigEndian = true)
        val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        return try {
            bytes.decodeToString(start, bytes.size, throwOnInvalidSequence = true)
        } catch (e: CharacterCodingException) {
            buildString { for (i in start until bytes.size) append(windows1252(bytes[i].toInt() and 0xFF)) }
        }
    }

    private fun windows1252(b: Int): Char = if (b in 0x80..0x9F) HIGH[b - 0x80] else b.toChar()

    /** Code points of bytes 0x80..0x9F in Windows-1252 (the five undefined bytes map to themselves, as the Windows decoder does). */
    private val HIGH = charArrayOf(
        '\u20AC', '\u0081', '\u201A', '\u0192', '\u201E', '\u2026', '\u2020', '\u2021', '\u02C6', '\u2030', '\u0160', '\u2039', '\u0152', '\u008D', '\u017D', '\u008F',
        '\u0090', '\u2018', '\u2019', '\u201C', '\u201D', '\u2022', '\u2013', '\u2014', '\u02DC', '\u2122', '\u0161', '\u203A', '\u0153', '\u009D', '\u017E', '\u0178',
    )
}
