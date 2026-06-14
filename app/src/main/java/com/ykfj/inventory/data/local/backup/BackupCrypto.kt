package com.ykfj.inventory.data.local.backup

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based AES-256-GCM encryption for backup archives.
 *
 * On-disk layout of an encrypted backup:
 * ```
 * [8 bytes  magic "YKFJBK01"]
 * [16 bytes PBKDF2 salt]
 * [12 bytes GCM IV/nonce]
 * [ciphertext + 16-byte GCM auth tag …]   ← the plaintext ZIP
 * ```
 * The header (magic, salt, IV) is written in the clear; it's safe to expose and
 * is needed to derive the key and start the cipher. The GCM auth tag means a
 * wrong password or any tampering is detected on the final read (decryption
 * throws rather than yielding garbage).
 *
 * Key derivation: PBKDF2WithHmacSHA256, [PBKDF2_ITERATIONS] iterations, 256-bit
 * key. Salt and IV are fresh per backup ([SecureRandom]).
 */
object BackupCrypto {

    private val MAGIC = byteArrayOf('Y'.code.toByte(), 'K'.code.toByte(), 'F'.code.toByte(), 'J'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte(), '0'.code.toByte(), '1'.code.toByte())
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** Length of the cleartext header so callers can size a peek buffer. */
    const val MAGIC_LEN = 8

    /** True if [header] (at least [MAGIC_LEN] bytes from the file start) is an encrypted backup. */
    fun hasMagic(header: ByteArray): Boolean =
        header.size >= MAGIC.size && MAGIC.indices.all { header[it] == MAGIC[it] }

    /**
     * Writes the cleartext header to [sink] and returns a [CipherOutputStream]
     * that encrypts everything subsequently written to it. The caller must
     * `close()` the returned stream to flush the final GCM block + auth tag.
     */
    fun encryptingStream(password: CharArray, sink: OutputStream): OutputStream {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        sink.write(MAGIC)
        sink.write(salt)
        sink.write(iv)
        sink.flush()
        return CipherOutputStream(sink, cipher)
    }

    /**
     * Reads + validates the cleartext header from [source], then returns a
     * [CipherInputStream] that decrypts the remaining bytes. A wrong password or
     * tampered file surfaces as an exception on the final read (GCM tag check).
     *
     * @throws IllegalArgumentException if [source] is not an encrypted backup.
     */
    fun decryptingStream(password: CharArray, source: InputStream): InputStream {
        val magic = source.readExactly(MAGIC.size)
        require(hasMagic(magic)) { "Not an encrypted YKFJ backup" }
        val salt = source.readExactly(SALT_LEN)
        val iv = source.readExactly(IV_LEN)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return CipherInputStream(source, cipher)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BITS)
        val keyBytes = try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return SecretKeySpec(keyBytes, "AES")
    }

    /** Reads exactly [n] bytes or throws — `InputStream.readNBytes` isn't available below API 33. */
    private fun InputStream.readExactly(n: Int): ByteArray {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val read = read(buf, off, n - off)
            if (read < 0) throw java.io.EOFException("Truncated backup header (wanted $n, got $off)")
            off += read
        }
        return buf
    }
}
