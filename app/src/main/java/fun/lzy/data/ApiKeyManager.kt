package fun.lzy.data

import android.content.Context
import android.util.Base64

class ApiKeyManager(context: Context) {
    private val prefs = context.getSharedPreferences("deepseek_secure_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DEEPSEEK_API_KEY = "obscured_api_key"
        private const val KEY_CHECK_INTERVAL_SEC = "check_interval_sec"
        private val OBFUSCATION_KEY = byteArrayOf(57, 118, 92, 19, 73, 112, 12, 34) // XOR Obfuscation Key
    }

    fun getApiKey(): String {
        val encryptedBase64 = prefs.getString(KEY_DEEPSEEK_API_KEY, null) ?: return ""
        return try {
            val decryptedBytes = xorDecrypt(encryptedBase64)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    fun saveApiKey(apiKey: String) {
        try {
            val encryptedBase64 = xorEncrypt(apiKey.trim().toByteArray(Charsets.UTF_8))
            prefs.edit().putString(KEY_DEEPSEEK_API_KEY, encryptedBase64).apply()
        } catch (e: Exception) {
            // Log/Ignore
        }
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_DEEPSEEK_API_KEY).apply()
    }

    fun getCheckInterval(): Int {
        return prefs.getInt(KEY_CHECK_INTERVAL_SEC, 53) // Default to 53 seconds
    }

    fun saveCheckInterval(seconds: Int) {
        prefs.edit().putInt(KEY_CHECK_INTERVAL_SEC, seconds).apply()
    }

    private fun xorEncrypt(bytes: ByteArray): String {
        val result = ByteArray(bytes.size)
        for (i in bytes.indices) {
            result[i] = (bytes[i].toInt() xor OBFUSCATION_KEY[i % OBFUSCATION_KEY.size].toInt()).toByte()
        }
        return Base64.encodeToString(result, Base64.NO_WRAP)
    }

    private fun xorDecrypt(base64Str: String): ByteArray {
        val bytes = Base64.decode(base64Str, Base64.NO_WRAP)
        val result = ByteArray(bytes.size)
        for (i in bytes.indices) {
            result[i] = (bytes[i].toInt() xor OBFUSCATION_KEY[i % OBFUSCATION_KEY.size].toInt()).toByte()
        }
        return result
    }
}
