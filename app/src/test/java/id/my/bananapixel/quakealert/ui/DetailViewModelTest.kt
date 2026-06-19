package id.my.bananapixel.quakealert.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import id.my.bananapixel.quakealert.db.Notification
import id.my.bananapixel.quakealert.db.Repository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [DetailViewModel].
 *
 * Tests the business logic of the subscription detail screen including:
 * - Search query state management
 * - Filtering notifications via LiveData
 * - markAsDeleted delegation to the repository
 * - Factory creation
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    // Required to make LiveData work synchronously in unit tests (no Looper needed)
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: DetailViewModel
    private lateinit var repository: Repository

    private val testDispatcher = StandardTestDispatcher()

    // ---------------------------------------------------------------------------
    // Test fixtures
    // ---------------------------------------------------------------------------

    private val subscriptionId = 42L

    private fun makeNotification(id: String, message: String) = Notification(
        id = id,
        subscriptionId = subscriptionId,
        timestamp = System.currentTimeMillis() / 1000L,
        sequenceId = "",
        topic = "test-topic",
        message = message,
        title = "",
        priority = 3,
        tags = "",
        click = "",
        actions = null,
        attachment = null,
        notificationId = 0,
        deleted = false,
        contentType = null,
        encoding = "",
        icon = null,
        event = "message"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        viewModel = DetailViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---------------------------------------------------------------------------
    // Search query state
    // ---------------------------------------------------------------------------

    @Test
    fun `initial search query is blank`() {
        // Given: Fresh ViewModel

        // When: checking search query status
        val hasQuery = viewModel.hasSearchQuery()

        // Then: no query is active
        assertFalse(hasQuery)
    }

    @Test
    fun `setSearchQuery with non-blank string sets hasSearchQuery to true`() {
        // When: setting a search query
        viewModel.setSearchQuery("5.0")

        // Then: hasSearchQuery reflects the change
        assertTrue(viewModel.hasSearchQuery())
    }

    @Test
    fun `setSearchQuery with blank string sets hasSearchQuery to false`() {
        // Given: a query was previously set
        viewModel.setSearchQuery("5.0")

        // When: clearing it
        viewModel.setSearchQuery("")

        // Then: no query is active
        assertFalse(viewModel.hasSearchQuery())
    }

    @Test
    fun `setSearchQuery with whitespace-only string sets hasSearchQuery to false`() {
        // When: setting a whitespace query
        viewModel.setSearchQuery("   ")

        // Then: treated as blank, no active query
        assertFalse(viewModel.hasSearchQuery())
    }

    @Test
    fun `setSearchQuery can be updated multiple times`() {
        // When: updating the query several times
        viewModel.setSearchQuery("quake")
        assertTrue(viewModel.hasSearchQuery())

        viewModel.setSearchQuery("")
        assertFalse(viewModel.hasSearchQuery())

        viewModel.setSearchQuery("magnitude")
        assertTrue(viewModel.hasSearchQuery())
    }

    // ---------------------------------------------------------------------------
    // list()
    // ---------------------------------------------------------------------------

    @Test
    fun `list() returns LiveData from repository`() {
        // Given: repository returns a LiveData
        val liveData = MutableLiveData(listOf(makeNotification("n1", "Test")))
        every { repository.getNotificationsLiveData(subscriptionId) } returns liveData

        // When: requesting the list
        val result = viewModel.list(subscriptionId)

        // Then: the LiveData from the repository is returned
        assertNotNull(result)
        assertEquals(1, result.value?.size)
        assertEquals("Test", result.value?.first()?.message)
    }

    @Test
    fun `list() returns empty LiveData when repository has no notifications`() {
        // Given: repository returns empty list
        val liveData = MutableLiveData(emptyList<Notification>())
        every { repository.getNotificationsLiveData(subscriptionId) } returns liveData

        // When
        val result = viewModel.list(subscriptionId)

        // Then
        assertNotNull(result)
        assertTrue(result.value.isNullOrEmpty())
    }

    // ---------------------------------------------------------------------------
    // listFiltered()
    // ---------------------------------------------------------------------------

    @Test
    fun `listFiltered() with no query delegates to getNotificationsLiveData`() {
        // Given: no query set, repository returns unfiltered list
        val allNotifications = listOf(
            makeNotification("n1", "Big earthquake"),
            makeNotification("n2", "Small tremor")
        )
        val liveData = MutableLiveData(allNotifications)
        every { repository.getNotificationsLiveData(subscriptionId) } returns liveData

        // When: observing filtered list with no active search
        val result = viewModel.listFiltered(subscriptionId)

        // Then: the unfiltered LiveData is used
        assertNotNull(result)
        assertEquals(2, result.value?.size)
    }

    @Test
    fun `listFiltered() with active query delegates to getNotificationsFilteredLiveData`() {
        // Given: a search query is set
        val filteredNotifications = listOf(makeNotification("n1", "Big earthquake"))
        val filteredLiveData = MutableLiveData(filteredNotifications)
        every { repository.getNotificationsFilteredLiveData(subscriptionId, "earthquake") } returns filteredLiveData

        // When: setting a query and observing filtered list
        viewModel.setSearchQuery("earthquake")
        val result = viewModel.listFiltered(subscriptionId)

        // Then: the filtered LiveData is used
        assertNotNull(result)
        assertEquals(1, result.value?.size)
        assertEquals("Big earthquake", result.value?.first()?.message)
    }

    // ---------------------------------------------------------------------------
    // markAsDeleted
    // ---------------------------------------------------------------------------

    @Test
    fun `markAsDeleted delegates to repository`() = runTest {
        // Given: repository is relaxed (accepts any call)
        val notificationId = "notif-xyz"

        // When: marking as deleted
        viewModel.markAsDeleted(notificationId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: repository received the call with the correct ID
        coVerify { repository.markAsDeleted(notificationId) }
    }

    @Test
    fun `markAsDeleted can be called multiple times for different IDs`() = runTest {
        // When: deleting multiple notifications
        viewModel.markAsDeleted("notif-001")
        viewModel.markAsDeleted("notif-002")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: each call reaches the repository
        coVerify { repository.markAsDeleted("notif-001") }
        coVerify { repository.markAsDeleted("notif-002") }
    }

    // ---------------------------------------------------------------------------
    // DetailViewModelFactory
    // ---------------------------------------------------------------------------

    @Test
    fun `DetailViewModelFactory creates DetailViewModel`() {
        // Given: a factory with a mock repository
        val factory = DetailViewModelFactory(repository)

        // When: creating the ViewModel
        val created = factory.create(DetailViewModel::class.java)

        // Then: instance is the correct type
        assertTrue(created is DetailViewModel)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `DetailViewModelFactory throws for unknown ViewModel class`() {
        // Given: a factory with a mock repository
        val factory = DetailViewModelFactory(repository)

        // When: requesting an unsupported ViewModel type
        // Then: throws IllegalArgumentException
        factory.create(ChatViewModel::class.java)
    }
}
