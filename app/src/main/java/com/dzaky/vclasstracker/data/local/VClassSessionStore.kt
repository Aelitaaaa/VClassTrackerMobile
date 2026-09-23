package com.dzaky.vclasstracker.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import okhttp3.Cookie
import org.json.JSONArray
import org.json.JSONObject

class VClassSessionStore(
    context: Context
) {

    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    fun saveSession(
        displayName: String,
        cookies: List<Cookie>
    ) {
        val cookieArray =
            JSONArray()

        cookies.forEach { cookie ->
            cookieArray.put(
                JSONObject()
                    .put(
                        "name",
                        cookie.name
                    )
                    .put(
                        "value",
                        cookie.value
                    )
                    .put(
                        "domain",
                        cookie.domain
                    )
                    .put(
                        "path",
                        cookie.path
                    )
                    .put(
                        "expiresAt",
                        cookie.expiresAt
                    )
                    .put(
                        "secure",
                        cookie.secure
                    )
                    .put(
                        "httpOnly",
                        cookie.httpOnly
                    )
                    .put(
                        "hostOnly",
                        cookie.hostOnly
                    )
            )
        }

        val json =
            JSONObject()
                .put(
                    "displayName",
                    displayName
                )
                .put(
                    "cookies",
                    cookieArray
                )
                .toString()

        val encrypted =
            encrypt(
                json
            )

        preferences
            .edit()
            .putString(
                KEY_SESSION,
                encrypted
            )
            .apply()
    }

    fun loadSession(): StoredVClassSession? {
        val encrypted =
            preferences.getString(
                KEY_SESSION,
                null
            )
                ?: return null

        val decrypted =
            try {
                decrypt(
                    encrypted
                )
            } catch (_: Exception) {
                clear()
                return null
            }

        val json =
            try {
                JSONObject(
                    decrypted
                )
            } catch (_: Exception) {
                clear()
                return null
            }

        val displayName =
            json.optString(
                "displayName"
            )

        val cookieArray =
            json.optJSONArray(
                "cookies"
            )
                ?: JSONArray()

        val currentTime =
            System.currentTimeMillis()

        val cookies =
            mutableListOf<Cookie>()

        for (
            index in 0 until cookieArray.length()
        ) {
            val item =
                cookieArray.optJSONObject(
                    index
                )
                    ?: continue

            val name =
                item.optString(
                    "name"
                )

            val value =
                item.optString(
                    "value"
                )

            val domain =
                item.optString(
                    "domain"
                )

            val path =
                item.optString(
                    "path",
                    "/"
                )

            val expiresAt =
                item.optLong(
                    "expiresAt",
                    Long.MAX_VALUE
                )

            val secure =
                item.optBoolean(
                    "secure",
                    false
                )

            val httpOnly =
                item.optBoolean(
                    "httpOnly",
                    false
                )

            val hostOnly =
                item.optBoolean(
                    "hostOnly",
                    false
                )

            if (
                name.isBlank() ||
                domain.isBlank() ||
                expiresAt <= currentTime
            ) {
                continue
            }

            try {
                val builder =
                    Cookie.Builder()
                        .name(
                            name
                        )
                        .value(
                            value
                        )
                        .path(
                            path
                        )

                if (hostOnly) {
                    builder.hostOnlyDomain(
                        domain
                    )
                } else {
                    builder.domain(
                        domain
                    )
                }

                if (
                    expiresAt != Long.MAX_VALUE
                ) {
                    builder.expiresAt(
                        expiresAt
                    )
                }

                if (secure) {
                    builder.secure()
                }

                if (httpOnly) {
                    builder.httpOnly()
                }

                cookies.add(
                    builder.build()
                )
            } catch (_: Exception) {
            }
        }

        if (cookies.isEmpty()) {
            clear()
            return null
        }

        return StoredVClassSession(
            displayName = displayName,
            cookies = cookies
        )
    }

    fun clear() {
        preferences
            .edit()
            .remove(
                KEY_SESSION
            )
            .apply()
    }

    fun hasSession(): Boolean {
        return loadSession() != null
    }

    private fun encrypt(
        value: String
    ): String {
        val cipher =
            Cipher.getInstance(
                TRANSFORMATION
            )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            getOrCreateSecretKey()
        )

        val encrypted =
            cipher.doFinal(
                value.toByteArray(
                    Charsets.UTF_8
                )
            )

        val iv =
            Base64.encodeToString(
                cipher.iv,
                Base64.NO_WRAP
            )

        val data =
            Base64.encodeToString(
                encrypted,
                Base64.NO_WRAP
            )

        return "$iv:$data"
    }

    private fun decrypt(
        value: String
    ): String {
        val parts =
            value.split(
                ":",
                limit = 2
            )

        if (parts.size != 2) {
            throw IllegalArgumentException(
                "Invalid encrypted session"
            )
        }

        val iv =
            Base64.decode(
                parts[0],
                Base64.NO_WRAP
            )

        val encrypted =
            Base64.decode(
                parts[1],
                Base64.NO_WRAP
            )

        val cipher =
            Cipher.getInstance(
                TRANSFORMATION
            )

        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(
                128,
                iv
            )
        )

        val decrypted =
            cipher.doFinal(
                encrypted
            )

        return String(
            decrypted,
            Charsets.UTF_8
        )
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore =
            KeyStore.getInstance(
                "AndroidKeyStore"
            ).apply {
                load(
                    null
                )
            }

        val existingKey =
            keyStore.getKey(
                KEY_ALIAS,
                null
            )

        if (
            existingKey is SecretKey
        ) {
            return existingKey
        }

        val keyGenerator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )

        val keySpec =
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(
                    KeyProperties.BLOCK_MODE_GCM
                )
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .build()

        keyGenerator.init(
            keySpec
        )

        return keyGenerator.generateKey()
    }

    companion object {
        private const val PREFS_NAME =
            "vclass_secure_session"

        private const val KEY_SESSION =
            "session"

        private const val KEY_ALIAS =
            "vclass_session_key"

        private const val TRANSFORMATION =
            "AES/GCM/NoPadding"
    }
}

data class StoredVClassSession(
    val displayName: String,
    val cookies: List<Cookie>
)