package `fun`.lzy.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ApiKeyManager(context: Context) {
    private val prefs = context.getSharedPreferences("deepseek_secure_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DEEPSEEK_API_KEY = "encrypted_api_key"
        private const val KEY_LEGACY_DEEPSEEK_API_KEY = "obscured_api_key"
        private const val KEY_CHECK_INTERVAL_SEC = "check_interval_sec"
        private const val KEYSTORE_ALIAS = "deepseek_monitor_api_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private val LEGACY_OBFUSCATION_KEY = byteArrayOf(57, 118, 92, 19, 73, 112, 12, 34)
    }

    fun getApiKey(): String {
        prefs.getString(KEY_DEEPSEEK_API_KEY, null)?.let { encryptedBase64 ->
            return decryptWithKeystore(encryptedBase64)
        }

        return migrateLegacyApiKey()
    }

    fun saveApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        val editor = prefs.edit().remove(KEY_LEGACY_DEEPSEEK_API_KEY)
        if (trimmed.isEmpty()) {
            editor.remove(KEY_DEEPSEEK_API_KEY).apply()
            return
        }

        encryptWithKeystore(trimmed)?.let { encryptedBase64 ->
            editor.putString(KEY_DEEPSEEK_API_KEY, encryptedBase64).apply()
        }
    }

    fun clearApiKey() {
        prefs.edit()
            .remove(KEY_DEEPSEEK_API_KEY)
            .remove(KEY_LEGACY_DEEPSEEK_API_KEY)
            .apply()
    }

    fun getCheckInterval(): Int {
        return prefs.getInt(KEY_CHECK_INTERVAL_SEC, 53) // Default to 53 seconds
    }

    fun saveCheckInterval(seconds: Int) {
        prefs.edit().putInt(KEY_CHECK_INTERVAL_SEC, seconds).apply()
    }

    private fun encryptWithKeystore(value: String): String? {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val cipherText = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val payload = cipher.iv + cipherText
            Base64.encodeToString(payload, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun decryptWithKeystore(base64Str: String): String {
        return try {
            val payload = Base64.decode(base64Str, Base64.NO_WRAP)
            if (payload.size <= 12) return ""
            val iv = payload.copyOfRange(0, 12)
            val cipherText = payload.copyOfRange(12, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keySpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    private fun migrateLegacyApiKey(): String {
        val legacyBase64 = prefs.getString(KEY_LEGACY_DEEPSEEK_API_KEY, null) ?: return ""
        val legacyApiKey = try {
            String(xorDecryptLegacy(legacyBase64), Charsets.UTF_8).trim()
        } catch (e: Exception) {
            ""
        }

        if (legacyApiKey.isNotEmpty()) {
            saveApiKey(legacyApiKey)
        } else {
            prefs.edit().remove(KEY_LEGACY_DEEPSEEK_API_KEY).apply()
        }
        return legacyApiKey
    }

    private fun xorDecryptLegacy(base64Str: String): ByteArray {
        val bytes = Base64.decode(base64Str, Base64.NO_WRAP)
        return ByteArray(bytes.size) { i ->
            (bytes[i].toInt() xor LEGACY_OBFUSCATION_KEY[i % LEGACY_OBFUSCATION_KEY.size].toInt()).toByte()
        }
    }
}
