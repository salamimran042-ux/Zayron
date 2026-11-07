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

/** Encrypted credentials and minimal account profile; never expose tokens to UI state. */
class SessionTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAtEpochSeconds: Long,
    val email: String? = null,
    val displayName: String? = null
) {
    init {
        require(accessToken.isNotBlank()) { "Access token must not be blank." }
        require(refreshToken.isNotBlank()) { "Refresh token must not be blank." }
        require(accessExpiresAtEpochSeconds > 0) { "A valid absolute expiry is required." }
        require(email == null || email.length <= 320) { "Email is too long." }
        require(displayName == null || displayName.length <= 120) { "Display name is too long." }
    }
}

/** Storage boundary for encrypted session credentials. */
interface SecureSessionStore {
    @Throws(Exception::class)
    fun save(tokens: SessionTokens)
    fun read(): SessionTokens?
    fun clear()
}

/**
 * AES-256-GCM encrypted session storage backed by a non-exportable Android Keystore key.
 * Format v2 adds encrypted profile fields and continues reading existing v1 sessions.
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
            Files.move(temporaryFile.toPath(), sessionFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            plaintext.fill(0)
            if (temporaryFile.exists()) temporaryFile.delete()
        }
    }

    @Synchronized
    override fun read(): SessionTokens? {
        if (!sessionFile.isFile) return null
        return try {
            require(sessionFile.length() in MIN_ENCRYPTED_FILE_LENGTH..MAX_ENCRYPTED_FILE_LENGTH)
            val encrypted = FileInputStream(sessionFile).use { it.readBytes() }
            DataInputStream(ByteArrayInputStream(encrypted)).use { data ->
                require(data.readInt() == FORMAT_VERSION)
                val ivLength = data.readInt()
                require(ivLength == GCM_IV_LENGTH)
                val iv = ByteArray(ivLength).also(data::readFully)
                val ciphertextLength = data.readInt()
                require(ciphertextLength in MIN_CIPHERTEXT_LENGTH..MAX_CIPHERTEXT_LENGTH)
                val ciphertext = ByteArray(ciphertextLength).also(data::readFully)
                require(data.available() == 0)
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
            // File deletion still occurs if Keystore cleanup is unavailable.
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
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
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
            writeNullableString(data, tokens.email)
            writeNullableString(data, tokens.displayName)
        }
        return output.toByteArray()
    }

    private fun decode(bytes: ByteArray): SessionTokens =
        DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            val version = data.readInt()
            require(version == LEGACY_FORMAT_VERSION || version == FORMAT_VERSION)
            val access = data.readUTF()
            val refresh = data.readUTF()
            val expiry = data.readLong()
            val email = if (version >= FORMAT_VERSION) readNullableString(data) else null
            val name = if (version >= FORMAT_VERSION) readNullableString(data) else null
            require(data.available() == 0)
            SessionTokens(access, refresh, expiry, email, name)
        }

    private fun writeNullableString(data: DataOutputStream, value: String?) {
        data.writeBoolean(value != null)
        if (value != null) data.writeUTF(value)
    }

    private fun readNullableString(data: DataInputStream): String? =
        if (data.readBoolean()) data.readUTF() else null

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ai.zayron.client.session.v1"
        const val SESSION_FILE_NAME = "zayron_session.enc"
        const val TEMP_FILE_NAME = "zayron_session.enc.tmp"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val LEGACY_FORMAT_VERSION = 1
        const val FORMAT_VERSION = 2
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
        const val MIN_CIPHERTEXT_LENGTH = 16
        const val MAX_CIPHERTEXT_LENGTH = 256 * 1024
        const val MIN_ENCRYPTED_FILE_LENGTH = 24L
        const val MAX_ENCRYPTED_FILE_LENGTH = MAX_CIPHERTEXT_LENGTH.toLong() + 24L
    }
}
