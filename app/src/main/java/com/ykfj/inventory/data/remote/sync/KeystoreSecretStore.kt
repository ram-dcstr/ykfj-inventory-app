package com.ykfj.inventory.data.remote.sync

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps secrets (currently the JWT signing key) with an AES-256-GCM key that
 * lives in the Android Keystore. The wrapping key is non-exportable — it stays
 * inside the device's hardware-backed keystore where available, so even a
 * stolen backup file is useless without the device.
 *
 * Storage format for an encrypted value: `base64(iv) + ":" + base64(ciphertext)`
 * — both parts URL-safe-encoded with no padding so they round-trip cleanly
 * through SQLite TEXT columns.
 *
 * Without this layer, the JWT secret sat in `app_settings` plaintext and
 * anyone with the device's data folder (root, ADB backup, accidental copy)
 * could forge tokens for any user.
 */
@Singleton
class KeystoreSecretStore @Inject constructor() {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    /** Encrypt [plaintext] under the app's wrap key. */
    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrapKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, BASE64_FLAGS) +
            DELIMITER +
            Base64.encodeToString(ciphertext, BASE64_FLAGS)
    }

    /**
     * Decrypt a value previously produced by [encrypt]. Throws if the input is
     * malformed or the wrap key was rotated (e.g. user wiped app data) — callers
     * should handle the exception by treating the secret as lost.
     */
    fun decrypt(encoded: String): String {
        val parts = encoded.split(DELIMITER)
        require(parts.size == 2) { "Malformed encrypted secret" }
        val iv = Base64.decode(parts[0], BASE64_FLAGS)
        val ciphertext = Base64.decode(parts[1], BASE64_FLAGS)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateWrapKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun getOrCreateWrapKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ykfj_secret_wrap_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_BITS = 128
        const val BASE64_FLAGS = Base64.NO_WRAP or Base64.URL_SAFE
        const val DELIMITER = ":"
    }
}
