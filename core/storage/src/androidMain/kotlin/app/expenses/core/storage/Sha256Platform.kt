package app.expenses.core.storage

import java.security.MessageDigest

/** The JDK's SHA-256: on a phone it uses the processor's SHA instructions, about 50 times quicker than [PureSha256]. */
internal actual fun newSha256Engine(): Sha256Engine = object : Sha256Engine {
    private val md = MessageDigest.getInstance("SHA-256")
    override fun update(data: ByteArray, off: Int, len: Int) = md.update(data, off, len)
    override fun finish(): ByteArray = md.digest()
}
