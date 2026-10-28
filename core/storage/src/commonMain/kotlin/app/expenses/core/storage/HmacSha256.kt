package app.expenses.core.storage

/** HMAC-SHA256 (RFC 2104) on [Sha256], so the key of an automatic copy is made the same way on every platform and needs nothing from the platforms. */
internal object HmacSha256 {
    private const val BLOCK = 64

    fun mac(key: ByteArray, message: ByteArray): ByteArray {
        val k = if (key.size > BLOCK) Sha256.hash(key) else key
        val inner = ByteArray(BLOCK) { ((if (it < k.size) k[it].toInt() else 0) xor 0x36).toByte() }
        val outer = ByteArray(BLOCK) { ((if (it < k.size) k[it].toInt() else 0) xor 0x5c).toByte() }
        val innerHash = Sha256.Digest().also { it.update(inner); it.update(message) }.finish()
        return Sha256.Digest().also { it.update(outer); it.update(innerHash) }.finish()
    }
}
