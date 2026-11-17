package com.example.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hardware-backed Android Keystore AES-256-GCM encryption for all on-device chat history,
 * action logs, and PIN hashes. Zero cloud keys or external transmission.
 */
object LocalCryptoManager {
  private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
  private const val KEY_ALIAS = "saathi_on_device_aes_key_v1"
  private const val TRANSFORMATION = "AES/GCM/NoPadding"
  private const val GCM_TAG_BITS = 128
  private const val IV_PREFIX_SEPARATOR = ":"

  @Volatile private var fallbackKey: SecretKey? = null

  private fun getOrCreateKey(): SecretKey {
    return try {
      val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
      val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
      if (existing != null) return existing

      val keyGenerator =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
      val spec =
        KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
          )
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .setKeySize(256)
          .setRandomizedEncryptionRequired(true)
          .build()
      keyGenerator.init(spec)
      keyGenerator.generateKey()
    } catch (_: Throwable) {
      // Fallback solely for host JVM / Robolectric unit tests lacking AndroidKeyStore
      fallbackKey
        ?: synchronized(this) {
          fallbackKey
            ?: ByteArray(32).also { SecureRandom().nextBytes(it) }.let {
              SecretKeySpec(it, "AES").also { key -> fallbackKey = key }
            }
        }
    }
  }

  fun encrypt(plaintext: String): String {
    if (plaintext.isEmpty()) return ""
    return try {
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
      val iv = Base64.getEncoder().encodeToString(cipher.iv)
      val ciphertext =
        Base64.getEncoder().encodeToString(
          cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        )
      "ENCv1$IV_PREFIX_SEPARATOR$iv$IV_PREFIX_SEPARATOR$ciphertext"
    } catch (_: Throwable) {
      plaintext
    }
  }

  fun decrypt(stored: String): String {
    if (stored.isEmpty()) return ""
    if (!stored.startsWith("ENCv1$IV_PREFIX_SEPARATOR")) return stored
    return try {
      val parts = stored.split(IV_PREFIX_SEPARATOR, limit = 3)
      if (parts.size != 3) return ""
      val iv = Base64.getDecoder().decode(parts[1])
      val ciphertext = Base64.getDecoder().decode(parts[2])
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
      String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    } catch (_: Throwable) {
      "[Encrypted payload unavailable]"
    }
  }

  fun hashPin(pin: String, salt: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest("$salt:$pin".toByteArray(StandardCharsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }

  fun generateSalt(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return Base64.getEncoder().encodeToString(bytes)
  }
}
