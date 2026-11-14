package com.example.util

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.data.model.ResidentNotice
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Gerenciador de segurança, privacidade e política de retenção para Avisos de Moradores.
 * - Protege documentos de identificação em repouso usando AES-256-GCM (Android Keystore quando disponível).
 * - Nunca registra documentos completos em logs do sistema.
 * - Gerencia a política configurável de retenção sem exclusões silenciosas.
 */
object NoticeSecurityManager {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "bps_resident_notice_doc_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val ENC_PREFIX = "ENC:v1:"

    private const val PREFS_NAME = "bps_notice_security_prefs"
    private const val KEY_RETENTION_DAYS = "notice_retention_days"
    private const val KEY_FALLBACK_KEY = "fallback_aes_key_b64"
    const val DEFAULT_RETENTION_DAYS = 90
    val RETENTION_OPTIONS_DAYS = listOf(30, 60, 90, 180, 365)

    @Volatile
    private var cachedSecretKey: SecretKey? = null

    private fun getOrCreateSecretKey(context: Context?): SecretKey {
        cachedSecretKey?.let { return it }

        // Tenta utilizar o Android Keystore nativo no dispositivo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
                keyStore.load(null)
                val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
                if (existingKey != null) {
                    cachedSecretKey = existingKey
                    return existingKey
                }
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                val newKey = keyGenerator.generateKey()
                cachedSecretKey = newKey
                return newKey
            } catch (_: Throwable) {
                // Ambiente JVM / Robolectric sem KeyStore de hardware: usa chave privada do aplicativo
            }
        }

        if (context != null) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedB64 = prefs.getString(KEY_FALLBACK_KEY, null)
            if (!savedB64.isNullOrBlank()) {
                val bytes = Base64.decode(savedB64, Base64.NO_WRAP)
                val key = SecretKeySpec(bytes, "AES")
                cachedSecretKey = key
                return key
            }
            val raw = ByteArray(32)
            SecureRandom().nextBytes(raw)
            val encoded = Base64.encodeToString(raw, Base64.NO_WRAP)
            prefs.edit().putString(KEY_FALLBACK_KEY, encoded).apply()
            val key = SecretKeySpec(raw, "AES")
            cachedSecretKey = key
            return key
        }

        // Fallback em memória apenas para uso unitário sem Context
        val ephemeral = ByteArray(32) { index -> (index * 7 + 19).toByte() }
        val key = SecretKeySpec(ephemeral, "AES")
        cachedSecretKey = key
        return key
    }

    fun encryptDocument(plainDocument: String, context: Context? = null): String {
        val clean = plainDocument.trim()
        if (clean.isEmpty()) return ""
        if (clean.startsWith(ENC_PREFIX)) return clean

        return try {
            val key = getOrCreateSecretKey(context)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(clean.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteBuffer.allocate(iv.size + cipherBytes.size)
                .put(iv)
                .put(cipherBytes)
                .array()
            ENC_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Throwable) {
            clean
        }
    }

    fun decryptDocument(storedValue: String, context: Context? = null): String {
        val clean = storedValue.trim()
        if (clean.isEmpty()) return ""
        if (!clean.startsWith(ENC_PREFIX)) return clean

        return try {
            val payload = Base64.decode(clean.removePrefix(ENC_PREFIX), Base64.NO_WRAP)
            if (payload.size <= GCM_IV_LENGTH_BYTES) return ""
            val iv = payload.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val cipherBytes = payload.copyOfRange(GCM_IV_LENGTH_BYTES, payload.size)
            val key = getOrCreateSecretKey(context)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
        } catch (_: Throwable) {
            "[Documento protegido]"
        }
    }

    fun maskDocument(storedOrPlain: String, context: Context? = null): String {
        val plain = decryptDocument(storedOrPlain, context).trim()
        if (plain.isEmpty() || plain == "[Documento protegido]") return plain
        return if (plain.length > 5) {
            val start = plain.take(2)
            val end = plain.takeLast(2)
            val masked = "*".repeat((plain.length - 4).coerceAtLeast(3))
            "$start$masked$end"
        } else {
            "***"
        }
    }

    fun isEncrypted(storedValue: String): Boolean = storedValue.startsWith(ENC_PREFIX)

    fun getRetentionDays(context: Context): Int {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)
            .coerceIn(7, 3650)
    }

    fun setRetentionDays(context: Context, days: Int) {
        val safeDays = days.coerceIn(7, 3650)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_RETENTION_DAYS, safeDays).apply()
    }

    /**
     * Identifica avisos concluídos ou cancelados cujo período já ultrapassou a política de retenção.
     * Nunca remove automaticamente sem confirmação explícita do usuário.
     */
    fun findNoticesEligibleForPurge(
        notices: List<ResidentNotice>,
        retentionDays: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): List<ResidentNotice> {
        val cutoffMillis = nowMillis - (retentionDays.toLong() * 24L * 60L * 60L * 1000L)
        return notices.filter { notice ->
            notice.status in setOf("CONCLUIDO", "CANCELADO") &&
                notice.updatedAtTimestamp < cutoffMillis
        }
    }
}
