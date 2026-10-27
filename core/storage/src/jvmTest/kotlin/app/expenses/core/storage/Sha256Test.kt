package app.expenses.core.storage

import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {
    @Test
    fun matchesTheStandardTestVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hex(ByteArray(0)))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hex("abc".encodeToByteArray()))
        assertEquals("248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1", Sha256.hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()))
        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", Sha256.hex(ByteArray(1_000_000) { 'a'.code.toByte() }))
    }

    @Test
    fun agreesWithTheJdkForEveryLengthAcrossTheBlockAndPaddingBoundaries() {
        val md = MessageDigest.getInstance("SHA-256")
        val rnd = Random(42)
        for (n in 0..200) {
            val data = rnd.nextBytes(n)
            assertEquals(md.digest(data).joinToString("") { "%02x".format(it) }, Sha256.hex(data), "length $n")
        }
        for (n in listOf(1_000, 4_095, 4_096, 65_537)) {
            val data = rnd.nextBytes(n)
            assertEquals(md.digest(data).joinToString("") { "%02x".format(it) }, Sha256.hex(data), "length $n")
        }
    }

    @Test
    fun thePortableImplementationAgreesWithTheJdkWhateverThePlatformUsesForTheRest() {
        val md = MessageDigest.getInstance("SHA-256")
        val rnd = Random(9)
        for (n in (0..130) + listOf(1_000, 4_096, 65_537, 300_000)) {
            val data = rnd.nextBytes(n)
            val pure = PureSha256()
            var i = 0
            while (i < n) { val k = minOf(rnd.nextInt(1, 100), n - i); pure.update(data, i, k); i += k }
            assertEquals(md.digest(data).joinToString("") { "%02x".format(it) }, pure.finish().joinToString("") { "%02x".format(it) }, "length $n")
        }
        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", PureSha256().also { it.update(ByteArray(1_000_000) { 'a'.code.toByte() }, 0, 1_000_000) }.finish().joinToString("") { "%02x".format(it) })
    }
}
