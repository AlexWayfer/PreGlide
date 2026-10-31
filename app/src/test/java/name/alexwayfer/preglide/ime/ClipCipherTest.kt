package name.alexwayfer.preglide.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class ClipCipherTest {
    private val key = newKey()
    private val cipher = ClipCipher { key }

    @Test
    fun whatIsEncryptedReadsBackAndDoesNotShowAsIs() {
        val text = "Konbini Cleanup: прокачка норм".toByteArray()

        val sealed = encrypted(cipher, text)

        assertNotEquals(String(text), String(sealed))
        assertEquals(String(text), String(cipher.decrypting(ByteArrayInputStream(sealed)).use { it.readBytes() }))
    }

    @Test
    fun eachFileHasItsOwnNonce() {
        val text = "раз".toByteArray()

        assertNotEquals(String(encrypted(cipher, text)), String(encrypted(cipher, text)))
    }

    @Test(expected = IOException::class)
    fun aChangedFileDoesNotRead() {
        val sealed = encrypted(cipher, "раз".toByteArray())
        sealed[sealed.size - 1] = (sealed.last() + 1).toByte()

        cipher.decrypting(ByteArrayInputStream(sealed)).use { it.readBytes() }
    }

    @Test(expected = IOException::class)
    fun anotherKeyDoesNotRead() {
        val sealed = encrypted(cipher, "раз".toByteArray())
        val other = newKey()

        ClipCipher { other }.decrypting(ByteArrayInputStream(sealed)).use { it.readBytes() }
    }

    @Test(expected = IOException::class)
    fun anEmptyFileDoesNotRead() {
        cipher.decrypting(ByteArrayInputStream(ByteArray(0)))
    }

    private fun encrypted(cipher: ClipCipher, bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> cipher.encrypting(out).use { it.write(bytes) } }.toByteArray()

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
}
