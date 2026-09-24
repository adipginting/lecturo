package com.adipginting.lecturo.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES/GCM encryption backed by an AndroidKeyStore key; the key never leaves
 * the TEE/StrongBox. Stored values are Base64(iv + ciphertext). Legacy
 * plaintext values pass through [decrypt] unchanged so existing installs
 * migrate on next save.
 */
object KeyStoreCrypto {

    private const val KEY_ALIAS = "lecturo_api_keys"
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        if (encoded.isEmpty()) return ""
        return decryptOrNull(encoded) ?: encoded
    }

    /** Null when [encoded] is not a value produced by [encrypt] (e.g. legacy plaintext). */
    fun decryptOrNull(encoded: String): String? {
        if (encoded.isEmpty()) return ""
        return runCatching {
            val blob = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(GCM_TAG_BITS, blob.copyOfRange(0, GCM_IV_BYTES)),
            )
            String(cipher.doFinal(blob.copyOfRange(GCM_IV_BYTES, blob.size)))
        }.getOrNull()
    }
}
