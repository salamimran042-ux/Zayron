package ai.zayron.client.ui.chat

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.zayron.client.data.chat.ChatRemoteDataSource
import ai.zayron.client.data.chat.ChatRepository
import ai.zayron.client.data.chat.ChatStreamEvent
import ai.zayron.client.data.chat.ChatStreamRequest
import ai.zayron.client.data.network.NetworkResult
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises cancellation and clear-conversation behavior on a real Android
 * runtime, while controlling the remote stream so races are reproducible.
 */
@RunWith(AndroidJUnit4::class)
class ChatViewModelCancellationTest {

    @Test
    fun stopGeneration_cancelsRemoteWorkAndUnlocksComposer() {
        val remote = ControlledChatRemoteDataSource()
        val repository = InMemoryChatRepository()
        val viewModel = newViewModel(repository, remote)

        onMainThread { viewModel.sendMessage("Please generate a reply") }
        assertNotNull("Remote request did not start", remote.awaitRequest())

        onMainThread { viewModel.cancelCurrentRequest() }

        waitUntil("Composer did not unlock after cancellation") {
            readOnMainThread { !viewModel.isSending }
        }
        assertFalse(readOnMainThread { viewModel.isSending })
        assertEquals(
            DeliveryState.LOCAL_ONLY,
            readOnMainThread { viewModel.messages.single().deliveryState }
        )
        assertTrue(
            readOnMainThread { viewModel.serviceMessage.orEmpty().contains("Generation stopped") }
        )
    }

    @Test
    fun clearConversation_doesNotLetCancelledRequestUnlockNewRequest() {
        val remote = ControlledChatRemoteDataSource()
        val repository = InMemoryChatRepository()
        val viewModel = newViewModel(repository, remote)

        onMainThread { viewModel.sendMessage("Old conversation") }
        assertNotNull("First remote request did not start", remote.awaitRequest())

        onMainThread { viewModel.clearConversation() }
        assertTrue(readOnMainThread { viewModel.messages.isEmpty() })
        assertFalse(readOnMainThread { viewModel.isSending })

        onMainThread { viewModel.sendMessage("New conversation") }
        val newRequest = remote.awaitRequest()
        assertNotNull("New remote request did not start", newRequest)

        // Completing the new request must leave its state authoritative even if
        // the cancelled request is still unwinding its cleanup path.
        assertTrue("New request was incorrectly unlocked", readOnMainThread { viewModel.isSending })
        assertEquals(1, readOnMainThread { viewModel.messages.size })
        assertEquals("New conversation", readOnMainThread { viewModel.messages.single().text })

        newRequest!!.complete(NetworkResult.Success(Unit, 200))
        waitUntil("New request did not finish") {
            readOnMainThread { !viewModel.isSending }
        }

        assertEquals(
            DeliveryState.SENT,
            readOnMainThread { viewModel.messages.single().deliveryState }
        )
        assertEquals("New conversation", readOnMainThread { viewModel.messages.single().text })
    }

    private fun newViewModel(
        repository: ChatRepository,
        remote: ChatRemoteDataSource
    ): ChatViewModel {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        return ChatViewModel(application, repository, remote)
    }

    private fun onMainThread(action: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    }

    private fun <T> readOnMainThread(action: () -> T): T {
        var result: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = action()
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun waitUntil(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
        assertTrue(message, condition())
    }

    private class InMemoryChatRepository : ChatRepository {
        @Volatile private var stored: List<ChatMessage> = emptyList()

        override fun loadMessages(): List<ChatMessage> = stored

        override fun saveMessages(messages: List<ChatMessage>) {
            stored = messages.toList()
        }

        override fun clearMessages() {
            stored = emptyList()
        }
    }

    private class ControlledChatRemoteDataSource : ChatRemoteDataSource {
        private val pending = LinkedBlockingQueue<CompletableDeferred<NetworkResult<Unit>>>()

        override suspend fun streamReply(
            request: ChatStreamRequest,
            onEvent: (ChatStreamEvent) -> Unit
        ): NetworkResult<Unit> {
            val response = CompletableDeferred<NetworkResult<Unit>>()
            pending.put(response)
            return response.await()
        }

        fun awaitRequest(): CompletableDeferred<NetworkResult<Unit>>? =
            pending.poll(5, TimeUnit.SECONDS)
    }
}