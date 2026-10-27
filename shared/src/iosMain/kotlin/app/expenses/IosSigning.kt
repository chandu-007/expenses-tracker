package app.expenses

import app.expenses.core.domain.ProvisionProfile
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile

/** Reads when this install's signing runs out from the app bundle's `embedded.mobileprovision` (absent on the simulator and in App Store builds, so null there). */
@OptIn(ExperimentalForeignApi::class)
internal object IosSigning {
    fun expiresAtMillis(): Long? {
        val path = NSBundle.mainBundle.bundlePath + "/embedded.mobileprovision"
        val bytes = NSData.dataWithContentsOfFile(path)?.toByteArray() ?: return null
        // The file is a signed blob around an XML text; one byte per character keeps the text intact whatever the binary parts hold.
        return ProvisionProfile.expiresAtMillis(CharArray(bytes.size) { (bytes[it].toInt() and 0xFF).toChar() }.concatToString())
    }
}
