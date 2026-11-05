package ai.zayron.client.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted client-side credentials needed to restore and refresh a backend session.
 * These values must never be exposed to Compose state, logs, analytics or crash reports.
 */
class SessionTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAtEpochSeconds: Long
) {
    init {
        require(accessToken.isNotBlank()) { "Access token must not be blank." }
        require(refreshToken.isNotBlank()) { "Refresh token must not be blank." }
        require(accessExpiresAtEpochSeconds > 0) { "A valid absolute expiry is required." }
    }
}

/** Storage boundary for encrypted session credentials. */
interface SecureSessionStore {
    @Throws(Exception::class)
    fun save(tokens: SessionTokens)

    /** Returns null when no valid stored session exists. Corrupt or unreadable data is cleared. */
    fun read(): SessionTokens?

    /** Removes both encrypted session data and its Keystore key. */
    fun clear()
}

/**
 * AES-256-GCM encrypted session storage backed by an Android Keystore key.
 *
 * Ciphertext is written to an app-private, no-backup file. The AES key is non-exportable
 * and remains in Android Keystore. There is deliberately no plaintext fallback.
 */
class AndroidKeystoreSessionStore(context: Context) : SecureSessionStore {
    private val appContext = context.applicationContext
    private val sessionFile = File(appContext.noBackupFilesDir, SESSION_FILE_NAME)
    private val temporaryFile = File(appContext.noBackupFilesDir, TEMP_FILE_NAME)

    @Synchronized
    override fun save(tokens: SessionTokens) {
        val plaintext = encode(tokens)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val ciphertext = cipher.doFinal(plaintext)
            val output = ByteArrayOutputStream()
            DataOutputStream(output).use { data ->
                data.writeInt(FORMAT_VERSION)
                data.writeInt(cipher.iv.size)
                data.write(cipher.iv)
                data.writeInt(ciphertext.size)
                data.write(ciphertext)
            }

            FileOutputStream(temporaryFile).use { stream ->
                stream.write(output.toByteArray())
                stream.fd.sync()
            }
            Files.move(
                temporaryFile.toPath(),
                sessionFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } finally {
            plaintext.fill(0)
            if (temporaryFile.exists()) temporaryFile.delete()
        }
    }

    @Synchronized
    override fun read(): SessionTokens? {
        if (!sessionFile.isFile) return null
        return try {
            require(sessionFile.length() in MIN_ENCRYPTED_FILE_LENGTH..MAX_ENCRYPTED_FILE_LENGTH) {
                "Invalid encrypted session file size."
            }
            val encrypted = FileInputStream(sessionFile).use { it.readBytes() }
            DataInputStream(ByteArrayInputStream(encrypted)).use { data ->
                require(data.readInt() == FORMAT_VERSION) { "Unsupported session format." }
                val ivLength = data.readInt()
                require(ivLength == GCM_IV_LENGTH) { "Invalid session nonce." }
                val iv = ByteArray(ivLength).also(data::readFully)
                val ciphertextLength = data.readInt()
                require(ciphertextLength in MIN_CIPHERTEXT_LENGTH..MAX_CIPHERTEXT_LENGTH) {
                    "Invalid encrypted session size."
                }
                val ciphertext = ByteArray(ciphertextLength).also(data::readFully)
                require(data.available() == 0) { "Unexpected trailing session data." }

                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
                val plaintext = cipher.doFinal(ciphertext)
                try {
                    decode(plaintext)
                } finally {
                    plaintext.fill(0)
                    ciphertext.fill(0)
                    iv.fill(0)
                }
            }
        } catch (_: Exception) {
            clear()
            null
        }
    }

    @Synchronized
    override fun clear() {
        if (sessionFile.exists()) sessionFile.delete()
        if (temporaryFile.exists()) temporaryFile.delete()
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
            // Local file deletion still occurs if Keystore cleanup is unavailable.
        }
    }

    private fun getExistingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        return (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)
            ?: throw GeneralSecurityException("Session encryption key is unavailable.")
    }

    private fun getOrCreateKey(): SecretKey {
        getExistingKeyOrNull()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun getExistingKeyOrNull(): SecretKey? = try {
        getExistingKey()
    } catch (_: GeneralSecurityException) {
        null
    }

    private fun encode(tokens: SessionTokens): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(FORMAT_VERSION)
            data.writeUTF(tokens.accessToken)
            data.writeUTF(tokens.refreshToken)
            data.writeLong(tokens.accessExpiresAtEpochSeconds)
        }
        return output.toByteArray()
    }

    private fun decode(bytes: ByteArray): SessionTokens =
        DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            require(data.readInt() == FORMAT_VERSION) { "Unsupported token payload." }
            val access = data.readUTF()
            val refresh = data.readUTF()
            val expiry = data.readLong()
            require(data.available() == 0) { "Unexpected token payload data." }
            SessionTokens(access, refresh, expiry)
        }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ai.zayron.client.session.v1"
        const val SESSION_FILE_NAME = "zayron_session.enc"
        const val TEMP_FILE_NAME = "zayron_session.enc.tmp"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = 1
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
        const val MIN_CIPHERTEXT_LENGTH = 16
        const val MAX_CIPHERTEXT_LENGTH = 256 * 1024
        const val MIN_ENCRYPTED_FILE_LENGTH = 24L
        const val MAX_ENCRYPTED_FILE_LENGTH = MAX_CIPHERTEXT_LENGTH.toLong() + 24L
    }
}
