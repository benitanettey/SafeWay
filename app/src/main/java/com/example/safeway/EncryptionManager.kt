package com.example.safeway

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object EncryptionManager {

    private const val KEY_ALIAS = "safeway_incident_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val GCM_TAG_LENGTH = 128 // bits
    private const val IV_LENGTH = 12 // bytes for GCM

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty() || plaintext.isBlank()) return plaintext
        return try {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            plaintext
        }
    }

    fun decrypt(ciphertext: String): String {
        if (ciphertext.isEmpty()) return ciphertext
        if (!isEncrypted(ciphertext)) return ciphertext
        return try {
            val secretKey = getOrCreateKey()
            val combined = Base64.decode(ciphertext, Base64.NO_WRAP)
            if (combined.size < IV_LENGTH + 1) return ciphertext
            val iv = combined.copyOfRange(0, IV_LENGTH)
            val encrypted = combined.copyOfRange(IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            ciphertext
        }
    }

    /**
     * Checks if a string looks like it might be encrypted (Base64 of IV+ciphertext).
     * Plaintext values like "High", "Physical", etc. will not match.
     */
    fun isEncrypted(value: String): Boolean {
        return value.length > IV_LENGTH * 2 && value.matches(Regex("^[A-Za-z0-9+/=]+$"))
    }
}
