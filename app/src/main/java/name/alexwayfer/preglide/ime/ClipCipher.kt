package name.alexwayfer.preglide.ime

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val TAG_BITS = 128
private const val KEY_BITS = 256
private const val KEYSTORE = "AndroidKeyStore"

/** The name of the key the clipboard's history is encrypted with, in Android's keystore. */
private const val HISTORY_KEY = "clipboard-history"

/**
 * Encrypts the clipboard's history with AES-GCM under [key]: each file starts with its own nonce, and a file
 * changed or read with another key fails to read rather than read wrong.
 */
class ClipCipher(private val key: () -> SecretKey) {
    /** [output], which encrypts what is written to it; closing it closes [output]. */
    fun encrypting(output: OutputStream): OutputStream {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val nonce = cipher.iv
        output.write(nonce.size)
        output.write(nonce)
        return CipherOutputStream(output, cipher)
    }

    /** [input], read decrypted; closing it closes [input]. */
    fun decrypting(input: InputStream): InputStream {
        val size = input.read()
        if (size <= 0) throw IOException("No nonce")
        // readNBytes came only in Android 13.
        val nonce = ByteArray(size).also { DataInputStream(input).readFully(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, nonce))
        return CipherInputStream(input, cipher)
    }
}

/**
 * The key of the clipboard's history in Android's keystore, made on first use. It never leaves the keystore, so a
 * copy of the files alone, as from a rooted phone, cannot be read.
 */
fun historyKey(): SecretKey {
    val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    (store.getKey(HISTORY_KEY, null) as? SecretKey)?.let { return it }
    val spec = KeyGenParameterSpec.Builder(HISTORY_KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(KEY_BITS)
        .build()
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
}
