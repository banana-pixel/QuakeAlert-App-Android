package id.my.bananapixel.quakealert.ui

import id.my.bananapixel.quakealert.db.ChatMessage
import id.my.bananapixel.quakealert.db.ChatRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ChatViewModel].
 *
 * Tests the business logic of chat message management including:
 * - Exposing chat messages from the repository as a Flow
 * - Delegating save operations to the use case
 * - Delegating prune operations to the use case
 * - Edge cases: empty lists, large timestamps, multiple invocations
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private lateinit var viewModel: ChatViewModel
    private lateinit var chatRepository: ChatRepository

    private val testDispatcher = StandardTestDispatcher()

    // ---------------------------------------------------------------------------
    // Test fixtures
    // ---------------------------------------------------------------------------

    private val sampleMessage = ChatMessage(
        id = "msg-001",
        senderId = "user-alpha",
        message = "Hello, World!",
        timestamp = 1_700_000_000L
    )

    private val anotherMessage = ChatMessage(
        id = "msg-002",
        senderId = "user-beta",
        message = "Earthquake detected!",
        timestamp = 1_700_001_000L
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        chatRepository = mockk()

        // Default stubs so the ViewModel can be constructed without crash
        every { chatRepository.chatMessages } returns flowOf(emptyList())
        every { chatRepository.recentMessages } returns flowOf(emptyList())

        viewModel = ChatViewModel(chatRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---------------------------------------------------------------------------
    // chatMessages Flow
    // ---------------------------------------------------------------------------

    @Test
    fun `chatMessages emits empty list when repository has no data`() = runTest {
        // Given: repository returns empty flow (default stub)

        // When: collecting first emission
        val result = viewModel.chatMessages.first()

        // Then: should be an empty list
        assertEquals(emptyList<ChatMessage>(), result)
    }

    @Test
    fun `chatMessages emits messages from repository`() = runTest {
        // Given: repository returns a list of messages
        val messages = listOf(sampleMessage, anotherMessage)
        every { chatRepository.chatMessages } returns flowOf(messages)
        val vm = ChatViewModel(chatRepository)

        // When: collecting the first emission
        val result = vm.chatMessages.first()

        // Then: should receive all messages in order
        assertEquals(2, result.size)
        assertEquals("msg-001", result[0].id)
        assertEquals("Earthquake detected!", result[1].message)
    }

    @Test
    fun `chatMessages reflects the correct sender ID`() = runTest {
        // Given: a message with a specific sender
        every { chatRepository.chatMessages } returns flowOf(listOf(sampleMessage))
        val vm = ChatViewModel(chatRepository)

        // When: observing messages
        val result = vm.chatMessages.first()

        // Then: sender ID is preserved
        assertEquals("user-alpha", result[0].senderId)
        assertEquals(1_700_000_000L, result[0].timestamp)
    }

    // ---------------------------------------------------------------------------
    // saveChatMessages
    // ---------------------------------------------------------------------------

    @Test
    fun `saveChatMessages delegates to repository via use case`() = runTest {
        // Given: repository save is stubbed
        coEvery { chatRepository.saveChatMessages(any()) } returns Unit
        val messages = listOf(sampleMessage)

        // When: saving messages
        viewModel.saveChatMessages(messages)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: repository was called with the correct list
        coVerify { chatRepository.saveChatMessages(messages) }
    }

    @Test
    fun `saveChatMessages with empty list still delegates to repository`() = runTest {
        // Given: repository save is stubbed
        coEvery { chatRepository.saveChatMessages(any()) } returns Unit

        // When: saving an empty list
        viewModel.saveChatMessages(emptyList())
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: repository is still called (caller's intent is explicit)
        coVerify { chatRepository.saveChatMessages(emptyList()) }
    }

    @Test
    fun `saveChatMessages can be called multiple times`() = runTest {
        // Given: repository save is stubbed
        coEvery { chatRepository.saveChatMessages(any()) } returns Unit

        // When: saving messages twice
        viewModel.saveChatMessages(listOf(sampleMessage))
        viewModel.saveChatMessages(listOf(anotherMessage))
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: repository was called twice
        coVerify(exactly = 2) { chatRepository.saveChatMessages(any()) }
    }

    // ---------------------------------------------------------------------------
    // pruneOldMessages
    // ---------------------------------------------------------------------------

    @Test
    fun `pruneOldMessages delegates to repository via use case`() = runTest {
        // Given: repository prune is stubbed
        val threshold = 1_699_000_000L
        coEvery { chatRepository.pruneOldMessages(any()) } returns Unit

        // When: pruning old messages
        viewModel.pruneOldMessages(threshold)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: repository was called with the correct threshold
        coVerify { chatRepository.pruneOldMessages(threshold) }
    }

    @Test
    fun `pruneOldMessages with zero threshold delegates to repository`() = runTest {
        // Given: threshold is 0 (would delete everything older than epoch)
        coEvery { chatRepository.pruneOldMessages(any()) } returns Unit

        // When: pruning with zero
        viewModel.pruneOldMessages(0L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: repository is still called
        coVerify { chatRepository.pruneOldMessages(0L) }
    }

    @Test
    fun `pruneOldMessages with Long MAX_VALUE threshold delegates to repository`() = runTest {
        // Given: future timestamp (prunes everything)
        coEvery { chatRepository.pruneOldMessages(any()) } returns Unit

        // When: pruning with max timestamp
        viewModel.pruneOldMessages(Long.MAX_VALUE)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: repository receives the correct value
        coVerify { chatRepository.pruneOldMessages(Long.MAX_VALUE) }
    }
}
