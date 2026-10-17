package app.expenses

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val out = ByteArray(length.toInt())
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

@OptIn(ExperimentalForeignApi::class)
internal fun ByteArray.toNSData(): NSData = if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }

/** Bytes from the system's secure random source. */
@OptIn(ExperimentalForeignApi::class)
internal fun secureRandom(count: Int): ByteArray {
    val out = ByteArray(count)
    out.usePinned { check(SecRandomCopyBytes(kSecRandomDefault, count.convert(), it.addressOf(0)) == 0) { "No secure random bytes" } }
    return out
}
