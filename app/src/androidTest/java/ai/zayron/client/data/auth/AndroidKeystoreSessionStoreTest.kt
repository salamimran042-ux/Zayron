package ai.zayron.client.data.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Device-side tests for Android Keystore encryption and persistence. */
@RunWith(AndroidJUnit4::class)
class AndroidKeystoreSessionStoreTest {
    private lateinit var store: AndroidKeystoreSessionStore
    private lateinit var sessionFile: File

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        store = AndroidKeystoreSessionStore(context)
        sessionFile = File(context.noBackupFilesDir, "zayron_session.enc")
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun saveAndRead_roundTripsTokensThroughKeystoreEncryption() {
        val expected = SessionTokens("access-token-device-test", "refresh-token-device-test", 1_900_000_000L)
        store.save(expected)
        val actual = store.read()
        assertNotNull(actual)
        assertEqualsSession(expected, actual!!)
    }

    @Test
    fun encryptedFile_doesNotContainPlaintextTokens() {
        val access = "secret-access-value-123"
        val refresh = "secret-refresh-value-456"
        store.save(SessionTokens(access, refresh, 1_900_000_000L))
        assertTrue("Encrypted session file was not created.", sessionFile.isFile)
        val encryptedBytes = sessionFile.readBytes()
        assertTrue("Encrypted session file is unexpectedly short (" + encryptedBytes.size + " bytes).", encryptedBytes.size >= 40)
        assertFalse(
            "Encrypted session file contains the plaintext access-token byte sequence.",
            encryptedBytes.containsSequence(access.toByteArray(Charsets.UTF_8))
        )
        assertFalse(
            "Encrypted session file contains the plaintext refresh-token byte sequence.",
            encryptedBytes.containsSequence(refresh.toByteArray(Charsets.UTF_8))
        )
    }

    @Test
    fun tamperedCiphertext_isRejectedAndStoredSessionIsCleared() {
        store.save(SessionTokens("access-to-tamper", "refresh-to-tamper", 1_900_000_000L))
        val bytes = sessionFile.readBytes()
        assertTrue(bytes.isNotEmpty())
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        sessionFile.writeBytes(bytes)
        assertNull(store.read())
        assertFalse(sessionFile.exists())
        assertNull(store.read())
    }

    @Test
    fun clearRemovesSessionAndAllowsFreshKeystoreSession() {
        val first = SessionTokens("first-access", "first-refresh", 1_900_000_000L)
        val second = SessionTokens("second-access", "second-refresh", 1_900_000_100L)
        store.save(first)
        store.clear()
        assertFalse(sessionFile.exists())
        assertNull(store.read())
        store.save(second)
        val restored = store.read()
        assertNotNull(restored)
        assertEqualsSession(second, restored!!)
    }

    private fun assertEqualsSession(expected: SessionTokens, actual: SessionTokens) {
        assertTrue(expected.accessToken == actual.accessToken)
        assertTrue(expected.refreshToken == actual.refreshToken)
        assertTrue(expected.accessExpiresAtEpochSeconds == actual.accessExpiresAtEpochSeconds)
        assertTrue(expected.email == actual.email)
        assertTrue(expected.displayName == actual.displayName)
    }

    private fun ByteArray.containsSequence(sequence: ByteArray): Boolean {
        if (sequence.isEmpty() || sequence.size > size) return false
        return indices.any { start ->
            start + sequence.size <= size &&
                sequence.indices.all { offset -> this[start + offset] == sequence[offset] }
        }
    }
}
